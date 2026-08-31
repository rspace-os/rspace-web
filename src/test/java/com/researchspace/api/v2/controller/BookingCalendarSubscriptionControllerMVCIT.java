package com.researchspace.api.v2.controller;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.AbstractAppInitializor;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

@ApiV2WebIntegrationTest
class BookingCalendarSubscriptionControllerMVCIT {

  private static final String USER_SUBSCRIPTION_PATH =
      "/api/v2/users/me/booking-calendar-subscription";

  @Autowired private WebApplicationContext context;
  @Autowired private BookingCalendarSubscriptionDao subscriptionDao;
  @Autowired private BookingConfigurationDao configurationDao;
  @Autowired private FeatureFlagManager featureFlags;
  @Autowired private UserManager userManager;
  @Autowired private PlatformTransactionManager transactionManager;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ApiV2Fixture fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    mockMvc = fixture.mockMvc();
  }

  @AfterEach
  void tearDown() {
    fixture.cleanUp();
  }

  @Test
  void managementRequiresAuthentication() throws Exception {
    mockMvc.perform(get(path(1))).andExpect(status().isUnauthorized());
    mockMvc.perform(get(USER_SUBSCRIPTION_PATH)).andExpect(status().isUnauthorized());
  }

  @Test
  void userCalendarManagementLifecycle() throws Exception {
    String apiKey = fixture.userKey();

    mockMvc
        .perform(get(USER_SUBSCRIPTION_PATH).header("apiKey", apiKey))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", containsString("no-store")))
        .andExpect(header().string("Cache-Control", containsString("private")))
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.subscriptionUrl").value((Object) null));

    MvcResult created =
        mockMvc
            .perform(post(USER_SUBSCRIPTION_PATH).header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.updatedAt").isString())
            .andExpect(jsonPath("$.subscriptionUrl").isNotEmpty())
            .andReturn();
    String subscriptionUrl =
        objectMapper
            .readTree(created.getResponse().getContentAsByteArray())
            .path("subscriptionUrl")
            .textValue();

    mockMvc
        .perform(get(USER_SUBSCRIPTION_PATH).header("apiKey", apiKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.subscriptionUrl").value(subscriptionUrl));

    mockMvc
        .perform(delete(USER_SUBSCRIPTION_PATH).header("apiKey", apiKey))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(USER_SUBSCRIPTION_PATH).header("apiKey", apiKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.subscriptionUrl").value((Object) null));
  }

  @Test
  void managementRejectsNonPositiveConfigurationIds() throws Exception {
    String apiKey = fixture.userKey();

    mockMvc
        .perform(get(path(0)).header("apiKey", apiKey))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
    mockMvc
        .perform(post(path(-1)).header("apiKey", apiKey))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
    mockMvc
        .perform(delete(path(0)).header("apiKey", apiKey))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
  }

  @Test
  void getAndPostConcealMissingAndUnreadableConfigurations() throws Exception {
    long configurationId = readableConfiguration();
    restrictToOwner(configurationId);
    mockMvc
        .perform(get(path(Long.MAX_VALUE)).header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post(path(Long.MAX_VALUE)).header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get(path(configurationId)).header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post(path(configurationId)).header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isNotFound());
  }

  @Test
  void managementLifecycleReturnsTheCurrentUrlFromGet() throws Exception {
    long configurationId = readableConfiguration();
    String apiKey = fixture.userKey();

    mockMvc
        .perform(get(path(configurationId)).header("apiKey", apiKey))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", containsString("no-store")))
        .andExpect(header().string("Cache-Control", containsString("private")))
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.updatedAt").value((Object) null))
        .andExpect(jsonPath("$.subscriptionUrl").value((Object) null));

    String firstUrl = create(configurationId, apiKey);
    assertTrue(firstUrl.startsWith("http://") || firstUrl.startsWith("https://"));

    mockMvc
        .perform(get(path(configurationId)).header("apiKey", apiKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.updatedAt").isString())
        .andExpect(jsonPath("$.subscriptionUrl").value(firstUrl));

    String replacementUrl = create(configurationId, apiKey);
    assertNotEquals(firstUrl, replacementUrl);

    mockMvc
        .perform(delete(path(configurationId)).header("apiKey", apiKey))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Cache-Control", containsString("no-store")))
        .andExpect(header().string("Cache-Control", containsString("private")))
        .andExpect(jsonPath("$").doesNotExist());
    mockMvc
        .perform(get(path(configurationId)).header("apiKey", apiKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.subscriptionUrl").value((Object) null));
  }

  @Test
  void deleteIsIdempotentForALiveConfiguration() throws Exception {
    long configurationId = readableConfiguration();
    mockMvc
        .perform(delete(path(configurationId)).header("apiKey", fixture.userKey()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(delete(path(configurationId)).header("apiKey", fixture.userKey()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteRevokesTheCallersRowAfterReadAccessIsLost() throws Exception {
    long configurationId = readableConfiguration();
    User subscriber = fixture.otherUser();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            ignored ->
                subscriptionDao.saveAndFlush(
                    new BookableItemCalendarSubscription(
                        configurationDao.get(configurationId),
                        subscriber,
                        CryptoUtils.hashToken("unrecoverable-test-credential"),
                        new Date())));
    restrictToOwner(configurationId);

    mockMvc
        .perform(get(path(configurationId)).header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete(path(configurationId)).header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isNoContent());

    boolean remains =
        new TransactionTemplate(transactionManager)
            .execute(
                ignored ->
                    subscriptionDao
                        .findByUserIdAndConfigurationId(subscriber.getId(), configurationId)
                        .isPresent());
    assertFalse(remains);
  }

  @Test
  void managementIsForbiddenWhileBookingIsDisabled() throws Exception {
    long configurationId = readableConfiguration();
    setBookingEnabled(false);
    try {
      mockMvc
          .perform(get(path(configurationId)).header("apiKey", fixture.userKey()))
          .andExpect(status().isForbidden());
    } finally {
      setBookingEnabled(true);
    }
  }

  private long readableConfiguration() {
    long instrumentId = fixture.instrument(fixture.user(), fixture.marker());
    return fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
  }

  private void restrictToOwner(long configurationId) throws Exception {
    String accessPath = "/api/v2/booking-configurations/" + configurationId + "/access";
    MvcResult access =
        mockMvc
            .perform(get(accessPath).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andReturn();
    mockMvc
        .perform(
            put(accessPath)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, access.getResponse().getHeader(HttpHeaders.ETAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assignments":[{"granteeKey":"user:%d","role":"OWNER"}]}
                    """
                        .formatted(fixture.user().getId())))
        .andExpect(status().isOk());
  }

  private String create(long configurationId, String apiKey) throws Exception {
    MvcResult result =
        mockMvc
            .perform(post(path(configurationId)).header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(header().string("Cache-Control", containsString("private")))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.updatedAt").isString())
            .andExpect(jsonPath("$.subscriptionUrl").isNotEmpty())
            .andReturn();
    JsonNode document = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    return document.path("subscriptionUrl").textValue();
  }

  private void setBookingEnabled(boolean enabled) {
    User sysadmin = userManager.getUserByUsername(AbstractAppInitializor.SYSADMIN_UNAME);
    featureFlags
        .updateFeatureFlag(
            BOOKING_ENABLED, new FeatureFlagManager.Patch(enabled, false, null), sysadmin, sysadmin)
        .orElseThrow();
  }

  private static String path(long configurationId) {
    return "/api/v2/booking-configurations/" + configurationId + "/calendar-subscription";
  }
}
