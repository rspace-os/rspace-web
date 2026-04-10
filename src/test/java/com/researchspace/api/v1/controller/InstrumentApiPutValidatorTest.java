package com.researchspace.api.v1.controller;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.model.record.BaseRecord;
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
  public void emptyNameOnPutIsAccepted() {
    ApiInstrument apiInstrument = new ApiInstrument();
    apiInstrument.setName(" ");
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
}
