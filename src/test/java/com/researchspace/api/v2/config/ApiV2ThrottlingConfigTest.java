package com.researchspace.api.v2.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.throttling.APIRequestThrottler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApiV2ThrottlingConfigTest {

  @Test
  void stockConfigurationDoesNotMisconfigureGlobalVersusUserMinInterval() {
    ApiV2ThrottlingConfig config = new ApiV2ThrottlingConfig();
    ReflectionTestUtils.setField(config, "enabled", true);
    ReflectionTestUtils.setField(config, "userLimitDay", 5000);
    ReflectionTestUtils.setField(config, "userLimitHour", 1000);
    ReflectionTestUtils.setField(config, "userLimit15Seconds", 15);
    ReflectionTestUtils.setField(config, "globalLimit15Seconds", 75);

    APIRequestThrottler userThrottler = config.apiV2UserThrottler();
    APIRequestThrottler globalThrottler = config.apiV2GlobalThrottler();

    assertTrue(
        globalThrottler.getMinIntervalMillis() <= userThrottler.getMinIntervalMillis(),
        "Global minimum interval must not exceed the per-user minimum interval by default");
  }
}
