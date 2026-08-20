package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.ApiExtraFieldsHelper;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/**
 * Validates an {@link ApiInventoryOperationPost} against the operation definition its {@code
 * operationType} names (DevDocs/adr/0015). The rules are interpreted generically from the shared
 * {@code operations_config.json} (no per-operation Java): origin cardinality, new-sample presence
 * (a noOutput operation like Destroy creates nothing), per-origin amount semantics (positive for a
 * decrementing operation, exactly zero for one that only links, e.g. Passage), configured
 * storage-temperature bounds (unit-aware), and a provenance link from the new sample back to every
 * origin. The new sample is also run through the same {@link SampleApiPostValidator} the public
 * samples endpoint uses. Checks needing an origin's live quantity are static helpers here, called
 * by the controller (DevDocs/adr/0007, DevDocs/adr/0010, DevDocs/adr/0013).
 */
@Component
public class InventoryOperationPostValidator implements Validator {

  /**
   * Ceiling on origins per request: each origin costs a read, a lock and an update cycle, so the
   * batch is capped like the samples endpoint caps newSampleSubSamplesCount (both 100).
   */
  static final int MAX_ORIGINS = 100;

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
      } else if (!config.effect().emptiesOrigin()) {
        // What the amount taken must be follows the operation's effect (DevDocs/adr/0015): an
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
      validateOriginExtraFields(origin, errors);
      errors.popNestedPath();
    }
  }

  /**
   * Origin extra fields may only ADD new fields (Destroy's disposed date): a delete request or an
   * id-bearing edit of an existing field is a mutation no operation definition describes, so it is
   * rejected even though the caller holds edit permission (DevDocs/adr/0015). Each allowed field's
   * content is then validated by the same shared field validator the subsample PUT endpoint uses
   * (name required, per-type content, link payloads).
   */
  private void validateOriginExtraFields(ApiInventoryOperationOriginUpdate origin, Errors errors) {
    if (CollectionUtils.isEmpty(origin.getExtraFields())) {
      return;
    }
    int fieldIndex = 0;
    for (ApiExtraField field : origin.getExtraFields()) {
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
        } else {
          ValidationUtils.invokeValidator(extraFieldsHelper, field, errors);
        }
      } finally {
        errors.popNestedPath();
      }
    }
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

    // The operations path bypasses SamplesApiController, so delegate the new sample to the exact
    // validator the public samples endpoint uses (name, tags, storage-temperature sanity, extra
    // fields including link payloads, subsample quantity units); its errors surface under
    // newSample.* (gap closed by DevDocs/adr/0015).
    errors.pushNestedPath("newSample");
    try {
      ValidationUtils.invokeValidator(sampleApiPostValidator, newSample, errors);
    } finally {
      errors.popNestedPath();
    }

    // Stricter than the samples endpoint (which allows quantity-less subsamples): an operation's
    // created subsamples represent material taken from the origins, so each must hold a positive
    // amount with a real unit, and there must be at least one of them.
    if (CollectionUtils.isEmpty(newSample.getSubSamples())) {
      errors.rejectValue(
          "newSample.subSamples",
          "errors.inventory.operation.subSamplesRequired",
          "The new sample must include at least one subsample.");
      return;
    }
    int index = 0;
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
      }
      index++;
    }

    validateConfiguredTemperatures(newSample, config, errors);
    validateProvenanceLinks(request, newSample, config, errors);
  }

  /**
   * An operation with a temperature input (e.g. Cryopreserve, Revive) stores it as the new sample's
   * storage temperature, and the config bounds it in Celsius (DevDocs/adr/0015). Both storage
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
            });
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
   * The new sample must link back to every origin with the operation's configured relation type
   * (e.g. Aliquot's IsPartOf, Pool's one HasPart per pooled subsample): the links are the
   * provenance record the operation exists to create (DevDocs/adr/0012, DevDocs/adr/0015). Extra
   * fields beyond the required links (the optional IsDocumentedBy link, text fields) are the
   * wizard's own output and are allowed; the link payloads themselves are validated by the
   * delegated samples-endpoint rules.
   */
  private void validateProvenanceLinks(
      ApiInventoryOperationPost request,
      ApiSampleWithFullSubSamples newSample,
      InventoryOperationConfig config,
      Errors errors) {
    for (InventoryOperationConfig.Link linkSpec : config.effect().links()) {
      for (ApiInventoryOperationOriginUpdate origin : request.getOrigins()) {
        if (origin.getId() == null) {
          continue; // already rejected as originIdRequired
        }
        String target = GlobalIdPrefix.SS.name() + origin.getId();
        boolean linked =
            newSample.getExtraFields() != null
                && newSample.getExtraFields().stream()
                    .map(ApiExtraField::getLink)
                    .filter(Objects::nonNull)
                    .anyMatch(
                        link ->
                            linkSpec.relationType().equals(link.getRelationType())
                                && target.equalsIgnoreCase(link.getTargetGlobalId()));
        if (!linked) {
          errors.rejectValue(
              "newSample.extraFields",
              "errors.inventory.operation.linkToOriginRequired",
              new Object[] {linkSpec.relationType(), target},
              "The new sample must link back to every origin subsample.");
        }
      }
    }
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

  /**
   * Whether the amount taken from an origin exceeds that origin's current quantity
   * (DevDocs/adr/0010). The comparison is unit-aware within a measurement category (e.g. 0.006 kg
   * against a 5 g origin), so a cross-unit entry in the same category is compared correctly. This
   * needs the origin's live quantity, which the stateless {@link Validator} contract cannot load,
   * so the controller loads each origin and calls this. A null amount, or a pair in different
   * categories (which the UI never produces), is not treated as over-removal. A null/absent origin
   * quantity means the origin holds nothing (a subsample whose quantity was never set), so any
   * positive amount taken from it is over-removal.
   */
  public static boolean amountTakenExceedsOrigin(
      ApiQuantityInfo amountTaken, ApiQuantityInfo originQuantity) {
    if (amountTaken == null || amountTaken.getNumericValue() == null) {
      return false;
    }
    if (originQuantity == null || originQuantity.getNumericValue() == null) {
      // Origin holds nothing: any positive amount taken is over-removal.
      return amountTaken.getNumericValue().compareTo(BigDecimal.ZERO) > 0;
    }
    QuantityUtils quantityUtils = new QuantityUtils();
    if (!quantityUtils.isComparableQuantities(amountTaken, originQuantity)) {
      return false;
    }
    return quantityUtils.getComparatorFor(originQuantity).compare(amountTaken, originQuantity) > 0;
  }

  /**
   * Whether an origin currently holds nothing: a null quantity (never set), a quantity without a
   * numeric value, or a non-positive amount. No operation may act on such an origin
   * (DevDocs/adr/0015): there is nothing to take, pool, preserve or destroy. Like {@link
   * #amountTakenExceedsOrigin} this needs the origin's live quantity, so the controller loads each
   * origin and calls this.
   */
  public static boolean originHoldsNothing(ApiQuantityInfo originQuantity) {
    return originQuantity == null
        || originQuantity.getNumericValue() == null
        || originQuantity.getNumericValue().signum() <= 0;
  }

  /**
   * Whether the amount taken equals the origin's current quantity, unit-aware within a measurement
   * category (0.005 kg empties a 5 g origin). An origin-emptying operation (emptiesOrigin, e.g.
   * Destroy) must take exactly what the origin holds, no less (over-removal is rejected
   * separately). Missing values or incomparable categories never count as emptying. Live-state
   * check: the controller loads each origin and calls this (DevDocs/adr/0015).
   */
  public static boolean amountTakenEmptiesOrigin(
      ApiQuantityInfo amountTaken, ApiQuantityInfo originQuantity) {
    if (amountTaken == null
        || amountTaken.getNumericValue() == null
        || originQuantity == null
        || originQuantity.getNumericValue() == null) {
      return false;
    }
    QuantityUtils quantityUtils = new QuantityUtils();
    if (!quantityUtils.isComparableQuantities(amountTaken, originQuantity)) {
      return false;
    }
    return quantityUtils.getComparatorFor(originQuantity).compare(amountTaken, originQuantity) == 0;
  }
}
