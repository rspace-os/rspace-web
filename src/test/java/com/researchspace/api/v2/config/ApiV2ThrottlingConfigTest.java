package com.researchspace.api.v2.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.throttling.APIRequestThrottler;
import org.junit.jupiter.api.Test;

class ApiV2ThrottlingConfigTest {

  @Test
  void stockConfigurationDoesNotMisconfigureGlobalVersusUserMinInterval() {
    ApiV2ThrottlingConfig config = new ApiV2ThrottlingConfig(true, 5000, 1000, 15, 75, 0);

    APIRequestThrottler userThrottler = config.apiV2UserThrottler();
    APIRequestThrottler globalThrottler = config.apiV2GlobalThrottler();

    assertTrue(
        globalThrottler.getMinIntervalMillis() <= userThrottler.getMinIntervalMillis(),
        "Global minimum interval must not exceed the per-user minimum interval by default");
  }
}
