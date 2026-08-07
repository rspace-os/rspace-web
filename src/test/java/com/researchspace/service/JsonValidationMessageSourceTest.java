package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v2.model.ApiV2CollectionQuery;
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
  void resolvesApiV2ConstraintMessagesFromJson() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(new JsonMessageSource());
    validator.afterPropertiesSet();

    ApiV2CollectionQuery query = new ApiV2CollectionQuery();
    query.setLimit(ApiV2CollectionQuery.MAX_LIMIT + 1);
    query.setDepth(ApiV2CollectionQuery.MAX_DEPTH + 1);

    assertEquals(
        Set.of("Limit must not exceed 100.", "Depth must not exceed 10."),
        validator.validate(query).stream()
            .map(ConstraintViolation::getMessage)
            .collect(java.util.stream.Collectors.toSet()));

    query.setLimit(0);
    query.setDepth(-1);
    assertEquals(
        Set.of("Limit must be 1 or greater.", "Depth must be 0 or greater."),
        validator.validate(query).stream()
            .map(ConstraintViolation::getMessage)
            .collect(java.util.stream.Collectors.toSet()));
    validator.close();
  }
}
