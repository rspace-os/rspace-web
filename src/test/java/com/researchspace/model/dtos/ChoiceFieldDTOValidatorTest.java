package com.researchspace.model.dtos;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.field.ChoiceFieldForm;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class ChoiceFieldDTOValidatorTest {

  static final ChoiceFieldDTO<ChoiceFieldForm> NO_NAME = createNoName();
  static final ChoiceFieldDTO<ChoiceFieldForm> VALID = createValid();
  static final ChoiceFieldDTO<ChoiceFieldForm> VALID_WITH_SPACES = createValidWithSpaces();
  static final ChoiceFieldDTO<ChoiceFieldForm> INVALID_CHARS_IN_NAME = createBadChars();
  private ChoiceFieldDTOValidator validator;

  @Before
  public void setUp() throws Exception {
    validator = new ChoiceFieldDTOValidator();
  }

  private static ChoiceFieldDTO<ChoiceFieldForm> createBadChars() {
    return new ChoiceFieldDTO<ChoiceFieldForm>("a=val&2 1&c=val 2", "no", "b", "choices");
  }

  private static ChoiceFieldDTO<ChoiceFieldForm> createValidWithSpaces() {
    return new ChoiceFieldDTO<ChoiceFieldForm>("a=val 1&c=val 2", "no", "b", "choices");
  }

  public static ChoiceFieldDTO<ChoiceFieldForm> createValid() {
    return new ChoiceFieldDTO<ChoiceFieldForm>("a=b&c=d", "no", "b", "choices");
  }

  private static ChoiceFieldDTO<ChoiceFieldForm> createNoName() {
    return new ChoiceFieldDTO<ChoiceFieldForm>("a=b&c=d", "no", "a=b", "");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSupports() {
    assertTrue(validator.supports(ChoiceFieldDTO.class));
  }

  @Test
  public void testValidateMissingName() {
    Errors errors = new BeanPropertyBindingResult(NO_NAME, "MyObject");
    validator.validate(NO_NAME, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError("no.name", errors));
  }

  @Test
  public void testValidHasNoErrors() {
    Errors errors = new BeanPropertyBindingResult(VALID, "MyObject");
    ChoiceFieldDTO<ChoiceFieldForm> dto = VALID;

    validator.validate(dto, errors);
    assertFalse(errors.hasErrors());

    errors = new BeanPropertyBindingResult(VALID_WITH_SPACES, "MyObject");
    dto = VALID_WITH_SPACES;

    validator.validate(dto, errors);
    assertFalse(errors.hasErrors());
  }

  @Test
  public void testinvalidChoiceOptions() {
    Errors errors = new BeanPropertyBindingResult(VALID, "MyObject");
    ChoiceFieldDTO<ChoiceFieldForm> dto = VALID;
    dto.setChoiceValues("a-b");

    validator.validate(dto, errors);
    assertTrue(ValidationTestUtils.hasError("choiceoptions.invalidformat", errors));

    errors = new BeanPropertyBindingResult(VALID, "MyObject");
    dto = INVALID_CHARS_IN_NAME;
    validator.validate(dto, errors);
    assertTrue(ValidationTestUtils.hasError("choiceoptions.invalidformat", errors));
  }

  @Test
  public void testSingleChpoceOK() {
    Errors errors = new BeanPropertyBindingResult(VALID, "MyObject");
    ChoiceFieldDTO<ChoiceFieldForm> dto = VALID;
    dto.setChoiceValues("a=b"); // single choice option
    validator.validate(dto, errors);
    assertFalse(errors.hasErrors());
  }
}
