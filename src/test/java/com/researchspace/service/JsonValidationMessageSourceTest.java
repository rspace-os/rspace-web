package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class JsonValidationMessageSourceTest {

  private static class Request {
    @Size(max = 3, message = "{validation.errors.stringMax}")
    private final String value = "abcdef";

    @NotNull private final String required = null;

    @NotNull(message = "{workspace:export.repositories.common.title}")
    private final String frontendCatalogueValue = null;
  }

  @Test
  void resolvesAndInterpolatesBeanValidationMessageFromJson() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    JsonMessageSource messageSource = new JsonMessageSource();
    validator.setValidationMessageSource(messageSource);
    validator.afterPropertiesSet();

    Set<ConstraintViolation<Request>> violations = validator.validate(new Request());

    assertEquals(3, violations.size());
    assertEquals(
        Set.of(
            "\"abcdef\" must be less than 3 characters but was length 6.",
            "may not be null",
            "Title"),
        violations.stream()
            .map(ConstraintViolation::getMessage)
            .collect(java.util.stream.Collectors.toSet()));
    validator.close();
  }
}
