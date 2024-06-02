package com.researchspace.api.v1.config;

import com.researchspace.api.v1.controller.ApiAccountInitialiser;
import com.researchspace.api.v1.throttling.APIFileUploadThrottler;
import com.researchspace.api.v1.throttling.APIRequestThrottler;
import com.researchspace.api.v1.throttling.ApiFileUploadThrottlerImpl;
import com.researchspace.api.v1.throttling.ApiRequestThrottlerImpl;
import com.researchspace.core.util.DefaultTimeSource;
import com.researchspace.core.util.TimeSource;
import com.researchspace.core.util.throttling.AllowanceTrackerSource;
import com.researchspace.core.util.throttling.AllowanceTrackerSourceImpl;
import com.researchspace.core.util.throttling.ThrottleDefinitionSet;
import com.researchspace.core.util.throttling.ThrottleInterval;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Configuration of API-specific spring beans in production environments */
@Configuration
@Profile({"prod", "prod-test", "api-prod-config-test"})
@Slf4j
public class ProdAPIConfig extends BaseAPIConfig {

  // these two fields seems unused, is that correct? RA - no , they can be
  // configured
  static final String API_GLOBAL_LIMIT_DAY = "api.global.limit.day";
  static final String API_GLOBAL_LIMIT_HOUR = "api.global.limit.hour";

  static final String API_GLOBAL_LIMIT_15S = "api.global.limit.15s";
  static final String API_GLOBAL_MIN_INTERVAL = "api.global.minInterval";
  static final String API_USER_LIMIT_DAY = "api.user.limit.day";
  static final String API_USER_LIMIT_HOUR = "api.user.limit.hour";
  static final String API_USER_LIMIT_15S = "api.user.limit.15s";
  static final String API_USER_MIN_INTERVAL = "api.user.minInterval";
  static final String API_BETA_ENABLED = "api.beta.enabled";

  @Value("${api.beta.enabled:false}")
  private Boolean betaApiEnabled = Boolean.FALSE;

  @Value("${" + API_GLOBAL_LIMIT_DAY + ":100000}")
  private Integer apiGlobalLimitDay = 100_000;

  @Value("${" + API_GLOBAL_LIMIT_HOUR + ":10000}")
  private Integer apiGlobalLimitHour = 10_000;

  @Value("${" + API_GLOBAL_LIMIT_15S + ":75}")
  private Integer apiGlobalLimit15s = 75;

  @Value("${" + API_GLOBAL_MIN_INTERVAL + ":1}")
  private Integer apiGlobalMinInterval = 1;

  @Value("${" + API_USER_LIMIT_DAY + ":5000}")
  private Integer apiUserLimitDay = 5000;

  @Value("${" + API_USER_LIMIT_HOUR + ":1000}")
  private Integer apiUserLimitHour = 1000;

  @Value("${" + API_USER_LIMIT_15S + ":15}")
  private Integer apiUserLimit15s = 15;

  //	@Value("${" + API_USER_MIN_INTERVAL + ":50}")
  private Integer apiUserMinInterval = 0;

  void setBetaApiEnabled(Boolean betaApiEnabled) {
    this.betaApiEnabled = betaApiEnabled;
  }

  @Value("${" + API_USER_FILEUPLOADLIMIT_ENABLED + ":false}")
  private Boolean apiFileUploadLimitEnabled = Boolean.FALSE;

  @Value("${" + API_USER_FILEUPLOADLIMIT_HOUR + ":100}")
  private Integer apiFileUploadLimitHour = 100;

  @Value("${" + API_USER_FILEUPLOADLIMIT_DAY + ":1000}")
  private Integer apiFileUploadLimitDay = 1000;

  /**
   * Whether or not any throttling is enabled. If <code>false</code> all throttling is switched off
   */
  @Value("${api.throttling.enabled:false}")
  private Boolean apiThrottlingEnabled = Boolean.FALSE;

  /** Name of property enabling/disabling file upload restriction */
  static final String API_USER_FILEUPLOADLIMIT_ENABLED = "api.fileuploadRateLimit.enabled";

  /** Maximum file upload per user per hour, in Mb */
  static final String API_USER_FILEUPLOADLIMIT_HOUR = "api.user.fileuploadRateLimit.hour";

  /** Maximum file upload per user per day, in Mb */
  static final String API_USER_FILEUPLOADLIMIT_DAY = "api.user.fileuploadRateLimit.day";

  @Bean
  TimeSource timeSource() {
    return new DefaultTimeSource();
  }

