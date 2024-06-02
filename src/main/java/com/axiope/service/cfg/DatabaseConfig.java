package com.axiope.service.cfg;

import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;

// ignore for now.. as propertuies aren't resolved
// @Configuration
public class DatabaseConfig {

  // this must be set to resolve value placeholders etc
  @Bean
  static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
    PropertySourcesPlaceholderConfigurer rc = new PropertySourcesPlaceholderConfigurer();
    rc.setIgnoreUnresolvablePlaceholders(true);
    return rc;
  }

  @Bean
  PersistenceExceptionTranslationPostProcessor persistenceExceptionTranslationPostProcessor() {
    return new PersistenceExceptionTranslationPostProcessor();
  }
}
