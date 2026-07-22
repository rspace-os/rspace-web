package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.controller.SamplesApiController.ApiSampleFullPost;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
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

  private void validateQuantityUnit(Errors errors, ApiSampleFullPost apiSamplePost) {
    ApiSampleWithFullSubSamples postedApiSample = apiSamplePost.getApiSample();
    SampleTemplate template = apiSamplePost.getTemplate();
    if (template != null && postedApiSample.getQuantity() != null) {
      RSUnitDef templateUnit = RSUnitDef.getUnitById(template.getDefaultUnitId());
      RSUnitDef sampleUnit = RSUnitDef.getUnitById(postedApiSample.getQuantity().getUnitId());
      if (!templateUnit.isComparable(sampleUnit)) {
        errors.rejectValue(
            "quantity",
            "errors.inventory.sample.unitIncompatibleWithTemplate",
            new Object[] {
              sampleUnit.getId(), sampleUnit.name(), templateUnit.getId(), templateUnit.name()
            },
            null);
      }
    }
  }
}
