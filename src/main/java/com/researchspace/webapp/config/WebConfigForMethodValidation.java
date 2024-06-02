package com.researchspace.webapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

/**
 * It is necessary to keep the methodValidationPostProcessor Bean out of the other child class of
 * WebMvcConfigurationSupport (WebConfig) else the effect on the order of @config class loading when
 * running Integration tests results in... issues.
 */
@Configuration
public class WebConfigForMethodValidation extends WebMvcConfigurationSupport {

  @Bean
  public MethodValidationPostProcessor methodValidationPostProcessor() {
    return new MethodValidationPostProcessor();
  }
}
