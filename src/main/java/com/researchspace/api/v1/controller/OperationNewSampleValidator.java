package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.OperationValidationSupport.MAX_EXTRA_FIELDS;
import static com.researchspace.api.v1.controller.OperationValidationSupport.MAX_SUBSAMPLES;
import static com.researchspace.api.v1.controller.OperationValidationSupport.fieldsWithKey;
import static com.researchspace.api.v1.controller.OperationValidationSupport.quantityUtils;
import static com.researchspace.api.v1.controller.OperationValidationSupport.rejectDeclaredField;
import static com.researchspace.api.v1.controller.OperationValidationSupport.rejectIfPresent;
import static com.researchspace.api.v1.controller.OperationValidationSupport.validateDeclaredContent;

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
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.InventoryOperationConfig;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;

/**
 * The new-sample half of {@link InventoryOperationPostValidator}: whether the operation creates a
 * sample at all, the subsamples it must hold, the properties no definition declares, the configured
 * storage temperatures, and the extra fields and provenance links the definition names. Split out
 * of the validator so a new rule about the created sample lands here rather than appending to one
 * class holding every rule for every payload region (PR #963 review).
 */
class OperationNewSampleValidator {

  private final SampleApiPostValidator sampleApiPostValidator;

  OperationNewSampleValidator(SampleApiPostValidator sampleApiPostValidator) {
    this.sampleApiPostValidator = sampleApiPostValidator;
  }

  /**
   * The documentation link is a wizard-level feature (an SOP chosen in the documentation step), not
   * a per-operation declaration, so its key is fixed and every operation that creates a sample
   * accepts one (DevDocs/adr/0007).
   */
  static final String DOCUMENTATION_LINK_KEY = "operations.documentationLink";

  private static final String DOCUMENTATION_RELATION_TYPE = "IsDocumentedBy";

  /** The ELN record kinds the documentation picker offers (ElnFolderBrowser.PICKABLE_TYPES). */
  static final Set<GlobalIdPrefix> DOCUMENTATION_TARGET_PREFIXES =
      Set.of(GlobalIdPrefix.SD, GlobalIdPrefix.NB, GlobalIdPrefix.GL);

