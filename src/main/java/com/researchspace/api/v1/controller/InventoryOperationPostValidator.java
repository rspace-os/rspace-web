package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.ApiExtraFieldsHelper;
import com.researchspace.service.inventory.InventoryOperationConfig;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/**
 * Validates an {@link ApiInventoryOperationPost} against the operation definition its {@code
 * operationType} names (DevDocs/adr/0007). The rules are interpreted generically from the shared
 * {@code operations_config.json} (no per-operation Java): origin cardinality, new-sample presence
 * (a noOutput operation like Destroy creates nothing), per-origin amount semantics (positive for a
 * decrementing operation, exactly zero for one that only links, e.g. Passage), configured
 * storage-temperature bounds (unit-aware), and a provenance link from the new sample back to every
 * origin. The new sample is also run through the same {@link SampleApiPostValidator} the public
 * samples endpoint uses. Checks needing an origin's live quantity are enforced by the manager
 * inside the operation's transaction, not here (DevDocs/adr/0007).
 */
@Component
public class InventoryOperationPostValidator implements Validator {

  /**
   * Ceiling on origins per request: each origin costs a read, a lock and an update cycle, so the
   * batch is capped like the samples endpoint caps newSampleSubSamplesCount (both 100).
   */
  static final int MAX_ORIGINS = 100;

  /**
   * The documentation link is a wizard-level feature (an SOP chosen in the documentation step), not
   * a per-operation declaration, so its key is fixed and every operation that creates a sample
   * accepts one (DevDocs/adr/0007).
   */
  static final String DOCUMENTATION_LINK_KEY = "operations.documentationLink";

  private static final String DOCUMENTATION_RELATION_TYPE = "IsDocumentedBy";

  private static final Pattern POSITIVE_INTEGER = Pattern.compile("0*[1-9]\\d*");

  /**
   * What each computed function promises about the content of the field it feeds. The backend
   * checks the shape rather than recomputing the value (DevDocs/adr/0007): the parent field an
   * {@code increment} counts from is findable only by its localized name, and a {@code today}
   * recomputed server-side would fight the client's timezone. A function with no rule here is left
   * unchecked; the registry test pins the shipped set.
   */
  private static final Map<String, Predicate<String>> COMPUTED_CONTENT_SHAPES =
      Map.of(
          "increment",
          content -> POSITIVE_INTEGER.matcher(content).matches(),
          "today",
          InventoryOperationPostValidator::isIsoDate);

  private static boolean isIsoDate(String content) {
    try {
      LocalDate.parse(content, DateTimeFormatter.ISO_LOCAL_DATE);
      return true;
    } catch (DateTimeParseException e) {
      return false;
    }
  }

  private final InventoryOperationConfigRegistry operationConfigs;
  private final SampleApiPostValidator sampleApiPostValidator;
  private final ApiExtraFieldsHelper extraFieldsHelper;

  public InventoryOperationPostValidator(
      InventoryOperationConfigRegistry operationConfigs,
      SampleApiPostValidator sampleApiPostValidator,
      ApiExtraFieldsHelper extraFieldsHelper) {
    this.operationConfigs = operationConfigs;
    this.sampleApiPostValidator = sampleApiPostValidator;
    this.extraFieldsHelper = extraFieldsHelper;
  }

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiInventoryOperationPost.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiInventoryOperationPost request = (ApiInventoryOperationPost) target;

    // The operation key names the definition every other rule comes from, so an unknown key is
    // rejected alone: there is nothing meaningful to validate the rest of the request against.
    Optional<InventoryOperationConfig> configForKey =
        operationConfigs.get(request.getOperationType());
    if (configForKey.isEmpty()) {
      errors.rejectValue(
          "operationType",
          "errors.inventory.operation.unknownType",
          new Object[] {request.getOperationType()},
          "Unknown operation type.");
      return;
    }
    InventoryOperationConfig config = configForKey.get();

