package com.researchspace.webapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.controller.*;
import com.researchspace.api.v2.auth.ApiV2Authenticator;
import com.researchspace.api.v2.controller.ApiV2AuthenticationInterceptor;
import com.researchspace.api.v2.controller.ApiV2PermissiveCorsInterceptor;
import com.researchspace.auth.TimezoneAdjuster;
import com.researchspace.auth.TimezoneAdjusterImpl;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.webapp.controller.*;
import com.researchspace.webapp.filter.OriginRefererCheckingInterceptor;
import com.researchspace.webapp.integrations.wopi.WopiProofKeyValidationInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Controller-layer beans that nevertheless can be created in a normal (non-web)
 * applicationContext
 */
@Configuration
public class WebDefaultConfig {

  @Autowired PropertyHolder properties;

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  public IControllerExceptionHandler controllerExceptionHandler() {
    return new ControllerExceptionHandler();
  }

  @Bean
  SuperCSVMessageConverter csvConverter() {
    return new SuperCSVMessageConverter();
  }

  @Bean
  CSVApiErrorMessageConverter csvApiErrorConverter() {
    return new CSVApiErrorMessageConverter();
  }

  @Bean
  ApiAuthenticationInterceptor apiAuthenticationInterceptor() {
    return new ApiAuthenticationInterceptor();
  }

  @Bean
  ApiV2AuthenticationInterceptor apiV2AuthenticationInterceptor(
      ApiV2Authenticator apiV2Authenticator) {
    return new ApiV2AuthenticationInterceptor(apiV2Authenticator);
  }

  @Bean
  PerformanceLoggingInterceptor performanceLoggingInterceptor() {
    return new PerformanceLoggingInterceptor(properties.getSlowLogThreshold());
  }

  @Bean
  ApiPermissiveCorsInterceptor apiPermissiveCorsInterceptor() {
    return new ApiPermissiveCorsInterceptor();
  }

  @Bean
  ApiV2PermissiveCorsInterceptor apiV2PermissiveCorsInterceptor() {
    return new ApiV2PermissiveCorsInterceptor();
  }

  @Bean
  OriginRefererCheckingInterceptor originRefererCheckingInterceptor() {
    return new OriginRefererCheckingInterceptor();
  }

  @Bean
  ProductAndPropertyAnnotationInterceptor productAndPropertyAnnotationInterceptor() {
    return new ProductAndPropertyAnnotationInterceptor();
  }

  @Bean
  LoggingInterceptor loggingInterceptor() {
    return new LoggingInterceptor();
  }

  @Bean
  BrowserCacheAdviceInterceptor browserCacheAdviceInterceptor() {
    return new BrowserCacheAdviceInterceptor();
  }

  @Bean
  TimezoneInterceptor timezoneInterceptor() {
    return new TimezoneInterceptor();
  }

  @Bean
  TimezoneAdjuster TimezoneAdjuster() {
    return new TimezoneAdjusterImpl();
  }

  @Bean
  WopiProofKeyValidationInterceptor wopiProofKeyValidationInterceptor() {
    return new WopiProofKeyValidationInterceptor();
  }
}
