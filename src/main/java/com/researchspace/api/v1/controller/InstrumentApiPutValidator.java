package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.service.inventory.InventoryFieldNameUniquenessValidator;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class InstrumentApiPutValidator extends InstrumentApiValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiInstrument.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiInstrument apiInstrumentPut = (ApiInstrument) target;
    // name can be absent if not renaming
    validateNameTooLong(apiInstrumentPut.getName(), errors);
    validateDescriptionTooLong(apiInstrumentPut.getDescription(), errors);
    validateTags(apiInstrumentPut.getTags(), errors);
    validateInventoryRecordQuantity(apiInstrumentPut, errors);
    validateExtraFields(apiInstrumentPut, errors);
    InventoryFieldNameUniquenessValidator.rejectDuplicatesInPayload(
        apiInstrumentPut.getFields(), apiInstrumentPut.getExtraFields(), errors);
    validateNotNullAndBlank("name", apiInstrumentPut.getName(), errors);
  }
}