    if (CollectionUtils.isEmpty(request.getOrigins())) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originsRequired",
          "At least one origin subsample must be provided for the operation.");
      return;
    }
    // A null list entry (JSON "[null]") cannot be iterated by the checks below; reject it as a
    // clean 400 rather than letting it surface as a 500.
    if (request.getOrigins().stream().anyMatch(Objects::isNull)) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originIdRequired",
          "Each origin must identify a subsample by id.");
      return;
    }
    if (config.requiresMultiple() && request.getOrigins().size() < 2) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originCountMinimum",
          "This operation requires at least two origin subsamples.");
    } else if (!config.requiresMultiple() && request.getOrigins().size() != 1) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originCountExact",
          "This operation requires exactly one origin subsample.");
    } else if (request.getOrigins().size() > MAX_ORIGINS) {
      // Each origin costs a read, a lock and an update cycle; cap the batch like the samples
      // endpoint caps newSampleSubSamplesCount at 100.
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originCountMaximum",
          new Object[] {MAX_ORIGINS},
          "This operation accepts at most 100 origin subsamples.");
    }

    validateOrigins(request, config, errors);
    validateNewSample(request, config, errors);
  }

  private void validateOrigins(
      ApiInventoryOperationPost request, InventoryOperationConfig config, Errors errors) {
    // A subsample may appear at most once: each origin's amount taken is validated against that
    // origin's original quantity, but the manager applies the decrements in order, so the same id
    // listed twice would be checked twice against the full quantity yet decremented twice (two 6 mL
    // entries could drain a 10 mL origin past what the over-removal check permits). See
    // DevDocs/adr/0007.
    Set<Long> seenIds = new HashSet<>();
    int index = 0;
    for (ApiInventoryOperationOriginUpdate origin : request.getOrigins()) {
      errors.pushNestedPath(String.format("origins[%d]", index++));
      if (origin.getId() == null) {
        errors.rejectValue(
            "id",
            "errors.inventory.operation.originIdRequired",
            "Each origin must identify a subsample by id.");
      } else if (!seenIds.add(origin.getId())) {
        errors.rejectValue(
            "id",
            "errors.inventory.operation.duplicateOrigin",
            "An origin subsample may appear at most once in an operation.");
      }
      if (!isValidAmountTaken(origin.getAmountTaken())) {
        errors.rejectValue(
            "amountTaken",
            "errors.inventory.operation.amountTakenInvalid",
            "Each origin must specify a non-negative amount, with a unit, to take from it.");
      } else if (!RSUnitDef.exists(origin.getAmountTaken().getUnitId())) {
        // The manager subtracts unit-aware, so an unknown unit would fail there as a 422 rather
        // than a field-scoped 400 (code review, finding 4).
        errors.rejectValue(
            "amountTaken",
            "errors.inventory.quantity.unitInvalid",
            new Object[] {origin.getAmountTaken().getUnitId()},
            "The amount taken must use a known unit.");
      } else if (!RSUnitDef.getUnitById(origin.getAmountTaken().getUnitId()).isAmount()) {
        errors.rejectValue(
            "amountTaken",
            "errors.inventory.quantity.unitNotAmount",
            new Object[] {origin.getAmountTaken().getUnitId()},
            "The amount taken must use an amount unit (volume, mass or count).");
      } else if (!QuantityInfo.canStoreWithoutRounding(origin.getAmountTaken().getNumericValue())) {
        // Quantities persist at 3dp (QuantityInfo rounds HALF_UP), so a finer amount would pass the
        // live-state checks as given yet decrement the origin by the rounded surrogate (0.0004 ml
        // would take nothing at all). Rejected rather than rounded, like over-removal.
        errors.rejectValue(
            "amountTaken",
            "errors.inventory.operation.amountTakenTooPrecise",
            "The amount taken supports at most 3 decimal places.");
      } else if (!config.effect().emptiesOrigin()) {
        // What the amount taken must be follows the operation's effect (DevDocs/adr/0007): an
        // operation that decrements its origins (amountTakenFrom configured) must take a positive
        // amount from each; one that only links to them (e.g. Passage) must take exactly zero. An
        // origin-emptying operation (Destroy) is checked live in the controller instead, where the
        // amount must equal the origin's current quantity.
        int amountSignum = origin.getAmountTaken().getNumericValue().signum();
        if (config.effect().amountTakenFrom() != null && amountSignum <= 0) {
          errors.rejectValue(
              "amountTaken",
              "errors.inventory.operation.amountTakenPositive",
              "This operation takes from each origin, so the amount taken must be greater than"
                  + " zero.");
        } else if (config.effect().amountTakenFrom() == null && amountSignum != 0) {
          errors.rejectValue(
              "amountTaken",
              "errors.inventory.operation.amountTakenZero",
              "This operation does not take from its origins, so the amount taken must be zero.");
        }
      }
      validateOriginExtraFields(origin, config, errors);
      errors.popNestedPath();
    }
  }

  /**
   * An origin's extra fields must be exactly the {@code originFields} the operation declares
   * (Destroy's disposed date), matched by key; an operation declaring none accepts none, which also
   * closes the route by which an origin could be made to link to itself (DevDocs/adr/0007). Fields
   * may only ADD: a delete request or an id-bearing edit of an existing field is a mutation no
   * definition describes, so it is rejected even though the caller holds edit permission. Each
   * allowed field is then validated by the same shared field validator the subsample PUT endpoint
   * uses (name required, per-type content, link payloads).
   */
  private void validateOriginExtraFields(
      ApiInventoryOperationOriginUpdate origin, InventoryOperationConfig config, Errors errors) {
    List<ApiExtraField> fields =
        origin.getExtraFields() == null ? List.of() : origin.getExtraFields();
    List<InventoryOperationConfig.OriginField> declared = config.effect().originFields();

    int fieldIndex = 0;
    for (ApiExtraField field : fields) {
      errors.pushNestedPath(String.format("extraFields[%d]", fieldIndex++));
      try {
        if (field == null
            || !field.isNewFieldRequest()
            || field.isDeleteFieldRequest()
            || field.getId() != null) {
          errors.rejectValue(
              "newFieldRequest",
              "errors.inventory.operation.originFieldNewOnly",
              "Origin extra fields may only add new fields.");
          continue;
        }
        if (declared.stream()
            .noneMatch(spec -> spec.nameKey().equals(field.getOperationFieldKey()))) {
          errors.rejectValue(
              "operationFieldKey",
              "errors.inventory.operation.fieldKeyUnknown",
              new Object[] {field.getOperationFieldKey()},
              "This field is not one the operation declares.");
        }
        ValidationUtils.invokeValidator(extraFieldsHelper, field, errors);
      } finally {
        errors.popNestedPath();
      }
    }

    for (InventoryOperationConfig.OriginField spec : declared) {
      List<ApiExtraField> keyed = fieldsWithKey(fields, spec.nameKey());
      if (keyed.size() != 1) {
        rejectDeclaredField(errors, "extraFields", spec.nameKey());
        continue;
      }
      ApiExtraField field = keyed.get(0);
      String path = String.format("extraFields[%d]", fields.indexOf(field));
      if (field.getTypeAsFieldType() != declaredFieldType(spec.type())) {
        rejectDeclaredField(errors, path, spec.nameKey());
        continue;
      }
      validateDeclaredContent(
          field.getContent(), spec.nameKey(), spec.contentFrom(), config, path, errors);
    }
  }

  /** The field type a definition declares, defaulting to text like {@link ApiExtraField} does. */
  private static FieldType declaredFieldType(String declaredType) {
    return declaredType == null ? FieldType.TEXT : FieldType.valueOf(declaredType.toUpperCase());
  }

  private void validateNewSample(
      ApiInventoryOperationPost request, InventoryOperationConfig config, Errors errors) {
    if (config.noOutput()) {
      if (request.getNewSample() != null) {
        errors.rejectValue(
            "newSample",
            "errors.inventory.operation.newSampleForbidden",
            "This operation does not create a sample, so newSample must be omitted.");
      }
      return;
    }
    if (request.getNewSample() == null) {
      errors.rejectValue(
          "newSample",
          "errors.inventory.operation.newSampleRequired",
          "This operation creates a sample, so newSample is required.");
      return;
    }
    ApiSampleWithFullSubSamples newSample = request.getNewSample();

    // A null subsample entry (JSON "[null]") cannot be iterated by the delegated validator or the
    // per-subsample checks below; reject it as a clean 400 rather than letting it 500.
    if (newSample.getSubSamples() != null
        && newSample.getSubSamples().stream().anyMatch(Objects::isNull)) {
      errors.rejectValue(
          "newSample.subSamples",
          "errors.inventory.operation.subSampleQuantityInvalid",
          "Each new subsample must hold a quantity greater than zero, with a unit.");
      return;
    }
    // A null tags/extraFields entry (JSON "[null]") would otherwise reach the delegated samples
    // validator's per-element checks (name length, key lookup) and NPE; reject it here as a clean
    // 400, same as origins/subSamples above. Tags are never declared by any operation, so this is
    // just the undeclaredProperty rule anticipating the element it cannot inspect; extraFields are
    // matched by key, so a null entry is reported the same way an unkeyed one already is.
    if (newSample.getTags() != null && newSample.getTags().stream().anyMatch(Objects::isNull)) {
      errors.rejectValue(
          "newSample.tags",
          "errors.inventory.operation.undeclaredProperty",
          new Object[] {"tags"},
          "This operation does not accept this property on the sample it creates.");
      return;
    }
    if (newSample.getExtraFields() != null
        && newSample.getExtraFields().stream().anyMatch(Objects::isNull)) {
      errors.rejectValue(
          "newSample.extraFields",
          "errors.inventory.operation.fieldKeyUnknown",
          new Object[] {null},
          "This field is not one the operation declares.");
      return;
    }

    // The operations path bypasses SamplesApiController, so delegate the new sample to the exact
    // validator the public samples endpoint uses (name, tags, storage-temperature sanity, extra
    // fields including link payloads, subsample quantity units); its errors surface under
    // newSample.* (gap closed by DevDocs/adr/0007).
    errors.pushNestedPath("newSample");
    try {
      ValidationUtils.invokeValidator(sampleApiPostValidator, newSample, errors);
    } finally {
      errors.popNestedPath();
    }

    // Stricter than the samples endpoint (which allows quantity-less subsamples): an operation's
    // created subsamples represent material taken from the origins, so each must hold a positive
    // amount with a real unit, and there must be at least one of them.
    // The count input's lower bound is the definition's own floor on how many subsamples the
    // operation may create; the count itself never travels on the wire, so the number of subsamples
    // is what it is checked against (DevDocs/adr/0007).
    int minimumSubSamples = minimumSubSampleCount(config);
    if (newSample.getSubSamples() == null || newSample.getSubSamples().size() < minimumSubSamples) {
      errors.rejectValue(
          "newSample.subSamples",
          "errors.inventory.operation.subSamplesRequired",
          new Object[] {minimumSubSamples},
          "The new sample does not include enough subsamples.");
      return;
    }
    int index = 0;
    boolean allQuantitiesValid = true;
    for (ApiSubSample subSample : newSample.getSubSamples()) {
      ApiQuantityInfo quantity = subSample.getQuantity();
      boolean positiveWithUnit =
          quantity != null
              && quantity.getNumericValue() != null
              && quantity.getNumericValue().signum() > 0
              && quantity.getUnitId() != null
              && quantity.getUnitId() > 0;
      if (!positiveWithUnit) {
        errors.rejectValue(
            String.format("newSample.subSamples[%d].quantity", index),
            "errors.inventory.operation.subSampleQuantityInvalid",
            "Each new subsample must hold a quantity greater than zero, with a unit.");
        allQuantitiesValid = false;
      } else if (!QuantityInfo.canStoreWithoutRounding(quantity.getNumericValue())) {
        // Same rule as amountTaken: a quantity finer than the stored 3dp would round to a
        // different amount, possibly zero, so the created subsample would not hold what was
        // validated (code review, finding 7).
        errors.rejectValue(
            String.format("newSample.subSamples[%d].quantity", index),
            "errors.inventory.operation.subSampleQuantityTooPrecise",
            "Each new subsample quantity supports at most 3 decimal places.");
        allQuantitiesValid = false;
      }
      index++;
    }

    // An operation with a single each-amount input (eachAmountFrom) copies it to every child, and
    // the API documents N equal subsamples; unequal children would silently break that contract
    // (valid-payload review, finding 2). Equality is unit-aware within a measurement category
    // (0.5 ml equals 500 ul); a different category can never be equal. Only checked once every
    // quantity passed the shape rules above, so it never double-reports an invalid quantity.
    if (config.effect().eachAmountFrom() != null && allQuantitiesValid) {
      QuantityUtils quantityUtils = new QuantityUtils();
      ApiQuantityInfo first = newSample.getSubSamples().get(0).getQuantity();
      boolean allEqual =
          newSample.getSubSamples().stream()
              .map(ApiSubSample::getQuantity)
              .allMatch(
                  quantity ->
                      quantityUtils.isComparableQuantities(first, quantity)
                          && quantityUtils.getComparatorFor(first).compare(first, quantity) == 0);
      if (!allEqual) {
        errors.rejectValue(
            "newSample.subSamples",
            "errors.inventory.operation.subSampleQuantitiesUnequal",
            "This operation creates equal subsamples, so every new subsample must have the same"
                + " quantity.");
      }
    }

    rejectUndeclaredNewSampleContent(newSample, config, errors);
    validateConfiguredTemperatures(newSample, config, errors);
    validateNewSampleExtraFields(request, newSample, config, errors);
  }

  /**
   * The new sample is a whitelist, not a general sample POST: an operation request may only carry
   * the properties its definition declares (DevDocs/adr/0007, superseding the earlier partial
   * rules). Everything else - sharing, placement, tags, barcodes, images, template field values,
   * per-subsample notes and fields - is content no operation definition describes, so it is
   * rejected naming the property rather than silently stripped. Name, templateId, quantity,
   * subSamples and extraFields are validated by their own rules; storage temperatures are allowed
   * only for an operation that declares a temperature input.
   */
  private void rejectUndeclaredNewSampleContent(
      ApiSampleWithFullSubSamples newSample, InventoryOperationConfig config, Errors errors) {
    rejectIfPresent(errors, "newSample.description", newSample.getDescription());
    rejectIfPresent(errors, "newSample.iconId", newSample.getIconId());
    rejectIfPresent(errors, "newSample.tags", newSample.getTags());
    rejectIfPresent(errors, "newSample.barcodes", newSample.getBarcodes());
    rejectIfPresent(errors, "newSample.identifiers", newSample.getIdentifiers());
    rejectIfPresent(errors, "newSample.sharingMode", newSample.getSharingMode());
    rejectIfPresent(errors, "newSample.sharedWith", newSample.getSharedWith());
    rejectIfPresent(errors, "newSample.newBase64Image", newSample.getNewBase64Image());
    rejectIfPresent(errors, "newSample.fields", newSample.getFields());
    rejectIfPresent(errors, "newSample.sampleSource", newSample.getSampleSource());
    rejectIfPresent(errors, "newSample.expiryDate", newSample.getExpiryDate());
    rejectIfPresent(
        errors, "newSample.newSampleSubSamplesCount", newSample.getNewSampleSubSamplesCount());
    rejectIfPresent(
        errors,
        "newSample.newSampleSubSampleTargetLocations",
        newSample.getNewSampleSubSampleTargetLocations());
    if (config.effect().storageTempFrom() == null) {
      rejectIfPresent(errors, "newSample.storageTempMin", newSample.getStorageTempMin());
      rejectIfPresent(errors, "newSample.storageTempMax", newSample.getStorageTempMax());
    }

    // The operation's own fields go on the created sample; its subsamples carry a quantity and
    // nothing else, so anything the wizard never sends on one is undeclared content too.
    int index = 0;
    for (ApiSubSample subSample : newSample.getSubSamples()) {
      String path = String.format("newSample.subSamples[%d].", index++);
      // A child's name is generated from the sample's (code review, finding 9); its icon is the
      // sample's. Neither is a declared input of any operation.
      rejectIfPresent(errors, path + "name", subSample.getName());
      rejectIfPresent(errors, path + "iconId", subSample.getIconId());
      rejectIfPresent(errors, path + "notes", subSample.getNotes());
      rejectIfPresent(errors, path + "extraFields", subSample.getExtraFields());
      rejectIfPresent(errors, path + "description", subSample.getDescription());
      rejectIfPresent(errors, path + "tags", subSample.getTags());
      rejectIfPresent(errors, path + "barcodes", subSample.getBarcodes());
      rejectIfPresent(errors, path + "sharingMode", subSample.getSharingMode());
      rejectIfPresent(errors, path + "sharedWith", subSample.getSharedWith());
      rejectIfPresent(errors, path + "newBase64Image", subSample.getNewBase64Image());
      rejectIfPresent(errors, path + "parentContainers", subSample.getParentContainers());
      rejectIfPresent(errors, path + "parentLocation", subSample.getParentLocation());
    }
  }

  /**
   * Rejects a property the operation definition does not declare. Absent means null, an empty
   * collection or a blank string, so a client that spells out the DTO's own defaults ({@code
   * "tags": []}) is not punished for sending nothing.
   */
  private void rejectIfPresent(Errors errors, String field, Object value) {
    boolean present = value != null;
    if (value instanceof Collection<?> collection) {
      present = !collection.isEmpty();
    } else if (value instanceof String string) {
      present = !string.isBlank();
    }
    if (present) {
      String property = field.substring(field.lastIndexOf('.') + 1);
      errors.rejectValue(
          field,
          "errors.inventory.operation.undeclaredProperty",
          new Object[] {property},
          "This operation does not accept this property on the sample it creates.");
    }
  }

  /**
   * An operation with a temperature input (e.g. Cryopreserve, Revive) stores it as the new sample's
   * storage temperature, and the config bounds it in Celsius (DevDocs/adr/0007). Both storage
   * temperatures are required and each is compared unit-aware against the configured bounds, so a
   * value sent in Kelvin or Fahrenheit is judged on the temperature it denotes, not its number.
   */
  private void validateConfiguredTemperatures(
      ApiSampleWithFullSubSamples newSample, InventoryOperationConfig config, Errors errors) {
    if (config.effect().storageTempFrom() == null) {
      return;
    }
    // Exactly one temperature input maps to the two storage-temperature fields; taking the first
    // also guards against double-rejecting should a config ever declare more than one.
    config.inputs().stream()
        .filter(input -> "temperature".equals(input.type()))
        .findFirst()
        .ifPresent(
            input -> {
              checkConfiguredTemperature(
                  newSample.getStorageTempMin(), "newSample.storageTempMin", input, errors);
              checkConfiguredTemperature(
                  newSample.getStorageTempMax(), "newSample.storageTempMax", input, errors);
              checkSingleTemperature(newSample, errors);
            });
  }

  /**
   * The wizard collects one temperature and writes it to both storage-temperature fields, so a
   * request spreading them into a range describes a sample the operation cannot produce (review
   * repro F5d). Compared unit-aware, so 253.15 K equals -20 degC.
   */
  private void checkSingleTemperature(ApiSampleWithFullSubSamples newSample, Errors errors) {
    ApiQuantityInfo minimum = newSample.getStorageTempMin();
    ApiQuantityInfo maximum = newSample.getStorageTempMax();
    if (minimum == null
        || maximum == null
        || minimum.getNumericValue() == null
        || maximum.getNumericValue() == null) {
      return; // already rejected as storageTempRequired
    }
    QuantityUtils quantityUtils = new QuantityUtils();
    if (!quantityUtils.isComparableQuantities(minimum, maximum)
        || quantityUtils.getComparatorFor(minimum).compare(minimum, maximum) != 0) {
      errors.rejectValue(
          "newSample.storageTempMax",
          "errors.inventory.operation.storageTempSingleValue",
          "This operation stores one temperature, so the minimum and maximum must be equal.");
    }
  }

  /**
   * The lower bound on how many subsamples the operation creates, taken from the input its {@code
   * countFrom} names. Defaults to one: an operation that creates a sample must put something in it.
   */
  private static int minimumSubSampleCount(InventoryOperationConfig config) {
    return config.inputs().stream()
        .filter(input -> input.key() != null && input.key().equals(config.effect().countFrom()))
        .map(InventoryOperationConfig.Input::min)
        .filter(Objects::nonNull)
        .findFirst()
        .map(BigDecimal::intValue)
        .filter(minimum -> minimum > 1)
        .orElse(1);
  }

  private void checkConfiguredTemperature(
      ApiQuantityInfo temperature,
      String field,
      InventoryOperationConfig.Input input,
      Errors errors) {
    if (temperature == null || temperature.getNumericValue() == null) {
      errors.rejectValue(
          field,
          "errors.inventory.operation.storageTempRequired",
          "This operation requires a storage temperature on the new sample.");
      return;
    }
    // A non-temperature unit is already rejected by the delegated samples-endpoint rules; the
    // configured bounds can only be checked against a real temperature.
    if (temperature.getUnitId() == null
        || !RSUnitDef.exists(temperature.getUnitId())
        || !RSUnitDef.getUnitById(temperature.getUnitId()).isTemperature()) {
      return;
    }
    QuantityInfo value = temperature.toQuantityInfo();
    QuantityUtils quantityUtils = new QuantityUtils();
    if (input.maxCelsius() != null) {
      QuantityInfo maximum = QuantityInfo.of(input.maxCelsius(), RSUnitDef.CELSIUS);
      if (quantityUtils.getComparatorFor(maximum).compare(value, maximum) > 0) {
        errors.rejectValue(
            field,
            "errors.inventory.operation.storageTempAboveMax",
            new Object[] {input.maxCelsius()},
            "The storage temperature is above this operation's maximum.");
      }
    }
    if (input.minCelsius() != null) {
      QuantityInfo minimum = QuantityInfo.of(input.minCelsius(), RSUnitDef.CELSIUS);
      if (quantityUtils.getComparatorFor(minimum).compare(value, minimum) < 0) {
        errors.rejectValue(
            field,
            "errors.inventory.operation.storageTempBelowMin",
            new Object[] {input.minCelsius()},
            "The storage temperature is below this operation's minimum.");
      }
    }
  }

  /**
   * The new sample's extra fields must be exactly the ones the operation definition declares, no
   * more and no fewer (DevDocs/adr/0007, superseding the earlier "extras are allowed" rule). Fields
   * are matched by {@code operationFieldKey}, not by name: resolved names interpolate user input
   * and are localized, so a name is not a stable identity. Each declared link spec fans out to one
   * link per origin (Pool's one HasPart per pooled subsample); each declared text field appears
   * once; and the wizard-level documentation link is allowed on any operation that creates a
   * sample.
   */
  private void validateNewSampleExtraFields(
      ApiInventoryOperationPost request,
      ApiSampleWithFullSubSamples newSample,
      InventoryOperationConfig config,
      Errors errors) {
    List<ApiExtraField> fields =
        newSample.getExtraFields() == null ? List.of() : newSample.getExtraFields();

    Set<String> declaredKeys = new HashSet<>();
    config.effect().links().forEach(link -> declaredKeys.add(link.fieldNameKey()));
    config.effect().textFields().forEach(textField -> declaredKeys.add(textField.nameKey()));
    declaredKeys.add(DOCUMENTATION_LINK_KEY);
    for (int index = 0; index < fields.size(); index++) {
      ApiExtraField field = fields.get(index);
      if (field != null && !declaredKeys.contains(field.getOperationFieldKey())) {
        errors.rejectValue(
            String.format("newSample.extraFields[%d].operationFieldKey", index),
            "errors.inventory.operation.fieldKeyUnknown",
            new Object[] {field.getOperationFieldKey()},
            "This field is not one the operation declares.");
      }
    }

    validateDeclaredLinks(request, config, fields, errors);
    for (InventoryOperationConfig.TextField textFieldSpec : config.effect().textFields()) {
      validateDeclaredTextField(textFieldSpec, config, fields, errors);
    }
    validateDocumentationLink(fields, errors);
  }

  /**
   * Each declared link spec must produce exactly one link per origin: the declared relation type,
   * targeting that origin, on a field whose effective type is LINK. Only a LINK-typed field counts
   * because persistence ({@code ApiExtraFieldsHelper.addRecordExtraFieldForIncomingApiField})
   * creates a link under exactly that predicate, so a link payload on a text-typed or type-omitted
   * field would otherwise validate here yet silently vanish on save (security review, finding 7).
   */
  private void validateDeclaredLinks(
      ApiInventoryOperationPost request,
      InventoryOperationConfig config,
      List<ApiExtraField> fields,
      Errors errors) {
    for (InventoryOperationConfig.Link linkSpec : config.effect().links()) {
      List<ApiExtraField> keyed = fieldsWithKey(fields, linkSpec.fieldNameKey());
      for (ApiInventoryOperationOriginUpdate origin : request.getOrigins()) {
        if (origin.getId() == null) {
          continue; // already rejected as originIdRequired
        }
        String target = GlobalIdPrefix.SS.name() + origin.getId();
        long linksToOrigin =
            keyed.stream()
                .filter(field -> field.getTypeAsFieldType() == FieldType.LINK)
                .map(ApiExtraField::getLink)
                .filter(Objects::nonNull)
                .filter(
                    link ->
                        linkSpec.relationType().equals(link.getRelationType())
                            && target.equalsIgnoreCase(link.getTargetGlobalId()))
                .count();
        if (linksToOrigin != 1) {
          errors.rejectValue(
              "newSample.extraFields",
              "errors.inventory.operation.linkToOriginRequired",
              new Object[] {linkSpec.relationType(), target},
              "The new sample must link back to every origin subsample exactly once.");
        }
      }
      // One link per origin and no more: a surplus field carrying the same key (a second link, or
      // one to a record that is not an origin) is content the definition does not describe. Only a
      // surplus is reported here; a shortfall is already named per origin above.
      if (keyed.size() > request.getOrigins().size()) {
        rejectDeclaredField(errors, "newSample.extraFields", linkSpec.fieldNameKey());
      }
    }
  }

  /**
   * A declared text field (Passage's passage number, Cryopreserve's cryomedium) must appear exactly
   * once, as a text field. Its content is checked against the computed function that feeds it, or
   * required when the feeding input is.
   */
  private void validateDeclaredTextField(
      InventoryOperationConfig.TextField textFieldSpec,
      InventoryOperationConfig config,
      List<ApiExtraField> fields,
      Errors errors) {
    List<ApiExtraField> keyed = fieldsWithKey(fields, textFieldSpec.nameKey());
    if (keyed.size() != 1) {
      rejectDeclaredField(errors, "newSample.extraFields", textFieldSpec.nameKey());
      return;
    }
    ApiExtraField field = keyed.get(0);
    String path = String.format("newSample.extraFields[%d]", fields.indexOf(field));
    if (field.getTypeAsFieldType() != FieldType.TEXT) {
      rejectDeclaredField(errors, path, textFieldSpec.nameKey());
      return;
    }
    validateDeclaredContent(
        field.getContent(),
        textFieldSpec.nameKey(),
        textFieldSpec.contentFrom(),
        config,
        path,
        errors);
  }

  /**
   * The documentation link is a wizard-level feature rather than a per-operation declaration, so
   * every output-producing operation accepts at most one, and it must actually be an IsDocumentedBy
   * link. Its target must be an ELN record of a kind the wizard's picker offers (a document, a
   * notebook or a Gallery file); a link "documented by" an Inventory record is rejected (code
   * review, finding 6). Readability of the target is checked by the delegated samples-endpoint
   * rules.
   */
  private void validateDocumentationLink(List<ApiExtraField> fields, Errors errors) {
    List<ApiExtraField> keyed = fieldsWithKey(fields, DOCUMENTATION_LINK_KEY);
    if (keyed.size() > 1) {
      rejectDeclaredField(errors, "newSample.extraFields", DOCUMENTATION_LINK_KEY);
      return;
    }
    for (ApiExtraField field : keyed) {
      boolean documentationLink =
          field.getTypeAsFieldType() == FieldType.LINK
              && field.getLink() != null
              && DOCUMENTATION_RELATION_TYPE.equals(field.getLink().getRelationType());
      if (!documentationLink) {
        errors.rejectValue(
            String.format("newSample.extraFields[%d].link", fields.indexOf(field)),
            "errors.inventory.operation.documentationLinkInvalid",
            new Object[] {DOCUMENTATION_RELATION_TYPE},
            "A documentation link must be a link field with the documentation relation type.");
      } else if (!targetsElnRecord(field.getLink().getTargetGlobalId())) {
        errors.rejectValue(
            String.format("newSample.extraFields[%d].link", fields.indexOf(field)),
            "errors.inventory.operation.documentationLinkTargetInvalid",
            "A documentation link must target an ELN document, notebook or Gallery file.");
      }
    }
  }

  /** The ELN record kinds the documentation picker offers (ElnFolderBrowser.PICKABLE_TYPES). */
  private static final Set<GlobalIdPrefix> DOCUMENTATION_TARGET_PREFIXES =
      Set.of(GlobalIdPrefix.SD, GlobalIdPrefix.NB, GlobalIdPrefix.GL);

  private static boolean targetsElnRecord(String targetGlobalId) {
    try {
      return DOCUMENTATION_TARGET_PREFIXES.contains(
          new GlobalIdentifier(targetGlobalId).getPrefix());
    } catch (IllegalArgumentException malformed) {
      // a malformed or missing id is already reported by the delegated link validation
      return true;
    }
  }

  private static List<ApiExtraField> fieldsWithKey(List<ApiExtraField> fields, String key) {
    return fields.stream()
        .filter(field -> field != null && key.equals(field.getOperationFieldKey()))
        .toList();
  }

  /**
   * The content of a declared field. A field fed by a computed value is checked against the shape
   * that function promises; a field fed by a plain input carries free text, required exactly when
   * that input is (Cryopreserve's optional cryomedium may be blank).
   */
  private void validateDeclaredContent(
      String content,
      String key,
      String contentFrom,
      InventoryOperationConfig config,
      String path,
      Errors errors) {
    Optional<InventoryOperationConfig.Computed> computed =
        config.effect().computed().stream()
            .filter(entry -> entry.into() != null && entry.into().equals(contentFrom))
            .findFirst();
    if (computed.isPresent()) {
      Predicate<String> shape = COMPUTED_CONTENT_SHAPES.get(computed.get().fn());
      if (shape != null && (content == null || !shape.test(content.trim()))) {
        errors.rejectValue(
            path + ".content",
            "errors.inventory.operation.computedContentInvalid",
            new Object[] {key, computed.get().fn()},
            "This field's content does not match what the operation computes for it.");
      }
      return;
    }
    boolean fedByRequiredInput =
        config.inputs().stream()
            .anyMatch(
                input ->
                    input.key() != null && input.key().equals(contentFrom) && input.required());
    if (fedByRequiredInput && StringUtils.isBlank(content)) {
      rejectDeclaredField(errors, path + ".content", key);
    }
  }

  private void rejectDeclaredField(Errors errors, String field, String key) {
    errors.rejectValue(
        field,
        "errors.inventory.operation.declaredFieldMissing",
        new Object[] {key},
        "The request must contain exactly the fields this operation declares.");
  }

  /**
   * A valid amount-taken is a non-negative numeric value carrying a real unit. The unit is required
   * because the manager converts it to a {@link com.researchspace.model.units.QuantityInfo}
   * (unit-aware subtraction); a null or non-positive unit would fail there with a 500 rather than a
   * clean 400. The frontend uses a non-positive unit id (UNSET_UNIT = 0) as an "unset" marker, so
   * the unit id must be present and greater than zero. A zero numeric value is still allowed (a
   * no-op decrement, e.g. Passage); a non-positive unit id is not.
   */
  private static boolean isValidAmountTaken(ApiQuantityInfo quantity) {
    return quantity != null
        && quantity.getNumericValue() != null
        && quantity.getNumericValue().compareTo(BigDecimal.ZERO) >= 0
        && quantity.getUnitId() != null
        && quantity.getUnitId() > 0;
  }
}
