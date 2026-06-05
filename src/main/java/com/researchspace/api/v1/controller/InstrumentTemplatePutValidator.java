package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.service.inventory.InventoryFieldNameUniquenessValidator;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validates incoming InstrumentTemplate put requests. */
@Component
public class InstrumentTemplatePutValidator extends InstrumentTemplateValidator {

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiInstrumentTemplate.class.isAssignableFrom(clazz);
  }

  @Override
  protected Validator getIncomingFieldValidator(ApiInventoryEntityField incomingField) {
    return incomingField.isNewFieldRequest()
        ? templateFieldPostValidator
        : templateFieldPutValidator;
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiInstrumentTemplate incomingTemplate = (ApiInstrumentTemplate) target;
    validateNameTooLong(incomingTemplate.getName(), errors);
    validateDescriptionTooLong(incomingTemplate.getDescription(), errors);
    validateTags(incomingTemplate.getTags(), errors);
    validateInventoryRecordQuantity(incomingTemplate, errors);
    validateFields(errors, incomingTemplate.getFields());
    InventoryFieldNameUniquenessValidator.rejectDuplicatesInPayload(
        incomingTemplate.getFields(), incomingTemplate.getExtraFields(), errors);
  }
}
