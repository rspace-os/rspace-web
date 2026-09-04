package com.researchspace.api.v1.controller;

import static com.researchspace.core.util.TransformerUtils.toList;

import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.model.units.RSUnitDef;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.validation.Errors;

/*
 * Helper methods for validating properties of samples and subsamples
 */
abstract class SampleApiValidator extends InventoryRecordValidator {

  QuantityUtils impl = new QuantityUtils();

  void validateStorageTemperatures(Errors errors, ApiSampleInfo apiSamplePost) {
    QuantityInfo max =
        validatedTemperature(apiSamplePost.getStorageTempMax(), "storageTempMax", errors);
    QuantityInfo min =
        validatedTemperature(apiSamplePost.getStorageTempMin(), "storageTempMin", errors);
    if (max != null && min != null) {

      if (!impl.isComparableQuantities(min, max)) {
        errors.rejectValue("storageTempMin", "errors.inventory.temperature.unitsNotComparable");
        return;
      }
      List<QuantityInfo> toSort = toList(min, max);
      impl.sortAsc(toSort);

      // unit comparison of temperature
      if (!toSort.get(0).equals(min)) {
        errors.rejectValue("storageTempMin", "errors.inventory.temperature.minGreaterThanMax");
      }
    }
  }

  /**
   * The temperature as a {@link QuantityInfo}, or null when it is absent or malformed (in which
   * case the reason is recorded against {@code field}).
   *
   * <p>The unit is resolved here, so an absent or unknown unit id has to be rejected rather than
   * left to throw out of {@link RSUnitDef#getUnitById} as an unchecked exception: a temperature
   * object of {@code {}} was surfacing as a 500 instead of a field-scoped 400. The magnitude is
   * checked for the same reason the amounts are: the column is DECIMAL(19,3), so a value outside it
   * would be stored as a different temperature, or overflow (Copilot review, PR #1090).
   */
  private QuantityInfo validatedTemperature(
      com.researchspace.api.v1.model.ApiQuantityInfo temperature, String field, Errors errors) {
    if (temperature == null) {
      return null;
    }
    if (temperature.getUnitId() == null
        || !RSUnitDef.exists(temperature.getUnitId())
        || !RSUnitDef.getUnitById(temperature.getUnitId()).isTemperature()) {
      errors.rejectValue(field, "errors.inventory.temperature.invalidUnit");
      return null;
    }
    // A missing number stays unresolved: whether a temperature is required at all is each caller's
    // own rule, but the min/max comparison below dereferences the number, so returning the value
    // here turned a malformed request into a 500 (Copilot review, PR #1090).
    if (temperature.getNumericValue() == null) {
      return null;
    }
    if (!QuantityInfo.canStoreWithoutRounding(temperature.getNumericValue())) {
      errors.rejectValue(field, "errors.inventory.temperature.notStorable");
      return null;
    }
    return temperature.toQuantityInfo();
  }

  // use supplier to reuse for ApiSampleFull and ApiSample
  void validateSubsampleQuantities(
      Supplier<List<? extends ApiSubSampleInfo>> apiSamplePost, Errors errors) {
    for (int i = 0; i < apiSamplePost.get().size(); i++) {
      ApiSubSampleInfo sub = apiSamplePost.get().get(i);
      if (sub.getQuantity() != null) {
        errors.pushNestedPath("subSamples[" + i + "]");
        validateInventoryRecordQuantity(sub, errors);
        errors.popNestedPath();
      }
    }
  }

  private final Set<String> reservedSampleFieldNames = (new Sample()).getReservedFieldNames();

  @Override
  protected Set<String> getReservedFieldNames() {
    return reservedSampleFieldNames;
  }
}
