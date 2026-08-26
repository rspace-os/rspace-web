package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.model.units.ValidTemperature;
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

    @ValidTemperature
    private final QuantityInfo invalidTemperature = QuantityInfo.of(-274, RSUnitDef.CELSIUS);
  }

  @Test
  void resolvesAndInterpolatesBeanValidationMessageFromJson() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    JsonMessageSource messageSource = new JsonMessageSource();
    validator.setValidationMessageSource(messageSource);
    validator.afterPropertiesSet();

    Set<ConstraintViolation<Request>> violations = validator.validate(new Request());

    assertEquals(6, violations.size());
    assertEquals(
        Set.of(
            "Description \"abcdef\" must be less than 3 characters.",
            "Format must be either xml or html.",
            "Format is a required field.",
            "Invalid temperature - must be a temperature measurement greater than absolute zero",
            "must not be null",
            "Title"),
        violations.stream()
            .map(ConstraintViolation::getMessage)
            .collect(java.util.stream.Collectors.toSet()));
    validator.close();
  }
}
