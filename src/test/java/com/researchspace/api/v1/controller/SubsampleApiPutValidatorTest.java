package com.researchspace.api.v1.controller;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.record.BaseRecord;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class SubsampleApiPutValidatorTest extends InventoryRecordValidationTestBase {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @Autowired private SubSampleApiPutValidator subSampleValidator;

  @Before
  public void setup() {
    validator = subSampleValidator;
  }

  @Test
  public void emptyNameOK() {
    ApiSubSample apiSubsample = new ApiSubSample();
    apiSubsample.setName(null);
    Errors e = new BeanPropertyBindingResult(apiSubsample, "samplePut");
    validator.validate(apiSubsample, e);
    assertEquals(0, e.getErrorCount());

    apiSubsample.setName(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    e = resetErrorsAndValidate(apiSubsample);
    assertEquals(1, e.getErrorCount());
    assertMaxLengthMsg(e);
  }

  @Test
  public void validateSubsampleDesc() {
    ApiSubSample apiSubsample = new ApiSubSample();
    apiSubsample.setName("s1");
    apiSubsample.setDescription(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    assertDescriptionValidation(apiSubsample);
  }

  @Test
  public void validateSubsampleTags() {
    ApiSubSample apiSubsample = new ApiSubSample();
    apiSubsample.setName("s1");
    apiSubsample.setApiTagInfo(randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    assertTagsTooLongValidation(apiSubsample);
  }

  @Test
  public void validateSubsampleQuantity() {
    ApiSubSample apiSubsample = new ApiSubSample();
    apiSubsample.setName("s1");
    apiSubsample.setQuantity(createInvalidQuantity());
    Errors e = new BeanPropertyBindingResult(apiSubsample, "samplePut");
    validator.validate(apiSubsample, e);
    assertEquals(1, e.getErrorCount());
    assertEquals("quantity", e.getFieldError().getField());
  }
}
