package com.researchspace.api.v1.controller;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class SampleApiPutValidatorTest extends InventoryRecordValidationTestBase {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @Autowired private SampleApiPutValidator samplePutValidator;

  @Before
  public void setup() {
    validator = samplePutValidator;
  }

  @Test
  public void emptyNameOK() {
    ApiSampleWithFullSubSamples apiSample = new ApiSampleWithFullSubSamples();
    apiSample.setName(" ");
    Errors e = new BeanPropertyBindingResult(apiSample, "samplePut");
    validator.validate(apiSample, e);
    assertEquals(0, e.getErrorCount());

    apiSample.setName(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    e = resetErrorsAndValidate(apiSample);
    assertEquals(1, e.getErrorCount());
    assertMaxLengthMsg(e);
  }

  @Test
  public void validateSampleDesc() {
    ApiSampleWithFullSubSamples apiSample = new ApiSampleWithFullSubSamples();
    apiSample.setName("s1");
    apiSample.setDescription(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    assertDescriptionValidation(apiSample);
  }

  @Test
  public void validateSampleTags() {
    ApiSampleWithFullSubSamples apiSample = new ApiSampleWithFullSubSamples();
    apiSample.setName("s1");
    apiSample.setApiTagInfo(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    assertTagsTooLongValidation(apiSample);

    apiSample.setApiTagInfo("a"); // too short
    Errors e = new BeanPropertyBindingResult(apiSample, "samplePut");
    validator.validate(apiSample, e);
    assertMinLengthMsg(e);

    apiSample.setApiTagInfo("<script>"); // contains invalid chars
    e = new BeanPropertyBindingResult(apiSample, "samplePut");
    validator.validate(apiSample, e);
    assertEquals("errors.invalidchars", e.getGlobalError().getCode());
  }

  @Test
  public void validateSampleQuantity() {
    ApiSampleWithFullSubSamples apiSample = new ApiSampleWithFullSubSamples();
    apiSample.setName("s1");
    apiSample.setQuantity(createInvalidQuantity());
    Errors e = new BeanPropertyBindingResult(apiSample, "samplePut");
    validator.validate(apiSample, e);
    assertEquals(1, e.getErrorCount());
    assertEquals("quantity", e.getFieldError().getField());
  }

  @Test
  public void validateStorageTemperatureValidUnits() {
    ApiSampleWithFullSubSamples apiSample = new ApiSampleWithFullSubSamples();
    apiSample.setName("s1");
    apiSample.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(5L), RSUnitDef.LITRE));
    Errors e = new BeanPropertyBindingResult(apiSample, "samplePut");
    validator.validate(apiSample, e);
    assertEquals(1, e.getErrorCount());
    assertEquals("storageTempMin", e.getFieldError().getField());

    apiSample.setStorageTempMin(null);
    apiSample.setStorageTempMax(new ApiQuantityInfo(BigDecimal.valueOf(5L), RSUnitDef.LITRE));
    e = resetErrorsAndValidate(apiSample);
    assertEquals(1, e.getErrorCount());
    assertEquals("storageTempMax", e.getFieldError().getField());
  }

  @Test
  public void validateStorageTemperatureMinMaxRelations() {
    ApiSampleWithFullSubSamples apiSample = new ApiSampleWithFullSubSamples();
    apiSample.setName("s1");
    apiSample.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(5L), RSUnitDef.CELSIUS));
    apiSample.setStorageTempMax(new ApiQuantityInfo(BigDecimal.valueOf(10L), RSUnitDef.CELSIUS));
    Errors e = new BeanPropertyBindingResult(apiSample, "samplePut");
    validator.validate(apiSample, e);
    assertEquals(0, e.getErrorCount());

    // min > max, should be rejected
    apiSample.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(20L), RSUnitDef.CELSIUS));
    e = resetErrorsAndValidate(apiSample);
    assertEquals(1, e.getErrorCount());

    // set invalid unit, should be rejected
    apiSample.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(5L), RSUnitDef.LITRE));
    e = resetErrorsAndValidate(apiSample);
    assertEquals(2, e.getErrorCount());

    // set min temperature in different unit (but still 5 degrees C), should pass
    apiSample.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(278L), RSUnitDef.KELVIN));
    e = resetErrorsAndValidate(apiSample);
    assertEquals(0, e.getErrorCount());

    // min == max, should be OK
    apiSample.setStorageTempMax(new ApiQuantityInfo(BigDecimal.valueOf(3L), RSUnitDef.CELSIUS));
    apiSample.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(3L), RSUnitDef.CELSIUS));
    e = resetErrorsAndValidate(apiSample);
    assertEquals(0, e.getErrorCount());
  }

  private Errors resetErrorsAndValidate(ApiSampleWithFullSubSamples apiSample) {
    Errors e;
    e = new BeanPropertyBindingResult(apiSample, "samplePut");
    validator.validate(apiSample, e);
    return e;
  }
}
