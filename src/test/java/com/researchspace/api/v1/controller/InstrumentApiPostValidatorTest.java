package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.service.inventory.InventoryFieldNameUniquenessValidator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class InstrumentApiPostValidatorTest extends InventoryRecordValidationTestBase {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @Autowired private InstrumentApiPostValidator instrumentPostValidator;

  @Before
  public void setup() {
    validator = instrumentPostValidator;
  }

  @Test
  public void rejectsDuplicateExtraFieldNames() {
    ApiInstrument apiInstrument = new ApiInstrument();
    apiInstrument.setName("i1");
    ApiExtraField ef1 = new ApiExtraField();
    ef1.setName("Notes");
    ApiExtraField ef2 = new ApiExtraField();
    ef2.setName("notes"); // case-insensitive duplicate
    apiInstrument.getExtraFields().add(ef1);
    apiInstrument.getExtraFields().add(ef2);

    Errors e = new BeanPropertyBindingResult(apiInstrument, "instrumentPost");
    validator.validate(apiInstrument, e);
    assertEquals(1, e.getErrorCount());
    assertEquals("extraFields[1].name", e.getFieldError().getField());
    assertEquals(
        InventoryFieldNameUniquenessValidator.DUPLICATE_NAME_ERROR_CODE,
        e.getFieldError().getCode());
  }
}
