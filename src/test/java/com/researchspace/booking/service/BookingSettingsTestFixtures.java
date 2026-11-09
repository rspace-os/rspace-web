package com.researchspace.booking.service;

import com.researchspace.service.JsonMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

final class BookingSettingsTestFixtures {

  private BookingSettingsTestFixtures() {}

  static LocalValidatorFactoryBean validator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(new JsonMessageSource());
    validator.afterPropertiesSet();
    return validator;
  }
}
