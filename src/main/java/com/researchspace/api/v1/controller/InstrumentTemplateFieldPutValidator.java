package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiInventoryEntityField;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

/** Validates attempt to update an instrument template field. */
@Component
public class InstrumentTemplateFieldPutValidator extends InstrumentTemplateFieldValidator {

  @Override
  public void validate(Object target, Errors errors) {
    ApiInventoryEntityField apiTemplateField = (ApiInventoryEntityField) target;

    String fieldName = apiTemplateField.getName();
    if (!StringUtils.isBlank(fieldName)) {
      validateIncomingTemplateFieldName(errors, fieldName);
    }

    validateIncomingTemplateFieldContent(errors, apiTemplateField);
  }
}
