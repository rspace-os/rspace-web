package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

@Component
public class SampleApiPostValidator extends SampleApiValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiSampleWithFullSubSamples.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiSampleInfo apiSamplePost = coreValidationForSamplesAndTemplatesPost(target, errors);
    validateApiExtraFieldsInNewSample((ApiSampleWithFullSubSamples) apiSamplePost, errors);
    validateSubsampleQuantities(
        () -> ((ApiSampleWithFullSubSamples) apiSamplePost).getSubSamples(), errors);
  }

  ApiSampleInfo coreValidationForSamplesAndTemplatesPost(Object target, Errors errors) {
    ApiSampleInfo apiSamplePost = (ApiSampleInfo) target;
    ValidationUtils.rejectIfEmptyOrWhitespace(
        errors, "name", "errors.required", new Object[] {"name"}, "name is required");
    validateNameTooLong(apiSamplePost.getName(), errors);
    validateDescriptionTooLong(apiSamplePost.getDescription(), errors);
    validateTags(apiSamplePost.getTags(), errors);
    validateInventoryRecordQuantity(apiSamplePost, errors);
    validateStorageTemperatures(errors, apiSamplePost);
    return apiSamplePost;
  }

  private void validateApiExtraFieldsInNewSample(
      ApiSampleWithFullSubSamples apiSample, Errors errors) {
    validateExtraFields(apiSample, errors);
    if (!CollectionUtils.isEmpty(apiSample.getSubSamples())) {
      int j = 0;
      for (ApiSubSample ss : apiSample.getSubSamples()) {
        errors.pushNestedPath(String.format("subSamples[%d]", j++));
        validateExtraFields(ss, errors);
        errors.popNestedPath();
      }
    }
  }
}
