package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiSampleField;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

/** Validates attempt to update sample template field. */
@Component
public class SampleTemplateFieldPutValidator extends SampleTemplateFieldValidator {

  @Override
  public void validate(Object target, Errors errors) {
    ApiSampleField apiTemplateField = (ApiSampleField) target;

    // check field name valid, if provided
    String fieldName = apiTemplateField.getName();
    if (!StringUtils.isBlank(fieldName)) {
      validateIncomingTemplateFieldName(errors, fieldName);
    }

    validateIncomingTemplateFieldContent(errors, apiTemplateField);
  }
}
