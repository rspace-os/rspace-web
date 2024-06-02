package com.researchspace.model.dtos;

import static com.researchspace.model.dtos.AbstractFormFieldDTO.MAX_NAME_LENGTH;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.field.TextFieldForm;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class TextFieldDTOValidatorTest {

  static final TextFieldDTO<TextFieldForm> NO_NAME = createNoName();
  static final TextFieldDTO<TextFieldForm> VALID = createValid();
  private TextFieldValidator validator;

  @Before
  public void setUp() throws Exception {
    validator = new TextFieldValidator();
  }

  public static TextFieldDTO<TextFieldForm> createValid() {
    return new TextFieldDTO<>("name", "");
  }

  private static TextFieldDTO<TextFieldForm> createNoName() {
    return new TextFieldDTO<>("", "");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSupports() {
    assertTrue(validator.supports(TextFieldDTO.class));
  }

  @Test
  public void testValidateMissingName() {
    Errors errors = new BeanPropertyBindingResult(NO_NAME, "MyObject");
    validator.validate(NO_NAME, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError("no.name", errors));
  }

  @Test
  public void testMaxName() {
    Errors errors = new BeanPropertyBindingResult(VALID, "MyObject");

    TextFieldDTO<TextFieldForm> dto = VALID;
    dto.setName(randomAlphabetic(MAX_NAME_LENGTH + 1));
    validator.validate(dto, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError("errors.maxlength", errors));
  }

  @Test
  public void testValidHasNoErrors() {
    Errors errors = new BeanPropertyBindingResult(VALID, "MyObject");
    TextFieldDTO<TextFieldForm> dto = VALID;
    validator.validate(dto, errors);
    assertFalse(errors.hasErrors());
  }
}
