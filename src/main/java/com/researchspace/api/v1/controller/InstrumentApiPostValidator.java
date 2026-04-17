package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiInstrumentEntityInfo;
import com.researchspace.model.inventory.Instrument;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

@Component
public class InstrumentApiPostValidator extends InstrumentApiValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return Instrument.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiInstrumentEntityInfo apiInstrumentPost =
        coreValidationForInstrumentsAndTemplatesPost(target, errors);
    validateApiExtraFieldsInNewInstrument(apiInstrumentPost, errors);
  }

  ApiInstrumentEntityInfo coreValidationForInstrumentsAndTemplatesPost(
      Object target, Errors errors) {
    ApiInstrumentEntityInfo apiInstrumentPost = (ApiInstrumentEntityInfo) target;
    ValidationUtils.rejectIfEmptyOrWhitespace(
        errors, "name", "errors.required", new Object[] {"name"}, "name is required");
    validateNameTooLong(apiInstrumentPost.getName(), errors);
    validateDescriptionTooLong(apiInstrumentPost.getDescription(), errors);
    validateTags(apiInstrumentPost.getTags(), errors);
    validateInventoryRecordQuantity(apiInstrumentPost, errors);
    return apiInstrumentPost;
  }

  private void validateApiExtraFieldsInNewInstrument(
      ApiInstrumentEntityInfo apiInstrument, Errors errors) {
    validateExtraFields(apiInstrument, errors);
  }
}
