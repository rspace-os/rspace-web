package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class SampleApiPutValidator extends SampleApiValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiSampleWithFullSubSamples.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiSampleWithFullSubSamples apiSamplePut = (ApiSampleWithFullSubSamples) target;
    // name can be absent if not renaming
    validateNameTooLong(apiSamplePut.getName(), errors);
    validateDescriptionTooLong(apiSamplePut.getDescription(), errors);
    validateTags(apiSamplePut.getTags(), errors);
    // additional validation
    validateInventoryRecordQuantity(apiSamplePut, errors);
    validateStorageTemperatures(errors, apiSamplePut);
    validateExtraFields(apiSamplePut, errors);
  }
}
