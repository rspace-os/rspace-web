package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validates incoming SampleTemplate put request. */
@Component
public class SampleTemplatePutValidator extends SampleTemplateValidator {

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiSampleTemplate.class.isAssignableFrom(clazz);
  }

  @Override
  protected Validator getIncomingFieldValidator(ApiSampleField incomingField) {
    return incomingField.isNewFieldRequest()
        ? templateFieldPostValidator
        : templateFieldPutValidator;
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiSampleTemplate incomingTemplate = (ApiSampleTemplate) target;
    validateDefaultUnit(errors, incomingTemplate.getDefaultUnitId());
    validateSubSampleAlias(errors, incomingTemplate.getSubSampleAlias());
    validateFields(errors, incomingTemplate.getFields());
  }
}