  void validateNewSample(
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
          "errors.inventory.operation.fieldKeyMissing",
          null,
          "This field is not one the operation declares.");
      return;
    }

    if (newSample.getSubSamples() != null && newSample.getSubSamples().size() > MAX_SUBSAMPLES) {
      errors.rejectValue(
          "newSample.subSamples",
          "errors.inventory.operation.subSampleCountMaximum",
          new Object[] {MAX_SUBSAMPLES},
          "This operation accepts at most 100 new subsamples.");
      return;
    }
    if (newSample.getExtraFields() != null
        && newSample.getExtraFields().size() > MAX_EXTRA_FIELDS) {
      errors.rejectValue(
          "newSample.extraFields",
          "errors.inventory.operation.extraFieldCountMaximum",
          new Object[] {MAX_EXTRA_FIELDS},
          "This operation accepts at most 100 extra fields on the sample it creates.");
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
      } else if (!RSUnitDef.exists(quantity.getUnitId())
          || !RSUnitDef.getUnitById(quantity.getUnitId()).isAmount()) {
        // Already reported by the delegated sample validator above (unitInvalid/unitNotAmount), so
        // no second error; marked invalid so the unit-aware equality and total checks below never
        // hand QuantityUtils a unit it throws on, which would turn the reported 400 into a 500
        // (Copilot review, PR #1090).
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

    // Creation derives the sample's own total from its children and stores it in the same
    // DECIMAL(19,3) column, so children that are each storable can still sum past it and 500 inside
    // the manager (Copilot review, PR #1090). Only checked once every quantity passed the shape
    // rules above, so it never double-reports an invalid quantity.
    // Quantities in different categories cannot be summed at all; that is already reported as its
    // own error above, so this check simply does not apply to them.
    boolean allComparable =
        allQuantitiesValid
            && newSample.getSubSamples().stream()
                .map(ApiSubSample::getQuantity)
                .allMatch(
                    quantity ->
                        quantityUtils.isComparableQuantities(
                            newSample.getSubSamples().get(0).getQuantity(), quantity));
    if (allComparable && newSample.getSubSamples().size() > 1) {
      QuantityInfo total =
          quantityUtils.sum(
              newSample.getSubSamples().stream()
                  .map(ApiSubSample::getQuantity)
                  .map(ApiQuantityInfo::toQuantityInfo)
                  .collect(Collectors.toList()));
      if (!QuantityInfo.canStoreWithoutRounding(total.getNumericValue())) {
        errors.rejectValue(
            "newSample.subSamples",
            "errors.inventory.operation.subSampleTotalNotStorable",
            "The new subsamples add up to more than a sample quantity can hold.");
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
   * per-subsample notes and fields, attachments and identifiers - is content no operation
   * definition describes, so it is rejected naming the property rather than silently stripped.
   * Name, templateId, quantity, subSamples and extraFields are validated by their own rules;
   * storage temperatures are allowed only for an operation that declares a temperature input.
   */
  private void rejectUndeclaredNewSampleContent(
      ApiSampleWithFullSubSamples newSample, InventoryOperationConfig config, Errors errors) {
    rejectIfPresent(errors, "newSample.description", newSample.getDescription());
    rejectIfPresent(errors, "newSample.iconId", newSample.getIconId());
    rejectIfPresent(errors, "newSample.tags", newSample.getTags());
    rejectIfPresent(errors, "newSample.barcodes", newSample.getBarcodes());
    rejectIfPresent(errors, "newSample.identifiers", newSample.getIdentifiers());
    // Inherited from ApiInventoryRecordInfo and writable, but sample creation never applies it, so
    // accepting it would perform a different operation than the one requested (Copilot review, PR
    // #1090).
    rejectIfPresent(errors, "newSample.attachments", newSample.getAttachments());
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
      // Both inherited and bindable, but createSubSampleFromIncomingApiSample persists neither, so
      // silently accepting them would break the strict contract (Copilot review, PR #1090).
      rejectIfPresent(errors, path + "attachments", subSample.getAttachments());
      rejectIfPresent(errors, path + "identifiers", subSample.getIdentifiers());
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
    // A null, unknown or non-temperature unit is already rejected by the delegated samples-endpoint
    // rules; comparing through it would unbox a null unit id or throw inside QuantityUtils, turning
    // that reported 400 into a 500 (Copilot review, PR #1090).
    if (!isKnownTemperature(minimum) || !isKnownTemperature(maximum)) {
      return;
    }
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

  /** Whether the quantity carries a unit that exists and denotes a temperature. */
  private static boolean isKnownTemperature(ApiQuantityInfo temperature) {
    return temperature.getUnitId() != null
        && RSUnitDef.exists(temperature.getUnitId())
        && RSUnitDef.getUnitById(temperature.getUnitId()).isTemperature();
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
    if (!isKnownTemperature(temperature)) {
      return;
    }
    QuantityInfo value = temperature.toQuantityInfo();
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
      if (field == null) {
        continue;
      }
      if (!declaredKeys.contains(field.getOperationFieldKey())) {
        errors.rejectValue(
            String.format("newSample.extraFields[%d].operationFieldKey", index),
            field.getOperationFieldKey() == null
                ? "errors.inventory.operation.fieldKeyMissing"
                : "errors.inventory.operation.fieldKeyUnknown",
            new Object[] {field.getOperationFieldKey()},
            "This field is not one the operation declares.");
      } else {
        // Verified against this operation's definition, so it may be persisted; see
        // ApiExtraField#operationFieldKeyVerified.
        field.setOperationFieldKeyVerified(true);
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

  private static boolean targetsElnRecord(String targetGlobalId) {
    try {
      return DOCUMENTATION_TARGET_PREFIXES.contains(
          new GlobalIdentifier(targetGlobalId).getPrefix());
    } catch (IllegalArgumentException malformed) {
      // a malformed or missing id is already reported by the delegated link validation
      return true;
    }
  }
}
