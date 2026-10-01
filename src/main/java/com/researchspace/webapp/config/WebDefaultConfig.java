package com.researchspace.webapp.config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.controller.*;
import com.researchspace.api.v2.auth.ApiV2Authenticator;
import com.researchspace.api.v2.auth.ApiV2BrowserSessionAuthenticator;
import com.researchspace.api.v2.controller.ApiV2AuthenticationInterceptor;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
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

  static final int MAX_JSON_DOCUMENT_LENGTH = 10 * 1024 * 1024;
  static final int MAX_JSON_STRING_LENGTH = 2 * 1024 * 1024;
  static final int MAX_JSON_TOKEN_COUNT = 100_000;
  static final int MAX_JSON_NESTING_DEPTH = 100;

  @Autowired PropertyHolder properties;

  @Bean
  ObjectMapper objectMapper() {
    StreamReadConstraints constraints =
        StreamReadConstraints.builder()
            .maxDocumentLength(MAX_JSON_DOCUMENT_LENGTH)
            .maxStringLength(MAX_JSON_STRING_LENGTH)
            .maxTokenCount(MAX_JSON_TOKEN_COUNT)
            .maxNestingDepth(MAX_JSON_NESTING_DEPTH)
            .build();
    return new ObjectMapper(JsonFactory.builder().streamReadConstraints(constraints).build());
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
      ApiV2Authenticator apiV2Authenticator,
      ApiV2BrowserSessionAuthenticator browserSessionAuthenticator,
      ApiV2EndpointCatalog endpoints) {
    return new ApiV2AuthenticationInterceptor(
        apiV2Authenticator, browserSessionAuthenticator, endpoints);
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
