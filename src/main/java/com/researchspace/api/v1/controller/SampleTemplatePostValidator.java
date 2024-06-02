package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validates incoming SampleTemplate posts */
@Component
public class SampleTemplatePostValidator extends SampleTemplateValidator {

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiSampleTemplatePost.class.isAssignableFrom(clazz);
  }

  @Override
  protected Validator getIncomingFieldValidator(ApiSampleField incomingField) {
    return templateFieldPostValidator;
  }

  @Override
  public void validate(Object target, Errors errors) {
    coreValidationForSamplesAndTemplatesPost(target, errors);
    ApiSampleTemplatePost templatePost = (ApiSampleTemplatePost) target;
    validateDefaultUnit(errors, templatePost.getDefaultUnitId());
    validateSubSampleAlias(errors, templatePost.getSubSampleAlias());
    validateFields(errors, templatePost.getFields());
  }
}
