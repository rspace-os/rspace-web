package com.researchspace.model.dtos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.field.TimeFieldForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class TimeFieldDTOValidatorTest {

  static TimeFieldDTO<TimeFieldForm> INITIAL_VALID;
  private TimeFieldDTOValidator validator;

  @BeforeEach
  public void setUp() throws Exception {
    INITIAL_VALID = createValid();
    validator = new TimeFieldDTOValidator();
  }

  public static TimeFieldDTO<TimeFieldForm> createValid() {
    TimeFieldDTO<TimeFieldForm> nfdto =
        new TimeFieldDTO<TimeFieldForm>("11:51 AM", "11:12 AM", "11:52 AM", "hh:mm a", "time");
    return nfdto;
  }

  private BeanPropertyBindingResult setUpErrorsObject() {
    return new BeanPropertyBindingResult(INITIAL_VALID, "MyObject");
  }

  @Test
  public void testSupports() {
    assertTrue(validator.supports(TimeFieldDTO.class));
  }

  @Test
  public void testValidHasNoErrors() {
    Errors errors = setUpErrorsObject();
    TimeFieldDTO<TimeFieldForm> dto = INITIAL_VALID;
    TimeFieldForm tff = dto.createFieldForm();
    validator.validate(dto, errors);
    assertFalse(errors.hasErrors());
  }

  @Test
  public void testNoNameInvalid() {
    Errors errors = setUpErrorsObject();
    TimeFieldDTO<TimeFieldForm> dto = INITIAL_VALID;
    dto.setName("");

    validator.validate(dto, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError("errors.noValue.name", errors));
  }

  @Test
  public void testInvalidFormats() {
    Errors errors = setUpErrorsObject();
    TimeFieldDTO<TimeFieldForm> dto = INITIAL_VALID;
    dto.setTimeFormat("invalid format");
    validator.validate(dto, errors);
    assertTrue(ValidationTestUtils.hasError("errors.invalidDateFormattingString", errors));
  }
}
