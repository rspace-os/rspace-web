package com.researchspace.model.dtos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.field.RadioFieldForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class RadioFieldDTOValidatorTest {

  static final RadioFieldDTO<RadioFieldForm> NO_NAME = createNoName();

  RadioFieldDTO<RadioFieldForm> validInstance;
  private RadioFieldDTOValidator validator;
  private Errors errors;

  @BeforeEach
  public void setUp() throws Exception {
    validator = new RadioFieldDTOValidator();
    validInstance = createValidInstance();
  }

  private RadioFieldDTO<RadioFieldForm> createValidInstance() {
    return createValid();
  }

  public static RadioFieldDTO<RadioFieldForm> createValid() {
    return new RadioFieldDTO<RadioFieldForm>("a=b&c=d", "b", "Radios", false, false);
  }

  private static RadioFieldDTO<RadioFieldForm> createNoName() {
    return new RadioFieldDTO<RadioFieldForm>("a=b&c=d", "b", "", false, false);
  }

  @Test
  public void testSupports() {
    assertTrue(validator.supports(RadioFieldDTO.class));
  }

  @Test
  public void testValidateMissingName() {
    Errors errors = new BeanPropertyBindingResult(NO_NAME, "MyObject");
    validator.validate(NO_NAME, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError("errors.noValue.name", errors));
  }

  @Test
  public void testValidHasNoErrors() {
    Errors errors = new BeanPropertyBindingResult(validInstance, "MyObject");
    RadioFieldDTO<RadioFieldForm> dto = validInstance;

    validator.validate(dto, errors);
    assertFalse(errors.hasErrors());
  }

  @Test
  public void testinvalidRadioOptions() {
    Errors errors = new BeanPropertyBindingResult(validInstance, "MyObject");
    RadioFieldDTO<RadioFieldForm> dto = validInstance;
    dto.setRadioValues("a-b");

    validator.validate(dto, errors);
    assertTrue(ValidationTestUtils.hasError("form.choiceOptions.invalidFormat", errors));
  }

  @Test
  public void testSingleChpoceOK() {
    Errors errors = new BeanPropertyBindingResult(validInstance, "MyObject");
    RadioFieldDTO<RadioFieldForm> dto = validInstance;
    dto.setRadioValues("a=b"); // single Radio option
    validator.validate(dto, errors);
    assertFalse(errors.hasErrors());
  }
}
