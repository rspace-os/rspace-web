package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.model.units.RSUnitDef;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Exercises {@link SampleTemplateValidator#validateDefaultUnit} via the concrete POST and PUT
 * validators, covering RSDEV-1067 (templates must reject default units that are not mass, volume,
 * or dimensionless).
 */
public class SampleTemplateValidatorTest extends InventoryRecordValidationTestBase {

  static final String NOT_AMOUNT_ERROR_CODE = "errors.inventory.template.invalid.unitId.notAmount";
  static final String INVALID_UNIT_ERROR_CODE = "errors.inventory.template.invalid.unitId";

  @Autowired private SampleTemplatePostValidator postValidator;
  @Autowired private SampleTemplatePutValidator putValidator;

  @Before
  public void setup() {
    // base class `validator` field unused here — tests pick POST or PUT explicitly.
  }

  // --- POST: default unit must be mass, volume, or dimensionless (isAmount) -------------

  @Test
  public void postRejectsMolarityDefaultUnit() {
    ApiSampleTemplatePost post = newValidPost();
    post.setDefaultUnitId(RSUnitDef.NANOMOLAR.getId()); // id 11 — ticket repro
    assertDefaultUnitErrorCode(validate(postValidator, post), NOT_AMOUNT_ERROR_CODE);
  }

  @Test
  public void postRejectsTemperatureDefaultUnit() {
    ApiSampleTemplatePost post = newValidPost();
    post.setDefaultUnitId(RSUnitDef.CELSIUS.getId());
    assertDefaultUnitErrorCode(validate(postValidator, post), NOT_AMOUNT_ERROR_CODE);
  }

  @Test
  public void postRejectsConcentrationDefaultUnit() {
    ApiSampleTemplatePost post = newValidPost();
    post.setDefaultUnitId(RSUnitDef.MGMS_PER_ML.getId());
    assertDefaultUnitErrorCode(validate(postValidator, post), NOT_AMOUNT_ERROR_CODE);
  }

  @Test
  public void postAcceptsMassDefaultUnit() {
    ApiSampleTemplatePost post = newValidPost();
    post.setDefaultUnitId(RSUnitDef.GRAM.getId());
    assertNoDefaultUnitError(validate(postValidator, post));
  }

  @Test
  public void postAcceptsVolumeDefaultUnit() {
    ApiSampleTemplatePost post = newValidPost();
    post.setDefaultUnitId(RSUnitDef.MILLI_LITRE.getId());
    assertNoDefaultUnitError(validate(postValidator, post));
  }

  @Test
  public void postAcceptsDimensionlessDefaultUnit() {
    ApiSampleTemplatePost post = newValidPost();
    post.setDefaultUnitId(RSUnitDef.DIMENSIONLESS.getId());
    assertNoDefaultUnitError(validate(postValidator, post));
  }

  @Test
  public void postRejectsNonExistentUnitWithExistingErrorCode() {
    // Regression guard: existing existence-check branch must keep firing for unknown ids.
    ApiSampleTemplatePost post = newValidPost();
    post.setDefaultUnitId(9999);
    assertDefaultUnitErrorCode(validate(postValidator, post), INVALID_UNIT_ERROR_CODE);
  }

  @Test
  public void postNullDefaultUnitProducesNoDefaultUnitError() {
    ApiSampleTemplatePost post = newValidPost();
    post.setDefaultUnitId(null);
    assertNoDefaultUnitError(validate(postValidator, post));
  }

  // --- PUT: same rules, asserted against ApiSampleTemplate update payloads --------------

  @Test
  public void putRejectsMolarityDefaultUnit() {
    ApiSampleTemplate put = newValidPut();
    put.setDefaultUnitId(RSUnitDef.NANOMOLAR.getId());
    assertDefaultUnitErrorCode(validate(putValidator, put), NOT_AMOUNT_ERROR_CODE);
  }

  @Test
  public void putRejectsTemperatureDefaultUnit() {
    ApiSampleTemplate put = newValidPut();
    put.setDefaultUnitId(RSUnitDef.CELSIUS.getId());
    assertDefaultUnitErrorCode(validate(putValidator, put), NOT_AMOUNT_ERROR_CODE);
  }

  @Test
  public void putRejectsConcentrationDefaultUnit() {
    ApiSampleTemplate put = newValidPut();
    put.setDefaultUnitId(RSUnitDef.MGMS_PER_ML.getId());
    assertDefaultUnitErrorCode(validate(putValidator, put), NOT_AMOUNT_ERROR_CODE);
  }

  @Test
  public void putAcceptsMassDefaultUnit() {
    ApiSampleTemplate put = newValidPut();
    put.setDefaultUnitId(RSUnitDef.GRAM.getId());
    assertNoDefaultUnitError(validate(putValidator, put));
  }

  @Test
  public void putAcceptsVolumeDefaultUnit() {
    ApiSampleTemplate put = newValidPut();
    put.setDefaultUnitId(RSUnitDef.MILLI_LITRE.getId());
    assertNoDefaultUnitError(validate(putValidator, put));
  }

  @Test
  public void putAcceptsDimensionlessDefaultUnit() {
    ApiSampleTemplate put = newValidPut();
    put.setDefaultUnitId(RSUnitDef.DIMENSIONLESS.getId());
    assertNoDefaultUnitError(validate(putValidator, put));
  }

  @Test
  public void putRejectsNonExistentUnitWithExistingErrorCode() {
    ApiSampleTemplate put = newValidPut();
    put.setDefaultUnitId(9999);
    assertDefaultUnitErrorCode(validate(putValidator, put), INVALID_UNIT_ERROR_CODE);
  }

  @Test
  public void putNullDefaultUnitProducesNoDefaultUnitError() {
    ApiSampleTemplate put = newValidPut();
    put.setDefaultUnitId(null);
    assertNoDefaultUnitError(validate(putValidator, put));
  }

  // --- helpers --------------------------------------------------------------------------

  private ApiSampleTemplatePost newValidPost() {
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("templateForUnitValidation");
    return post;
  }

  private ApiSampleTemplate newValidPut() {
    ApiSampleTemplate put = new ApiSampleTemplate();
    put.setName("templateForUnitValidation");
    return put;
  }

  private Errors validate(Validator validator, Object target) {
    Errors errors = new BeanPropertyBindingResult(target, "template");
    validator.validate(target, errors);
    return errors;
  }

  private void assertDefaultUnitErrorCode(Errors errors, String expectedCode) {
    assertNotNull(
        errors.getFieldError("defaultUnitId"),
        "expected a defaultUnitId error but found none. All errors: " + errors.getAllErrors());
    assertEquals(expectedCode, errors.getFieldError("defaultUnitId").getCode());
  }

  private void assertNoDefaultUnitError(Errors errors) {
    assertNull(
        errors.getFieldError("defaultUnitId"),
        "unexpected defaultUnitId error: " + errors.getFieldError("defaultUnitId"));
  }
}
