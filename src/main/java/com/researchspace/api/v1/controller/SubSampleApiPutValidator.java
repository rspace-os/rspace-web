package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiSubSample;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class SubSampleApiPutValidator extends InventoryRecordValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiSubSample.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiSubSample apiSubSamplePut = (ApiSubSample) target;
    // name can be absent
    validateNameTooLong(apiSubSamplePut.getName(), errors);
    validateDescriptionTooLong(apiSubSamplePut.getDescription(), errors);
    validateTags(apiSubSamplePut.getTags(), errors);
    validateInventoryRecordQuantity(apiSubSamplePut, errors);
    validateExtraFields(apiSubSamplePut, errors);
  }
}
