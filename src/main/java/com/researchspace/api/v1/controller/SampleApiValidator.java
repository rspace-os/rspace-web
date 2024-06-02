package com.researchspace.api.v1.controller;

import static com.researchspace.core.util.TransformerUtils.toList;

import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
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
    QuantityInfo max = null;
    QuantityInfo min = null;
    if (apiSamplePost.getStorageTempMax() != null) {
      max = apiSamplePost.getStorageTempMax().toQuantityInfo();
      validateTemperatureUnit(max, "storageTempMax", errors);
    }
    if (apiSamplePost.getStorageTempMin() != null) {
      min = apiSamplePost.getStorageTempMin().toQuantityInfo();
      validateTemperatureUnit(min, "storageTempMin", errors);
    }
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

  void validateTemperatureUnit(QuantityInfo temp, String field, Errors errors) {
    RSUnitDef def = RSUnitDef.getUnitById(temp.getUnitId());
    if (!def.isTemperature()) {
      errors.rejectValue(field, "errors.inventory.temperature.invalidUnit");
    }
  }

  void validateQuantities(ApiSampleWithFullSubSamples apiSamplePost, Errors errors) {
    validateInventoryRecordQuantity(apiSamplePost, errors);
    validateSubsampleQuantities(() -> apiSamplePost.getSubSamples(), errors);
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
