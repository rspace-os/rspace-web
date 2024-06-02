package com.researchspace.api.v1.controller;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class SampleApiPostValidatorTest extends InventoryRecordValidationTestBase {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @Autowired
  @Qualifier("sampleApiPostValidator")
  private SampleApiPostValidator samplePostValidator;

  @Before
  public void setup() {
    validator = samplePostValidator;
  }

  @Test
  public void validateSampleName() {
    ApiSampleWithFullSubSamples full = new ApiSampleWithFullSubSamples();
    full.setName(" ");
    Errors e = new BeanPropertyBindingResult(full, "fullpost");
    validator.validate(full, e);
    assertEquals(1, e.getErrorCount());
    assertFieldNameIs(e, "name");
    assertEquals("errors.required", e.getFieldError().getCode());

    full.setName(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    e = resetErrorsAndValidate(full);
    assertEquals(1, e.getErrorCount());
    assertMaxLengthMsg(e);
  }

  @Test
  public void validateExpiryDate() {
    ApiSampleWithFullSubSamples full = new ApiSampleWithFullSubSamples();
    full.setName("ok");

    // past value is ok
    full.setExpiryDate(LocalDate.of(2019, 1, 1)); // a date in the past
    Errors e = new BeanPropertyBindingResult(full, "fullpost");
    validator.validate(full, e);
    assertEquals(0, e.getErrorCount());

    // future value is ok
    full.setExpiryDate(LocalDate.now().plus(1, ChronoUnit.YEARS)); // a date in the future
    e = new BeanPropertyBindingResult(full, "fullpost");
    validator.validate(full, e);
    assertEquals(0, e.getErrorCount());
  }

  @Test
  public void validateSampleDesc() {
    ApiSampleWithFullSubSamples full = new ApiSampleWithFullSubSamples();
    full.setName("s1");
    full.setDescription(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    Errors e = new BeanPropertyBindingResult(full, "fullpost");
    validator.validate(full, e);
    assertEquals(1, e.getErrorCount());
    assertMaxLengthMsg(e);
    assertFieldNameIs(e, "description");
  }

  @Test
  public void validateSampleTags() {
    ApiSampleWithFullSubSamples full = new ApiSampleWithFullSubSamples();
    full.setName("s1");
    full.setApiTagInfo(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    Errors e = new BeanPropertyBindingResult(full, "fullpost");
    validator.validate(full, e);
    assertEquals(1, e.getErrorCount());
    assertMaxLengthMsg(e);
    assertFieldNameIs(e, "tags");
  }

  @Test
  public void validateSampleQuantity() {
    ApiSampleWithFullSubSamples full = new ApiSampleWithFullSubSamples();
    full.setName("s1");
    full.setQuantity(createInvalidQuantity());
    Errors e = new BeanPropertyBindingResult(full, "fullpost");
    validator.validate(full, e);
    assertEquals(1, e.getErrorCount());
    assertEquals("quantity", e.getFieldError().getField());
  }

  @Test
  public void validateStorageTemperatureValidUnits() {
    ApiSampleWithFullSubSamples full = new ApiSampleWithFullSubSamples();
    full.setName("s1");
    full.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(5L), RSUnitDef.LITRE));
    Errors e = new BeanPropertyBindingResult(full, "fullpost");
    validator.validate(full, e);
    assertEquals(1, e.getErrorCount());
    assertEquals("storageTempMin", e.getFieldError().getField());

    full.setStorageTempMin(null);
    full.setStorageTempMax(new ApiQuantityInfo(BigDecimal.valueOf(5L), RSUnitDef.LITRE));
    e = resetErrorsAndValidate(full);
    assertEquals(1, e.getErrorCount());
    assertEquals("storageTempMax", e.getFieldError().getField());
  }

  @Test
  public void validateStorageTemperatureMinMaxRelations() {
    ApiSampleWithFullSubSamples full = new ApiSampleWithFullSubSamples();
    full.setName("s1");
    full.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(5L), RSUnitDef.CELSIUS));
    full.setStorageTempMax(new ApiQuantityInfo(BigDecimal.valueOf(10L), RSUnitDef.CELSIUS));
    Errors e = new BeanPropertyBindingResult(full, "fullpost");
    validator.validate(full, e);
    assertEquals(0, e.getErrorCount());

    // min > max, should be rejected
    full.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(20L), RSUnitDef.CELSIUS));
    e = resetErrorsAndValidate(full);
    assertEquals(1, e.getErrorCount());

    // set invalid unit, should be rejected
    full.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(5L), RSUnitDef.LITRE));
    e = resetErrorsAndValidate(full);
    assertEquals(2, e.getErrorCount());

    // set min temperature in different unit (but still 5 degrees C), should pass
    full.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(278L), RSUnitDef.KELVIN));
    e = resetErrorsAndValidate(full);
    assertEquals(0, e.getErrorCount());

    // min == max, should be OK
    full.setStorageTempMax(new ApiQuantityInfo(BigDecimal.valueOf(3L), RSUnitDef.CELSIUS));
    full.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(3L), RSUnitDef.CELSIUS));
    e = resetErrorsAndValidate(full);
    assertEquals(0, e.getErrorCount());
  }

  private Errors resetErrorsAndValidate(ApiSampleWithFullSubSamples full) {
    Errors e;
    e = new BeanPropertyBindingResult(full, "fullpost");
    validator.validate(full, e);
    return e;
  }

  @Test
  public void validateSubsampleQuantity() {
    ApiSampleWithFullSubSamples full = new ApiSampleWithFullSubSamples();
    full.setName("s1");
    addSubsample(full, createValidQuantity());
    addSubsample(full, createInvalidQuantity());
    addSubsample(full, createInvalidUnit());

    Errors e = new BeanPropertyBindingResult(full, "fullpost");
    validator.validate(full, e);
    assertEquals(2, e.getErrorCount());
    assertEquals("subSamples[1].quantity", e.getFieldError().getField());
    assertEquals("subSamples[2].quantity", e.getFieldErrors().get(1).getField());
  }

  @Test
  public void validateSubsampleExtraFields() {
    ApiSampleWithFullSubSamples full = new ApiSampleWithFullSubSamples();
    full.setName("s1");
    addSubsample(full, createValidQuantity());
    addSubsample(full, createValidQuantity());
    ApiExtraField ef1 = new ApiExtraField();
    ef1.setName("  ");
    full.getSubSamples().get(1).getExtraFields().add(ef1);

    Errors e = new BeanPropertyBindingResult(full, "fullpost");
    validator.validate(full, e);
    assertEquals(1, e.getErrorCount());
    assertEquals("subSamples[1].extraFields[0].name", e.getFieldError().getField());
  }

  private void addSubsample(ApiSampleWithFullSubSamples full, ApiQuantityInfo quantity) {
    ApiSubSample apiSubSample = new ApiSubSample();
    apiSubSample.setQuantity(quantity);
    full.getSubSamples().add(apiSubSample);
  }
}
