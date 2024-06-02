package com.researchspace.model.dtos;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.record.BaseRecord;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class StringFieldDTOValidatorTest {

  static final StringFieldDTO<StringFieldForm> NO_NAME = createNoName();
  static final StringFieldDTO<StringFieldForm> VALID = createValid();
  private StringFieldDTOValidator validator;

  @Before
  public void setUp() throws Exception {
    validator = new StringFieldDTOValidator();
  }

  private static StringFieldDTO<StringFieldForm> createValid() {
    StringFieldDTO<StringFieldForm> nfdto = new StringFieldDTO<>("name", "true", "");
    return nfdto;
  }

  private static StringFieldDTO<StringFieldForm> createNoName() {
    StringFieldDTO<StringFieldForm> nfdto = new StringFieldDTO<>("", "true", "default value");
    return nfdto;
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSupports() {
    assertTrue(validator.supports(StringFieldDTO.class));
  }

  @Test
  public void testValidateMissingName() {
    Errors errors = new BeanPropertyBindingResult(NO_NAME, "MyObject");
    validator.validate(NO_NAME, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError("no.name", errors));
  }

  @Test
  public void testTooLongDefaultValue() {
    StringFieldDTO<StringFieldForm> nfdto = createValid();
    nfdto.setDefaultStringValue(
        RandomStringUtils.randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    Errors errors = new BeanPropertyBindingResult(nfdto, "MyObject");
    validator.validate(nfdto, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError("errors.maxlength", errors));
  }

  @Test
  public void testValidHasNoErrors() {
    Errors errors = new BeanPropertyBindingResult(VALID, "MyObject");
    StringFieldDTO<StringFieldForm> dto = VALID;

    validator.validate(dto, errors);
    assertFalse(errors.hasErrors());
  }
}
