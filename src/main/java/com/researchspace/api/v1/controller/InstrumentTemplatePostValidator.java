package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.service.inventory.InventoryFieldNameUniquenessValidator;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validates incoming InstrumentTemplate posts. */
@Component
public class InstrumentTemplatePostValidator extends InstrumentTemplateValidator {

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiInstrumentTemplatePost.class.isAssignableFrom(clazz);
  }

  @Override
  protected Validator getIncomingFieldValidator(ApiInventoryEntityField incomingField) {
    return templateFieldPostValidator;
  }

  @Override
  public void validate(Object target, Errors errors) {
    coreValidationForInstrumentsAndTemplatesPost(target, errors);
    ApiInstrumentTemplatePost templatePost = (ApiInstrumentTemplatePost) target;
    validateFields(errors, templatePost.getFields());
    InventoryFieldNameUniquenessValidator.rejectDuplicatesInPayload(
        templatePost.getFields(), templatePost.getExtraFields(), errors);
  }
}
