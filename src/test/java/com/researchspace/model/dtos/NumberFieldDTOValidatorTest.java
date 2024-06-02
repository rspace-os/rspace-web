package com.researchspace.model.dtos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.field.FieldType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class NumberFieldDTOValidatorTest {

  private static final NumberFieldDTO MIN_GT_MAX = createMin_Gt_Max();
  private static final NumberFieldDTO DEFAULT_OUTSIDE_RANGE = createDefaultOutsideRange();
  private static final NumberFieldDTO NO_NAME = createNoName();
  private static final NumberFieldDTO VALID = createValid();
  private static final NumberFieldDTO TOO_LARGE_DP_NUMBER = createTooLargeDP();
  private NumberFieldDTOValidator validator;

  @Before
  public void setUp() throws Exception {
    validator = new NumberFieldDTOValidator();
  }

  private static NumberFieldDTO createValid() {
    NumberFieldDTO nfdto = new NumberFieldDTO("0", "10", "1", "5", FieldType.NUMBER, "name");
    return nfdto;
  }

  private static NumberFieldDTO createValidNoDefaults() {
    NumberFieldDTO nfdto = new NumberFieldDTO("", "", "", "", FieldType.NUMBER, "name");
    return nfdto;
  }

  private static NumberFieldDTO createValidMinOnly() {
    NumberFieldDTO nfdto = new NumberFieldDTO("0", "", "", "", FieldType.NUMBER, "name");
    return nfdto;
  }

  private static NumberFieldDTO createNoName() {
    NumberFieldDTO nfdto = new NumberFieldDTO("0", "10", "1", "12", FieldType.NUMBER, "");
    return nfdto;
  }

  private static NumberFieldDTO createDefaultOutsideRange() {
    NumberFieldDTO nfdto = new NumberFieldDTO("0", "10", "1", "12", FieldType.NUMBER, "anyname");
    return nfdto;
  }

  private static NumberFieldDTO createDefaultltMin() {
    NumberFieldDTO nfdto = new NumberFieldDTO("0", "", "1", "-1", FieldType.NUMBER, "anyname");
    return nfdto;
  }

  private static NumberFieldDTO createDefaultgtMax() {
    NumberFieldDTO nfdto = new NumberFieldDTO("", "10", "1", "12", FieldType.NUMBER, "anyname");
    return nfdto;
  }

  private static NumberFieldDTO createMin_Gt_Max() {
    NumberFieldDTO nfdto = new NumberFieldDTO("10", "0", "1", "5", FieldType.NUMBER, "anyname");
    return nfdto;
  }

  private static NumberFieldDTO createTooLargeDP() {
    NumberFieldDTO nfdto =
        new NumberFieldDTO("10", "0", "123333333333333", "5", FieldType.NUMBER, "anyname");
    return nfdto;
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSupports() {
    assertTrue(validator.supports(NumberFieldDTO.class));
  }

  @Test
  public void testValidateDisorderedRange() {
    Errors errors = new BeanPropertyBindingResult(DEFAULT_OUTSIDE_RANGE, "MyObject");
    validator.validate(DEFAULT_OUTSIDE_RANGE, errors);
    assertTrue(errors.hasErrors());
    assertEquals(1, errors.getErrorCount());
    assertTrue(errors.getGlobalError().getCode().equals("errors.disorderedRange"));
  }

  @Test
  public void testValidateMinGtMax() {
    Errors errors = new BeanPropertyBindingResult(MIN_GT_MAX, "MyObject");
    validator.validate(MIN_GT_MAX, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError("errors.disorderedRange", errors));
  }

  @Test
  public void testValidateDefaultltMin() {
    NumberFieldDTO dto = createDefaultltMin();
    Errors errors = new BeanPropertyBindingResult(dto, "MyObject");
    validator.validate(dto, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError("errors.disorderedRange", errors));
  }

  @Test
  public void testValidateDefaultGtMax() {
    NumberFieldDTO dto = createDefaultgtMax();
    Errors errors = new BeanPropertyBindingResult(dto, "MyObject");
    validator.validate(dto, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError("errors.disorderedRange", errors));
  }

  @Test
  public void testValidateTooLargeDPNumber() {
    Errors errors = new BeanPropertyBindingResult(TOO_LARGE_DP_NUMBER, "MyObject");
    validator.validate(TOO_LARGE_DP_NUMBER, errors);
    assertTrue(errors.hasErrors());
    // decmal places should be between 0 and 127
    assertTrue(ValidationTestUtils.hasError("errors.number.decimalPlaces", errors));

    // check -ve value not allowed
    NumberFieldDTO dto2 = createTooLargeDP();
    dto2.setDecimalPlaces("-1");
    Errors errors2 = new BeanPropertyBindingResult(dto2, "MyObject");
    validator.validate(dto2, errors2);
    assertTrue(ValidationTestUtils.hasError("errors.number.decimalPlaces", errors2));
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
    NumberFieldDTO dto = VALID;

    validator.validate(dto, errors);
    assertFalse(errors.hasErrors());
  }

  @Test
  public void testValidMinOnlyHasNoErrors() {
    Errors errors = new BeanPropertyBindingResult(VALID, "MyObject");
    NumberFieldDTO dto = createValidMinOnly();

    validator.validate(dto, errors);
    assertFalse(errors.hasErrors());
  }

  @Test
  public void testValidNoDefaultsNoErrors() {
    Errors errors = new BeanPropertyBindingResult(VALID, "MyObject");
    NumberFieldDTO dto = createValidNoDefaults();

    validator.validate(dto, errors);
    assertFalse(errors.hasErrors());
  }

  @Test
  public void testNonNumeric() {
    Errors errors = new BeanPropertyBindingResult(VALID, "MyObject");
    NumberFieldDTO localValid = createValid();
    localValid.setDecimalPlaces("a");
    validator.validate(localValid, errors);
    assertTrue(errors.hasErrors());
    assertTrue(ValidationTestUtils.hasError("errors.number.decimalPlaces", errors));
  }
}
