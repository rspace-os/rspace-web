package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.API_VERSION.ONE;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.junit.Assert.assertEquals;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import com.researchspace.api.v1.throttling.APIFileUploadThrottler;
import com.researchspace.api.v1.throttling.APIRequestThrottler;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles({"dev", "test-block-throttling"})
public class InterceptorOrderingV1MVCIT extends API_MVC_TestBase {

  // overrides default Dev profile to supply a failing blocker
  @Configuration
  @Profile({"dev"})
  static class BlockingThrottler {
    @Bean
    @Profile({"test-block-throttling"})
    APIRequestThrottler userThrottler() {
      return APIRequestThrottler.ALWAYS_BLOCK;
    }

    @Bean
    @Profile({"test-block-throttling"})
    APIRequestThrottler globalThrottler() {
      return APIRequestThrottler.ALWAYS_BLOCK;
    }

    @Bean
    @Profile({"test-block-throttling"})
    APIRequestThrottler inventoryThrottler() {
      return APIRequestThrottler.ALWAYS_BLOCK;
    }

    @Bean
    @Profile({"test-block-throttling"})
    APIFileUploadThrottler fileUploadThrottler() {
      return APIFileUploadThrottler.ALWAYS_BLOCK;
    }
  }

  User apiUser = null;

  String apiKey = "";

  @Autowired APIRequestThrottlingInterceptor throttle;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    apiUser = createAndSaveUser(getRandomAlphabeticString("user"));
    apiKey = createNewApiKeyForUser(apiUser);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void throttlingDominatesAuthorisation() throws Exception {
    MvcResult result =
        mockMvc
            .perform(createBuilderForGet(ONE, randomAlphabetic(MIN_KEY_LENGTH), STATUS, apiUser))
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertEquals(TOO_MANY_REQUESTS.value(), error.getHttpCode());

    // anonymous
    result = mockMvc.perform(createBuilderForGet(ONE, "", STATUS, apiUser)).andReturn();
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertEquals(TOO_MANY_REQUESTS.value(), error.getHttpCode());
  }
}
