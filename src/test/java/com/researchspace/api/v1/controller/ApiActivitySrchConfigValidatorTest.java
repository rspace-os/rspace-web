package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;

class ApiActivitySrchConfigValidatorTest {

  private final ApiActivitySrchConfigValidator validator = new ApiActivitySrchConfigValidator();

  @Test
  void acceptsGlobalAndBookingResourceIds() {
    for (String identifier :
        List.of(
            "SD123", "SD123v4", "bookings:41", "booking-configurations:42", "booking-settings:1")) {
      ApiActivitySrchConfig config = new ApiActivitySrchConfig();
      config.setOid(identifier);

      var errors = new BeanPropertyBindingResult(config, "config");
      validator.validate(config, errors);

      assertFalse(errors.hasFieldErrors("oid"), identifier);
      assertFalse(hasOidBeanValidationError(config), identifier);
    }
  }

  @Test
  void rejectsMalformedBookingAndArbitraryPrefixedIds() {
    for (String identifier : List.of("bookings:41v1", "booking-settings:x", "other:41")) {
      ApiActivitySrchConfig config = new ApiActivitySrchConfig();
      config.setOid(identifier);

      var errors = new BeanPropertyBindingResult(config, "config");
      validator.validate(config, errors);

      assertTrue(errors.hasFieldErrors("oid"), identifier);
      assertTrue(hasOidBeanValidationError(config), identifier);
    }
  }

  private static boolean hasOidBeanValidationError(ApiActivitySrchConfig config) {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      return factory.getValidator().validate(config).stream()
          .anyMatch(violation -> violation.getPropertyPath().toString().equals("oid"));
    }
  }
}
