package com.researchspace.api.v1.config;

import com.researchspace.api.v1.controller.ApiAccountInitialiser;
import com.researchspace.api.v1.throttling.APIFileUploadThrottler;
import com.researchspace.api.v1.throttling.APIRequestThrottler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Configuration of API-specific spring beans in non-production environments */
@Configuration()
@Profile({"run", "dev"})
public class DevTestAPIConfig extends BaseAPIConfig {
  // e
  @Bean
  @Profile("!test-block-throttling")
  APIRequestThrottler userThrottler() {
    return APIRequestThrottler.PASS_THRU;
  }

  @Bean
  @Profile("!test-block-throttling")
  APIRequestThrottler globalThrottler() {
    return APIRequestThrottler.PASS_THRU;
  }

  @Bean
  @Profile("!test-block-throttling")
  APIRequestThrottler inventoryThrottler() {
    return APIRequestThrottler.PASS_THRU;
  }

  @Bean
  @Profile("!test-block-throttling")
  APIFileUploadThrottler fileUploadThrottler() {
    return APIFileUploadThrottler.PASS_THRU;
  }

  @Bean
  ApiAccountInitialiser AccountInitialiser() {
    return new AccountInitialiserImpl();
  }
}
