package com.researchspace.api.v1.config;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.api.v1.auth.ApiAuthenticator;
import com.researchspace.api.v1.auth.ApiKeyAuthenticator;
import com.researchspace.api.v1.auth.MainRSpaceApiAuthenticator;
import com.researchspace.api.v1.auth.OAuthTokenAuthenticator;
import com.researchspace.api.v1.controller.APIFileUploadThrottlingInterceptor;
import com.researchspace.api.v1.controller.APIRequestThrottlingInterceptor;
import com.researchspace.api.v1.controller.FilesAPIHandler;
import com.researchspace.api.v1.service.ExportApiHandler;
import com.researchspace.api.v1.service.RSFormApiHandler;
import com.researchspace.api.v1.service.impl.ExportApiSpringBatchHandlerImpl;
import com.researchspace.api.v1.service.impl.RSFormApiHandlerImpl;
import com.researchspace.api.v1.throttling.APIFileUploadThrottler;
import com.researchspace.api.v1.throttling.APIRequestThrottler;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.UserApiKeyManager;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Common API bean configuration for all profiles */
@Configuration
@EnableBatchProcessing
public class BaseAPIConfig {

  @Bean
  APIRequestThrottlingInterceptor apiThrottlingInterceptor(
      APIRequestThrottler userThrottler,
      APIRequestThrottler globalThrottler,
      APIRequestThrottler inventoryThrottler) {
    return new APIRequestThrottlingInterceptor(userThrottler, globalThrottler, inventoryThrottler);
  }

  @Bean
  APIFileUploadThrottlingInterceptor apiFileUploadThrottlingInterceptor(
      APIFileUploadThrottler fileUploadThrottler) {
    return new APIFileUploadThrottlingInterceptor(fileUploadThrottler);
  }

  @Autowired @Lazy UserApiKeyManager apiKeyMgr;

  // implementation can be changed in future or altered depending on environment
  @Bean
  ApiAuthenticator apiAuthenticator(
      OAuthTokenAuthenticator oAuthTokenAuthenticator,
      AnalyticsManager analyticsManager,
      ApiAvailabilityHandler apiAvailabilityHandler) {

    return new MainRSpaceApiAuthenticator(
        new ApiKeyAuthenticator(apiKeyMgr), oAuthTokenAuthenticator,
        analyticsManager, apiAvailabilityHandler);
  }

  @Bean
  public FilesAPIHandler filesAPIHandler() {
    return new FilesAPIHandler();
  }

  @Bean
  public ExportApiHandler exportApiHandler() {
    return new ExportApiSpringBatchHandlerImpl();
  }

  @Bean
  RSFormApiHandler formApiHandler() {
    return new RSFormApiHandlerImpl();
  }
}