  @Bean
  APIRequestThrottler userThrottler() {
    if (!apiThrottlingEnabled) {
      return APIRequestThrottler.PASS_THRU;
    }
    ApiRequestThrottlerImpl throttler =
        new ApiRequestThrottlerImpl(
            timeSource(), userRequestDefinitions(), userAllowanceTrackerSource());
    throttler.setMinIntervalMillis(apiUserMinInterval);
    return throttler;
  }

  @Bean
  AllowanceTrackerSource userAllowanceTrackerSource() {
    return new AllowanceTrackerSourceImpl(timeSource(), userRequestDefinitions());
  }

  @Bean
  ThrottleDefinitionSet userRequestDefinitions() {
    ThrottleDefinitionSet rc = new ThrottleDefinitionSet("requests");
    rc.addDefinition(ThrottleInterval.QUARTER_MIN, apiUserLimit15s);
    rc.addDefinition(ThrottleInterval.HOUR, apiUserLimitHour);
    rc.addDefinition(ThrottleInterval.DAY, apiUserLimitDay);
    return rc;
  }

  @Bean
  APIRequestThrottler globalThrottler() {
    if (!apiThrottlingEnabled) {
      return APIRequestThrottler.PASS_THRU;
    }
    ApiRequestThrottlerImpl throttler =
        new ApiRequestThrottlerImpl(
            timeSource(), globalRequestDefinitions(), globalAllowanceTrackerSource());
    throttler.setMinIntervalMillis(apiGlobalMinInterval);
    return throttler;
  }

  @Bean
  APIFileUploadThrottler fileUploadThrottler() {
    if (isFileUploadLimitEnabled()) {
      APIFileUploadThrottler throttler =
          new ApiFileUploadThrottlerImpl(
              timeSource(), fileUploadThrottlerDefinitions(), fileUploadThrottlerTrackerSource());
      return throttler;
    } else {
      return APIFileUploadThrottler.PASS_THRU;
    }
  }

  private Boolean isFileUploadLimitEnabled() {
    return apiFileUploadLimitEnabled;
  }

  @Bean
  ThrottleDefinitionSet fileUploadThrottlerDefinitions() {
    ThrottleDefinitionSet rc = new ThrottleDefinitionSet("Mb");
    if (isFileUploadLimitEnabled()) {
      rc.addDefinition(ThrottleInterval.HOUR, apiFileUploadLimitHour);
      rc.addDefinition(ThrottleInterval.DAY, apiFileUploadLimitDay);
    }
    return rc;
  }

  @Bean
  AllowanceTrackerSource fileUploadThrottlerTrackerSource() {
    return new AllowanceTrackerSourceImpl(timeSource(), fileUploadThrottlerDefinitions());
  }

  @Bean
  AllowanceTrackerSource globalAllowanceTrackerSource() {
    return new AllowanceTrackerSourceImpl(timeSource(), globalRequestDefinitions());
  }

  @Bean
  ThrottleDefinitionSet globalRequestDefinitions() {
    ThrottleDefinitionSet rc = new ThrottleDefinitionSet("requests");
    rc.addDefinition(ThrottleInterval.QUARTER_MIN, apiGlobalLimit15s);
    // we don't care about overall usage, just limiting peak global usage
    // every 15 seconds to max 75 requests.
    return rc;
  }

  @Bean
  ApiAccountInitialiser accountInitialiser() {

    log.info("Beta API account initialisor is {}", betaApiEnabled ? "enabled" : "disabled");
    if (betaApiEnabled) {
      return new AccountInitialiserImpl();
    } else {
      return u -> {
        throw new UnsupportedOperationException(
            "Initialising user account is not supported in this profile");
      };
    }
  }

  @Bean
  APIRequestThrottler inventoryThrottler() {
    if (!apiThrottlingEnabled) {
      return APIRequestThrottler.PASS_THRU;
    }
    ApiRequestThrottlerImpl throttler =
        new ApiRequestThrottlerImpl(
            timeSource(), inventoryRequestDefinitions(), inventoryAllowanceTrackerSource());
    throttler.setMinIntervalMillis(0);
    return throttler;
  }

  @Bean
  AllowanceTrackerSource inventoryAllowanceTrackerSource() {
    return new AllowanceTrackerSourceImpl(timeSource(), inventoryRequestDefinitions());
  }

  // is 10x rate limit for regular API
  @Bean
  ThrottleDefinitionSet inventoryRequestDefinitions() {
    ThrottleDefinitionSet rc = new ThrottleDefinitionSet("requests");
    rc.addDefinition(ThrottleInterval.HOUR, apiUserLimitHour * 1000);
    return rc;
  }
}
