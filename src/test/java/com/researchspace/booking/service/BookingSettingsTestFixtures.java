package com.researchspace.booking.service;

import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingDisplaySettings;
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

  static BookingConfigurationDefaults defaults() {
    BookingConfigurationDefaults defaults = new BookingConfigurationDefaults();
    defaults.setId(BookingConfigurationDefaults.SINGLETON_ID);
    BookingDisplaySettings.defaults().applyTo(defaults);
    return defaults;
  }
}
