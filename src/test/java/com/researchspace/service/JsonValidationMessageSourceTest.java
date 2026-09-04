package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.User;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.RadioFieldForm;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class JsonValidationMessageSourceTest {

  private static class Request {
    @Size(max = 3, message = "{validation.errors.descriptionStringMax}")
    private final String value = "abcdef";

    @NotNull private final String required = null;

    @NotNull(message = "{workspace:export.repositories.common.title}")
    private final String frontendCatalogueValue = null;

    @NotNull(message = "{validation.errors.exportFormatRequired}")
    private final String semanticMessage = null;

    @Pattern(regexp = "xml|html", message = "{validation.errors.exportFormatInvalid}")
    private final String invalidFormat = "pdf";
  }

  @Test
  void resolvesAndInterpolatesBeanValidationMessageFromJson() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    JsonMessageSource messageSource = new JsonMessageSource();
    validator.setValidationMessageSource(messageSource);
    validator.afterPropertiesSet();

    Set<ConstraintViolation<Request>> violations = validator.validate(new Request());

    assertEquals(5, violations.size());
    assertEquals(
        Set.of(
            "Description \"abcdef\" must be less than 3 characters.",
            "Format must be either xml or html.",
            "Format is a required field.",
            "must not be null",
            "Title"),
        violations.stream()
            .map(ConstraintViolation::getMessage)
            .collect(java.util.stream.Collectors.toSet()));
    validator.close();
  }

  @Test
  void resolvesFieldFormMessagesFromJson() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(new JsonMessageSource());
    validator.afterPropertiesSet();

    assertEquals(
        "Choice options are required.",
        validator.validate(new ChoiceFieldForm()).iterator().next().getMessage());

    DateFieldForm date = new DateFieldForm();
    date.setFormat("");
    assertEquals(
        "Date format is required.", validator.validate(date).iterator().next().getMessage());

    assertEquals(
        "Radio options are required.",
        validator.validate(new RadioFieldForm()).iterator().next().getMessage());
    validator.close();
  }

  @Test
  void resolvesScheduledMaintenanceMessageFromJson() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(new JsonMessageSource());
    validator.afterPropertiesSet();
    String message = "x".repeat(User.DEFAULT_MAXFIELD_LEN + 1);
    ScheduledMaintenance maintenance = new ScheduledMaintenance(null, null);
    maintenance.setMessage(message);

    ConstraintViolation<ScheduledMaintenance> violation =
        validator.validateProperty(maintenance, "message").iterator().next();

    assertEquals(
        "Message must be no more than " + User.DEFAULT_MAXFIELD_LEN + " characters.",
        violation.getMessage());
    validator.close();
  }
}
