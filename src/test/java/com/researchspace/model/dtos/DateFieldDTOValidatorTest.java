package com.researchspace.model.dtos;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.field.DateFieldForm;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class DateFieldDTOValidatorTest {

  static DateFieldDTO<DateFieldForm> INITIAL_VALID;
  private DateFieldDTOValidator validator;

  @Before
  public void setUp() throws Exception {
    INITIAL_VALID = createValid();
    validator = new DateFieldDTOValidator();
  }

  public static DateFieldDTO<DateFieldForm> createValid() {
    DateFieldDTO<DateFieldForm> nfdto =
        new DateFieldDTO<>("1/1/2010", "31/12/2009", "2/2/2010", "dd/mm/yyyy", "name");
    return nfdto;
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSupports() {
    assertTrue(validator.supports(DateFieldDTO.class));
  }

  @Test
  public void testValidHasNoErrors() {
    Errors errors = setUpErrorsObject();
    DateFieldDTO<DateFieldForm> dto = INITIAL_VALID;

    validator.validate(dto, errors);
    assertFalse(errors.hasErrors());
  }

  @Test
  public void testNoNameInvalid() {
    Errors errors = setUpErrorsObject();
    DateFieldDTO<DateFieldForm> dto = INITIAL_VALID;
    dto.setName("");

    validator.validate(dto, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError("no.name", errors));
  }

  @Test
  public void testInvalidFormats() {
    Errors errors = setUpErrorsObject();
    DateFieldDTO<DateFieldForm> dto = INITIAL_VALID;
    dto.setDateFormat("invalid format");
    validator.validate(dto, errors);
    assertTrue(ValidationTestUtils.hasError("errors.invalidDateFormattingString", errors));
  }

  @Test
  public void testMaxDateDoesntMatchDateFormat() {
    Errors errors = setUpErrorsObject();
    DateFieldDTO<DateFieldForm> dto = createValid();
    dto.setMaxValue("invalid");
    validator.validate(dto, errors);
    assertTrue(ValidationTestUtils.hasError("errors.invalidDateFormat", errors));
  }

  @Test
  public void testMinDateDoesntMatchDateFormat() {
    Errors errors = setUpErrorsObject();
    DateFieldDTO<DateFieldForm> dto = createValid();
    dto.setMinValue("invalid");
    validator.validate(dto, errors);
    assertTrue(ValidationTestUtils.hasError("errors.invalidDateFormat", errors));
  }

  @Test
  public void testDefaultDateDoesntMatchDateFormat() {
    Errors errors = setUpErrorsObject();
    DateFieldDTO<DateFieldForm> dto = createValid();
    dto.setDefaultValue("invalid");
    validator.validate(dto, errors);
    assertTrue(ValidationTestUtils.hasError("errors.invalidDateFormat", errors));
  }

  @Test
  public void testDefaultDefaultDateDOutsideRange() {
    Errors errors = setUpErrorsObject();
    DateFieldDTO<DateFieldForm> dto = createValid();
    dto.setDefaultValue("21/21/5000");
    validator.validate(dto, errors);
    assertTrue(ValidationTestUtils.hasError("errors.dateOutsideAllowedRange", errors));
  }

  @Test
  public void testDefaultMinDateLAterTahMaxDate() {
    Errors errors = setUpErrorsObject();
    DateFieldDTO<DateFieldForm> dto = createValid();
    dto.setMinValue("21/21/5000");
    validator.validate(dto, errors);
    assertTrue(ValidationTestUtils.hasError("errors.minDateLaterThanMaxDate", errors));
  }

  private BeanPropertyBindingResult setUpErrorsObject() {
    return new BeanPropertyBindingResult(INITIAL_VALID, "MyObject");
  }

  @Test
  public void testNoDefaultsOK() {
    Errors errors = setUpErrorsObject();
    DateFieldDTO<DateFieldForm> dto = INITIAL_VALID;
    dto.setMaxValue("");
    dto.setMinValue("");
    dto.setDefaultValue("");

    validator.validate(dto, errors);
    assertFalse(errors.hasErrors());
  }
}
