package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiSampleField;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

/** Validates attempt to create sample template field. */
@Component
public class SampleTemplateFieldPostValidator extends SampleTemplateFieldValidator {

  @Override
  public void validate(Object target, Errors errors) {
    ApiSampleField apiTemplateField = (ApiSampleField) target;

    // check field name
    String fieldName = apiTemplateField.getName();
    if (StringUtils.isBlank(fieldName)) {
      errors.rejectValue("name", "errors.inventory.template.empty.field.name", "empty field name");
    } else {
      validateIncomingTemplateFieldName(errors, fieldName);
    }

    // check field type
    ApiFieldType fieldType = apiTemplateField.getType();
    if (fieldType == null) {
      errors.rejectValue("type", "errors.inventory.template.empty.field.type", "empty field type");
    }

    validateIncomingTemplateFieldContent(errors, apiTemplateField);
  }
}
