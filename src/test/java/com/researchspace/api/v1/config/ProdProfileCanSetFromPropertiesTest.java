package com.researchspace.api.v1.config;

import static com.researchspace.api.v1.config.ProdAPIConfig.API_GLOBAL_MIN_INTERVAL;
import static com.researchspace.api.v1.config.ProdAPIConfig.API_USER_MIN_INTERVAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.api.v1.auth.ApiAuthenticator;
import com.researchspace.api.v1.auth.OAuthTokenAuthenticator;
import com.researchspace.api.v1.controller.APIRequestThrottlingInterceptor;
import com.researchspace.api.v1.controller.FilesAPIHandler;
import com.researchspace.api.v1.service.ExportApiHandler;
import com.researchspace.api.v1.service.RSFormApiHandler;
import com.researchspace.api.v1.throttling.APIRequestThrottler;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.service.FormManager;
import com.researchspace.service.IconImageManager;
import com.researchspace.service.UserApiKeyManager;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@RunWith(Suite.class)
@SuiteClasses({
  ProdProfileCanSetFromPropertiesTest.UserPropertiesSet.class,
  ProdProfileCanSetFromPropertiesTest.DisabledThrottling.class,
  ProdProfileCanSetFromPropertiesTest.UserPropertiesSetWronglyLogsWarning.class
})
public class ProdProfileCanSetFromPropertiesTest {
  // using a test-specific spring profile enables more precise control over what
  // beans are created,
  // so as to avoid having to load entire Spring-prod beans before running the
  // test.
  // Also it avoid polluting 'prod' profile with duplicate bean definitions
  @Configuration
  @Profile("api-prod-config-test")
  public static class ApiProdConfigTestHelper {

    // these are mocks for various transitive dependencies
    @Mock UserApiKeyManager userApiKeyMgr;

    @Mock OAuthTokenAuthenticator oauthTokenAuthenticator;
    @Mock FormManager formMgr;

    private void initMocks() {
      MockitoAnnotations.initMocks(ApiProdConfigTestHelper.class);
    }

    @Bean
    UserApiKeyManager userApiKeyManager() {
      initMocks();
      return userApiKeyMgr;
    }

    @Bean
    OAuthTokenAuthenticator oAuthTokenAuthenticator() {
      initMocks();
      return oauthTokenAuthenticator;
    }

    @Bean
    FormManager formManager() {
      initMocks();
      return formMgr;
    }
  }

  @Configuration
  @Profile("api-prod-config-test")
  public static class ProdApiConfTss extends ProdAPIConfig {
    // implementation can be changed in future or altered depending on environment

    @Mock ApiAuthenticator apiAuthenticator;
    @Mock FilesAPIHandler filesAPIHandler;
    @Mock ExportApiHandler exportApiHandler;
    @Mock IconImageManager iconMgr;
    @Mock IPermissionUtils permUtils;
    @Mock UserApiKeyManager userApiKeyMgr;
    @Mock RSFormApiHandler formApiHandler;

    private void initMocks() {
      MockitoAnnotations.initMocks(ProdApiConfTss.class);
    }

    @Bean
    RSFormApiHandler formApiHandler() {
      return formApiHandler;
    }

    @Bean
    IPermissionUtils permissionUtils() {
      initMocks();
      return permUtils;
    }

    @Bean
    IconImageManager iconImageManager() {
      initMocks();
      return iconMgr;
    }

    @Bean
    // something in spring 5.2 update causes dependency not to set, setting required to false fixes.
    ApiAuthenticator apiAuthenticator(
        @Autowired(required = false) OAuthTokenAuthenticator oAuthTokenAuthenticator) {
      initMocks();
      return apiAuthenticator;
    }

    @Bean
    public FilesAPIHandler filesAPIHandler() {
      initMocks();
      return filesAPIHandler;
    }

    @Bean
    public ExportApiHandler exportApiHandler() {
      initMocks();
      return exportApiHandler;
    }

    @Bean
    UserApiKeyManager userApiKeyManager() {
      initMocks();
      return userApiKeyMgr;
    }
  }

  @TestPropertySource(
      properties = {API_USER_MIN_INTERVAL + "=1000", API_GLOBAL_MIN_INTERVAL + "=1234"})
  @ContextConfiguration(classes = {ApiProdConfigTestHelper.class, ProdApiConfTss.class})
  @ActiveProfiles("api-prod-config-test")
  public static class UserPropertiesSet extends APIProdConfigTestBase {

    @Autowired APIRequestThrottler userThrottler;

    @Test
    public void testSetupConfiguredFromProperties() {
      assertEquals(0, userMinInterval());
      assertEquals(0, globalMinInteval());
      assertTrue(userThrottler.proceed("any"));
      assertTrue(globalThrottler.proceed("global"));
    }
  }

  // rspac-2096
  @TestPropertySource(
      properties = {"api.throttling.enabled=false", API_GLOBAL_MIN_INTERVAL + "=200000"})
  @ContextConfiguration(classes = {ApiProdConfigTestHelper.class, ProdApiConfTss.class})
  @ActiveProfiles("api-prod-config-test")
  public static class DisabledThrottling extends APIProdConfigTestBase {

    @Test
    public void testNoLimitation() {
      assertEquals(0, globalMinInteval());
      assertEquals(APIRequestThrottler.PASS_THRU, globalThrottler);
      assertEquals(APIRequestThrottler.PASS_THRU, userThrottler);
      // 30 very rapid invocations would trigger the throttler to reject requests, if
      // it was enabled.
      IntStream.range(0, 30)
          .forEach(
              i -> {
                assertTrue(userThrottler.proceed("any"));
                assertTrue(globalThrottler.proceed("global"));
              });
    }
  }

  @TestPropertySource(
      properties = {API_USER_MIN_INTERVAL + "=1000", API_GLOBAL_MIN_INTERVAL + "=500"})
  @ContextConfiguration(classes = {ApiProdConfigTestHelper.class, ProdApiConfTss.class})
  @ActiveProfiles("api-prod-config-test")
  public static class UserPropertiesSetWronglyLogsWarning extends APIProdConfigTestBase {
    @Autowired APIRequestThrottlingInterceptor apiThrottleInterceptor;

    @Test
    public void testSetupConfiguredFromProperties() {
      assertEquals(0, userMinInterval());
      assertEquals(0, globalMinInteval());
    }
  }
}
