package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.controller.SamplesApiController.ApiSampleFullPost;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.service.ApiFieldsHelper;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.units.RSUnitDef;
import java.util.List;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validator for creating a new sample */
@Component
public class SampleApiPostFullValidator implements Validator {

  @Autowired private ApiFieldsHelper fieldHelper;

  @Data
  @AllArgsConstructor
  public static class ErrorAggregator implements Consumer<String> {
    Errors errors;

    @Override
    public void accept(String errorMessage) {
      if (!StringUtils.isEmpty(errorMessage)) {
        errors.reject("errors.inventory.field.validation", new Object[] {errorMessage}, null);
      }
    }
  }

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(ApiSampleFullPost.class);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiSampleFullPost apiSamplePost = (ApiSampleFullPost) target;
    validateSeries(errors, apiSamplePost);
    validateFieldsAndTemplateFields(errors, apiSamplePost);
    validateQuantityUnit(errors, apiSamplePost);
  }

  private void validateSeries(Errors errors, ApiSampleFullPost apiSamplePost) {
    ApiSampleWithFullSubSamples apiSample = apiSamplePost.getApiSample();
    Integer subSamplesCount = apiSample.getNewSampleSubSamplesCount();

    // if we are defining instructions to create subsamples,we can't include subsample definition as
    // well.
    if (subSamplesCount != null && !CollectionUtils.isEmpty(apiSample.getSubSamples())) {
      errors.reject("errors.inventory.sample.subSamplesArrayNotEmpty");
    }
    if (subSamplesCount != null) {
      verifyAcceptableCount(subSamplesCount, "subSamplesCount", errors);
    }
  }

  private void verifyAcceptableCount(Integer actual, String field, Errors errors) {
    if (actual < 1 || actual > 100) {
      errors.reject(
          "errors.inventory.sample.subSamplesCountOutOfRange", new Object[] {field, actual}, null);
    }
  }

  private void validateFieldsAndTemplateFields(Errors errors, ApiSampleFullPost apiSamplePost) {
    if (apiSamplePost.getTemplate() != null) {
      List<ApiInventoryEntityField> incomingApiFields = apiSamplePost.getApiSample().getFields();
      List<InventoryEntityField> templateFields = apiSamplePost.getTemplate().getActiveFields();

      if (!incomingApiFields.isEmpty()) {
        // run validation against template fields
        fieldHelper.checkApiFieldsMatchingFormFields(
            incomingApiFields,
            templateFields,
            apiSamplePost.getUser(),
            new ErrorAggregator(errors));
      }

      fieldHelper.validateMandatoryFieldsForEntityPost(incomingApiFields, templateFields, errors);
    }
  }

  /**
   * A template fixes the measurement category of the samples made from it. The sample's total is
   * derived from its subsamples when any are posted, so each posted subsample quantity is checked
   * against the template as well as the optional top-level quantity; otherwise a comparable
   * top-level value could stand in front of children in another category.
   */
  private void validateQuantityUnit(Errors errors, ApiSampleFullPost apiSamplePost) {
    ApiSampleWithFullSubSamples postedApiSample = apiSamplePost.getApiSample();
    SampleTemplate template = apiSamplePost.getTemplate();
    if (template == null) {
      return;
    }
    RSUnitDef templateUnit = RSUnitDef.getUnitById(template.getDefaultUnitId());
    rejectUnitIncompatibleWithTemplate(
        errors, "quantity", postedApiSample.getQuantity(), templateUnit);
    if (postedApiSample.getSubSamples() == null) {
      return;
    }
    int index = 0;
    for (ApiSubSample subSample : postedApiSample.getSubSamples()) {
      rejectUnitIncompatibleWithTemplate(
          errors,
          String.format("subSamples[%d].quantity", index++),
          subSample == null ? null : subSample.getQuantity(),
          templateUnit);
    }
  }

  private void rejectUnitIncompatibleWithTemplate(
      Errors errors, String field, ApiQuantityInfo quantity, RSUnitDef templateUnit) {
    if (quantity == null
        || quantity.getUnitId() == null
        || !RSUnitDef.exists(quantity.getUnitId())) {
      return; // absent, or already rejected as an invalid unit by the quantity validator
    }
    RSUnitDef sampleUnit = RSUnitDef.getUnitById(quantity.getUnitId());
    if (!templateUnit.isComparable(sampleUnit)) {
      errors.rejectValue(
          field,
          "errors.inventory.sample.unitIncompatibleWithTemplate",
          new Object[] {
            sampleUnit.getId(), sampleUnit.name(), templateUnit.getId(), templateUnit.name()
          },
          null);
    }
  }
}
