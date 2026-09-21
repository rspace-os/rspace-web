package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class ApiExtraFieldsHelperValidatorTest {

  ApiExtraFieldsHelper helper = new ApiExtraFieldsHelper(new RecordFactory());

  @BeforeEach
  public void setUp() {
    ReflectionTestUtils.setField(
        helper, "messages", new MessageSourceUtils(new JsonMessageSource()));
  }

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
    assertEquals("errors.inventory.field.validation", e.getFieldError().getCode());
    assertEquals(
        "'this is not a number' cannot be parsed into number", e.getFieldError().getArguments()[0]);
    assertEquals("content", e.getFieldError().getField());
  }
}
