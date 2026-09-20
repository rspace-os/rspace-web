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

      if (!toSort.get(0).equals(min)) {
        errors.rejectValue("storageTempMin", "errors.inventory.temperature.minGreaterThanMax");
      }
    }
  }

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
    // A missing number stays unresolved: whether a temperature is required at all is each
    // caller's own rule, and the min/max comparison below dereferences the number.
    if (temperature.getNumericValue() == null) {
      return null;
    }
    if (!QuantityInfo.canStoreWithoutRounding(temperature.getNumericValue())) {
      errors.rejectValue(field, "errors.inventory.temperature.notStorable");
      return null;
    }
    return temperature.toQuantityInfo();
  }

  void validateSubsampleQuantities(
      Supplier<List<? extends ApiSubSampleInfo>> apiSamplePost, Errors errors) {
    for (int i = 0; i < apiSamplePost.get().size(); i++) {
      ApiSubSampleInfo sub = apiSamplePost.get().get(i);
      // A null element ("subSamples": [null]) is already a bean-validation error at binding, but
      // the controllers accept a BindingResult so this validator still runs; dereferencing the
      // element would turn that reported 400 into a 500.
      if (sub != null && sub.getQuantity() != null) {
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
