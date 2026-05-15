package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.controller.SamplesApiController.ApiSampleFullPost;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.service.ApiFieldsHelper;
import com.researchspace.model.inventory.Sample;
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
  private static final String SAMPLE_ENTITY_NAME_TYPE = "sample";

  @Data
  @AllArgsConstructor
  public static class ErrorAggregator implements Consumer<String> {
    Errors errors;

    @Override
    public void accept(String errorMessage) {
      if (!StringUtils.isEmpty(errorMessage)) {
        errors.reject("", errorMessage);
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
      errors.reject("", "subSamples array must be empty if newSampleSubSamplesCount is provided");
    }
    if (subSamplesCount != null) {
      verifyAcceptableCount(subSamplesCount, "subSamplesCount", errors);
    }
  }

  private void verifyAcceptableCount(Integer actual, String field, Errors errors) {
    if (actual < 1 || actual > 100) {
      errors.reject("", String.format("%s supported values are 1-100, was [%d]", field, actual));
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

      fieldHelper.validateMandatoryFieldsForEntityPost(
          SAMPLE_ENTITY_NAME_TYPE, incomingApiFields, templateFields, errors);
    }
  }

  private void validateQuantityUnit(Errors errors, ApiSampleFullPost apiSamplePost) {
    ApiSampleWithFullSubSamples postedApiSample = apiSamplePost.getApiSample();
    Sample template = apiSamplePost.getTemplate();
    if (template != null && postedApiSample.getQuantity() != null) {
      RSUnitDef templateUnit = RSUnitDef.getUnitById(template.getDefaultUnitId());
      RSUnitDef sampleUnit = RSUnitDef.getUnitById(postedApiSample.getQuantity().getUnitId());
      if (!templateUnit.isComparable(sampleUnit)) {
        errors.rejectValue(
            "quantity",
            "errors.inventory.sample.unit.incompatible.with.template",
            new Object[] {
              sampleUnit.getId(), sampleUnit.name(), templateUnit.getId(), templateUnit.name()
            },
            "incompatible sample unit");
      }
    }
  }
}
