package com.researchspace.service.inventory;

import static org.junit.Assert.assertEquals;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.model.record.RecordFactory;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class ApiExtraFieldsHelperValidatorTest {

  ApiExtraFieldsHelper helper = new ApiExtraFieldsHelper(new RecordFactory());

  @Test
  public void validateApiExtraField() {
    ApiExtraField toValidate = new ApiExtraField();
    Errors e = new BeanPropertyBindingResult(toValidate, "ef");
    helper.validate(toValidate, e);

    assertEquals(1, e.getErrorCount());
    assertEquals("errors.required", e.getFieldError().getCode());

    // invalid type
    toValidate.setName("f1");
    toValidate.setContent("this is not a number");
    toValidate.setType(ExtraFieldTypeEnum.NUMBER);

    e = new BeanPropertyBindingResult(toValidate, "ef");
    helper.validate(toValidate, e);
    assertEquals(1, e.getErrorCount());
    assertEquals(
        "'this is not a number' cannot be parsed into number",
        e.getFieldError().getDefaultMessage());
    assertEquals("content", e.getFieldError().getField());
  }
}
