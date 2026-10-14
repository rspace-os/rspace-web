package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.InventoryApiSearchConfig;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.RadioFieldForm;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

class JsonValidationRoutingTest {
  @Test
  void defaultFactoriesResolveApplicationConstraintsFromJson() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      DateFieldForm date = new DateFieldForm();
      date.setFormat("");
      ChoiceFieldForm choice = new ChoiceFieldForm();
      choice.setChoiceOptions("");
      RadioFieldForm radio = new RadioFieldForm();
      radio.setRadioOption("");
      ScheduledMaintenance maintenance = new ScheduledMaintenance(new Date(0), new Date(1));
      maintenance.setMessage("x".repeat(256));
      InventoryApiSearchConfig search = new InventoryApiSearchConfig();
      search.setResultType("invalid");

      assertEquals(
          "Start date is required.",
          validator
              .validateValue(ScheduledMaintenance.class, "startDate", null)
              .iterator()
              .next()
              .getMessage());
      assertEquals(
          "End date is required.",
          validator
              .validateValue(ScheduledMaintenance.class, "endDate", null)
              .iterator()
              .next()
              .getMessage());
      assertMessage(validator, date, "format", "Date format is required.");
      assertMessage(validator, choice, "choiceOptions", "Choice options are required.");
      assertMessage(validator, radio, "radioOption", "Radio options are required.");
      assertMessage(
          validator, maintenance, "message", "Message must be no longer than 255 characters.");
      assertMessage(
          validator,
          search,
          "resultType",
          "Requested result type must be one of: SAMPLE, SUBSAMPLE, CONTAINER, SAMPLE_TEMPLATE,"
              + " INSTRUMENT or INSTRUMENT_TEMPLATE");
    }
  }

  private static class Request {
    @NotNull private String required;

    @Size(max = 3, message = "{validation.errors.descriptionStringMax}")
    private String description = "abcdef";
  }

  @Test
  void defaultFactoryPreservesInterpolationAndLocaleAwareBuiltInMessages() {
    var previous = LocaleContextHolder.getLocaleContext();
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      LocaleContextHolder.setLocale(Locale.GERMAN);
      var violations = factory.getValidator().validate(new Request());
      assertEquals(
          Set.of("darf nicht null sein", "Description \"abcdef\" must be less than 3 characters."),
          violations.stream().map(violation -> violation.getMessage()).collect(Collectors.toSet()));
      var required =
          violations.stream()
              .filter(violation -> violation.getPropertyPath().toString().equals("required"))
              .findFirst()
              .orElseThrow();
      MessageInterpolator.Context context = mock(MessageInterpolator.Context.class);
      when(context.getConstraintDescriptor())
          .thenAnswer(invocation -> required.getConstraintDescriptor());
      assertEquals(
          "must not be null",
          factory
              .getMessageInterpolator()
              .interpolate(required.getMessageTemplate(), context, Locale.ENGLISH));
    } finally {
      LocaleContextHolder.setLocaleContext(previous);
    }
  }

  private static void assertMessage(
      Validator validator, Object target, String property, String expected) {
    assertEquals(
        Set.of(expected),
        validator.validateProperty(target, property).stream()
            .map(violation -> violation.getMessage())
            .collect(Collectors.toSet()));
  }
}
