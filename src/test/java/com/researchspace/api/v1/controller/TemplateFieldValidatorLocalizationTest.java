package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiFieldToModelFieldFactory;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.Validator;

class TemplateFieldValidatorLocalizationTest {

  private static Stream<Named<Validator>> validators() {
    return Stream.of(
        Named.of("sample", new SampleTemplateFieldPostValidator()),
        Named.of("instrument", new InstrumentTemplateFieldPostValidator()));
  }

  @ParameterizedTest
  @MethodSource("validators")
  void resolvesModelValidationErrorsBeforeAddingThemToBindingResult(Validator validator) {
    ReflectionTestUtils.setField(
        validator, "apiFieldToModelFieldFactory", new ApiFieldToModelFieldFactory());
    ReflectionTestUtils.setField(
        validator, "messages", new MessageSourceUtils(new JsonMessageSource()));
    ApiInventoryEntityField field = new ApiInventoryEntityField();
    field.setName("Number field");
    field.setType(ApiFieldType.NUMBER);
    field.setContent("not a number");
    BeanPropertyBindingResult errors = new BeanPropertyBindingResult(field, "field");

    validator.validate(field, errors);

    FieldError error = errors.getFieldError("content");
    assertEquals("errors.inventory.template.invalidFieldContent", error.getCode());
    assertEquals(
        "[not a number] is invalid for field type Number: Invalid number: not a number",
        error.getArguments()[0]);
  }
}
