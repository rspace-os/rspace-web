package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.testutils.SpringTransactionalTest;
import java.math.BigDecimal;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/*
 * utility test harness setup methods for sample validation tests
 */
abstract class InventoryRecordValidationTestBase extends SpringTransactionalTest {

  Validator validator;

  Errors resetErrorsAndValidate(ApiInventoryRecordInfo full) {
    Errors e = new BeanPropertyBindingResult(full, "samplePut");
    validator.validate(full, e);
    return e;
  }

  ApiQuantityInfo createValidQuantity() {
    return new ApiQuantityInfo(BigDecimal.valueOf(3L), RSUnitDef.GRAM.getId());
  }

  ApiQuantityInfo createInvalidQuantity() {
    return new ApiQuantityInfo(BigDecimal.valueOf(-2L), RSUnitDef.GRAM.getId());
  }

  ApiQuantityInfo createInvalidUnit() {
    return new ApiQuantityInfo(BigDecimal.valueOf(3L), -200);
  }

  void assertMaxLengthMsg(Errors e) {
    String code =
        e.getFieldError() != null ? e.getFieldError().getCode() : e.getGlobalError().getCode();
    assertEquals("errors.maxlength", code);
  }

  void assertMinLengthMsg(Errors e) {
    String code =
        e.getFieldError() != null ? e.getFieldError().getCode() : e.getGlobalError().getCode();
    assertEquals("errors.minlength", code);
  }

  void assertFieldNameIs(Errors e, String fieldName) {
    assertEquals(fieldName, e.getFieldError().getField());
  }

  void assertDescriptionValidation(ApiInventoryRecordInfo apiInventoryInfo) {
    Errors e = new BeanPropertyBindingResult(apiInventoryInfo, "samplePut");
    validator.validate(apiInventoryInfo, e);
    assertEquals(1, e.getErrorCount());
    assertMaxLengthMsg(e);
    assertFieldNameIs(e, "description");
  }

  void assertTagsTooLongValidation(ApiInventoryRecordInfo apiInventoryInfo) {
    Errors e = new BeanPropertyBindingResult(apiInventoryInfo, "samplePut");
    validator.validate(apiInventoryInfo, e);
    assertEquals(1, e.getErrorCount());
    assertMaxLengthMsg(e);
    assertFieldNameIs(e, "tags");
  }
}
