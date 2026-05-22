package com.researchspace.api.v1.controller;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.Assert.assertEquals;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.inventory.InventoryFieldNameUniquenessValidator;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class InstrumentApiPutValidatorTest extends InventoryRecordValidationTestBase {

  @Autowired private InstrumentApiPutValidator instrumentPutValidator;

  @Before
  public void setup() {
    validator = instrumentPutValidator;
  }

  @Test
  public void emptyNameOnPutIsNotAccepted() {
    ApiInstrument apiInstrument = new ApiInstrument();
    apiInstrument.setName(" ");
    Errors e = new BeanPropertyBindingResult(apiInstrument, "instrumentPut");
    validator.validate(apiInstrument, e);
    assertEquals(1, e.getErrorCount());
  }

  @Test
  public void nullNameOnPutIsAccepted() {
    ApiInstrument apiInstrument = new ApiInstrument();
    apiInstrument.setName(null);
    apiInstrument.setDescription("updated");
    Errors e = new BeanPropertyBindingResult(apiInstrument, "instrumentPut");
    validator.validate(apiInstrument, e);
    assertEquals(0, e.getErrorCount());
  }

  @Test
  public void tooLongNameRejected() {
    ApiInstrument apiInstrument = new ApiInstrument();
    apiInstrument.setName(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    Errors e = new BeanPropertyBindingResult(apiInstrument, "instrumentPut");
    validator.validate(apiInstrument, e);
    assertEquals(1, e.getErrorCount());
    assertMaxLengthMsg(e);
  }

  @Test
  public void tooLongDescriptionRejected() {
    ApiInstrument apiInstrument = new ApiInstrument();
    apiInstrument.setName("instr");
    apiInstrument.setDescription(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    assertDescriptionValidation(apiInstrument);
  }

  @Test
  public void invalidTagsRejected() {
    ApiInstrument apiInstrument = new ApiInstrument();
    apiInstrument.setName("instr");
    apiInstrument.setApiTagInfo(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    assertTagsTooLongValidation(apiInstrument);

    apiInstrument.setApiTagInfo("<script>"); // contains invalid chars
    Errors e = new BeanPropertyBindingResult(apiInstrument, "instrumentPut");
    validator.validate(apiInstrument, e);
    assertEquals("errors.invalidchars", e.getGlobalError().getCode());
  }

  @Test
  public void rejectsDuplicateExtraFieldNames() {
    ApiInstrument apiInstrument = new ApiInstrument();
    apiInstrument.setName("instr");
    ApiExtraField ef1 = new ApiExtraField();
    ef1.setName("Notes");
    ApiExtraField ef2 = new ApiExtraField();
    ef2.setName("notes"); // case-insensitive duplicate
    apiInstrument.getExtraFields().add(ef1);
    apiInstrument.getExtraFields().add(ef2);

    Errors e = new BeanPropertyBindingResult(apiInstrument, "instrumentPut");
    validator.validate(apiInstrument, e);
    assertEquals(1, e.getErrorCount());
    assertEquals("extraFields[1].name", e.getFieldError().getField());
    assertEquals(
        InventoryFieldNameUniquenessValidator.DUPLICATE_NAME_ERROR_CODE,
        e.getFieldError().getCode());
  }

  @Test
  public void rejectsDuplicateFieldNames() {
    ApiInstrument apiInstrument = new ApiInstrument();
    apiInstrument.setName("instr");
    ApiInventoryEntityField f1 = new ApiInventoryEntityField();
    f1.setName("Calibration");
    ApiInventoryEntityField f2 = new ApiInventoryEntityField();
    f2.setName("calibration"); // case-insensitive duplicate
    apiInstrument.getFields().add(f1);
    apiInstrument.getFields().add(f2);

    Errors e = new BeanPropertyBindingResult(apiInstrument, "instrumentPut");
    validator.validate(apiInstrument, e);
    assertEquals(1, e.getErrorCount());
    assertEquals("fields[1].name", e.getFieldError().getField());
    assertEquals(
        InventoryFieldNameUniquenessValidator.DUPLICATE_NAME_ERROR_CODE,
        e.getFieldError().getCode());
  }

  @Test
  public void rejectsDuplicateNameAcrossFieldsAndExtraFields() {
    ApiInstrument apiInstrument = new ApiInstrument();
    apiInstrument.setName("instr");
    ApiInventoryEntityField f1 = new ApiInventoryEntityField();
    f1.setName("Serial Number");
    ApiExtraField ef1 = new ApiExtraField();
    ef1.setName("serial number"); // case-insensitive cross-collection duplicate
    apiInstrument.getFields().add(f1);
    apiInstrument.getExtraFields().add(ef1);

    Errors e = new BeanPropertyBindingResult(apiInstrument, "instrumentPut");
    validator.validate(apiInstrument, e);
    assertEquals(1, e.getErrorCount());
    assertEquals("extraFields[0].name", e.getFieldError().getField());
    assertEquals(
        InventoryFieldNameUniquenessValidator.DUPLICATE_NAME_ERROR_CODE,
        e.getFieldError().getCode());
  }
}
