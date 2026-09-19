package com.researchspace.api.v1.controller;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.inventory.InventoryFieldNameUniquenessValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

@ExtendWith(MockitoExtension.class)
public class SubsampleApiPutValidatorTest extends InventoryRecordValidationTestBase {

  @Autowired private SubSampleApiPutValidator subSampleValidator;

  @BeforeEach
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

  @Test
  public void rejectsDuplicateExtraFieldNames() {
    ApiSubSample apiSubsample = new ApiSubSample();
    apiSubsample.setName("s1");
    ApiExtraField ef1 = new ApiExtraField();
    ef1.setName("Notes");
    ApiExtraField ef2 = new ApiExtraField();
    ef2.setName("notes"); // case-insensitive duplicate
    apiSubsample.getExtraFields().add(ef1);
    apiSubsample.getExtraFields().add(ef2);

    Errors e = new BeanPropertyBindingResult(apiSubsample, "samplePut");
    validator.validate(apiSubsample, e);
    assertEquals(1, e.getErrorCount());
    assertEquals("extraFields[1].name", e.getFieldError().getField());
    assertEquals(
        InventoryFieldNameUniquenessValidator.DUPLICATE_NAME_ERROR_CODE,
        e.getFieldError().getCode());
  }
}
