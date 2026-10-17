package com.researchspace.api.v2.config;

import com.researchspace.api.v2.controller.ApiV2PreAuthenticationThrottlingInterceptor;
import com.researchspace.api.v2.controller.ApiV2RequestThrottlingInterceptor;
import com.researchspace.api.v2.throttling.APIRequestThrottler;
import com.researchspace.api.v2.throttling.Bucket4jApiRequestThrottler;
import com.researchspace.core.util.DefaultTimeSource;
import com.researchspace.core.util.TimeSource;
import com.researchspace.core.util.throttling.ThrottleDefinitionSet;
import com.researchspace.core.util.throttling.ThrottleInterval;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** REST API v2 rate-limit configuration, independent of v1 throttling beans. */
@Configuration
public class ApiV2ThrottlingConfig {

  private final boolean enabled;

  private final int userLimitDay;

  private final int userLimitHour;

  private final int userLimit15Seconds;

  private final int globalLimit15Seconds;

  // Matches the per-user throttler's hardcoded minimum interval (0) so the stock configuration
  // doesn't trip ApiV2RequestThrottlingInterceptor's misconfiguration warning on every boot.
  private final int globalMinInterval;

  public ApiV2ThrottlingConfig(
      @Value("${api.throttling.enabled:false}") boolean enabled,
      @Value("${api.user.limit.day:5000}") int userLimitDay,
      @Value("${api.user.limit.hour:1000}") int userLimitHour,
      @Value("${api.user.limit.15s:15}") int userLimit15Seconds,
      @Value("${api.global.limit.15s:75}") int globalLimit15Seconds,
      @Value("${api.global.minInterval:0}") int globalMinInterval) {
    this.enabled = enabled;
    this.userLimitDay = userLimitDay;
    this.userLimitHour = userLimitHour;
    this.userLimit15Seconds = userLimit15Seconds;
    this.globalLimit15Seconds = globalLimit15Seconds;
    this.globalMinInterval = globalMinInterval;
  }

  @Bean
  ApiV2RequestThrottlingInterceptor apiV2RequestThrottlingInterceptor(
      @Qualifier("apiV2UserThrottler") APIRequestThrottler userThrottler,
      @Qualifier("apiV2GlobalThrottler") APIRequestThrottler globalThrottler) {
    return new ApiV2RequestThrottlingInterceptor(userThrottler, globalThrottler);
  }

  @Bean
  ApiV2PreAuthenticationThrottlingInterceptor apiV2PreAuthenticationThrottlingInterceptor(
      @Qualifier("apiV2PreAuthenticationThrottler") APIRequestThrottler throttler) {
    return new ApiV2PreAuthenticationThrottlingInterceptor(throttler);
  }

  @Bean("apiV2PreAuthenticationThrottler")
  APIRequestThrottler apiV2PreAuthenticationThrottler() {
    if (!enabled) {
      return APIRequestThrottler.PASS_THRU;
    }
    ThrottleDefinitionSet definitions = new ThrottleDefinitionSet("v2 pre-authentication requests");
    definitions.addDefinition(ThrottleInterval.QUARTER_MIN, globalLimit15Seconds);
    return throttler(definitions, 0);
  }

  @Bean("apiV2UserThrottler")
  APIRequestThrottler apiV2UserThrottler() {
    if (!enabled) {
      return APIRequestThrottler.PASS_THRU;
    }
    ThrottleDefinitionSet definitions = new ThrottleDefinitionSet("v2 user requests");
    definitions.addDefinition(ThrottleInterval.QUARTER_MIN, userLimit15Seconds);
    definitions.addDefinition(ThrottleInterval.HOUR, userLimitHour);
    definitions.addDefinition(ThrottleInterval.DAY, userLimitDay);
    return throttler(definitions, 0);
  }

  @Bean("apiV2GlobalThrottler")
  APIRequestThrottler apiV2GlobalThrottler() {
    if (!enabled) {
      return APIRequestThrottler.PASS_THRU;
    }
    ThrottleDefinitionSet definitions = new ThrottleDefinitionSet("v2 global requests");
    definitions.addDefinition(ThrottleInterval.QUARTER_MIN, globalLimit15Seconds);
    return throttler(definitions, globalMinInterval);
  }

  private static APIRequestThrottler throttler(
      ThrottleDefinitionSet definitions, int minimumInterval) {
    TimeSource timeSource = new DefaultTimeSource();
    return new Bucket4jApiRequestThrottler(timeSource, definitions, minimumInterval);
  }
}
