package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

/** Validates attempt to create an instrument template field. */
@Component
public class InstrumentTemplateFieldPostValidator extends InstrumentTemplateFieldValidator {

  @Override
  public void validate(Object target, Errors errors) {
    ApiInventoryEntityField apiTemplateField = (ApiInventoryEntityField) target;

    String fieldName = apiTemplateField.getName();
    if (StringUtils.isBlank(fieldName)) {
      errors.rejectValue("name", "errors.inventory.template.emptyFieldName");
    } else {
      validateIncomingTemplateFieldName(errors, fieldName);
    }

    ApiFieldType fieldType = apiTemplateField.getType();
    if (fieldType == null) {
      errors.rejectValue("type", "errors.inventory.template.emptyFieldType");
    }

    validateIncomingTemplateFieldContent(errors, apiTemplateField);
  }
}
