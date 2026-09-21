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
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventorySharingMode;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.AbstractAppInitializor;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import com.researchspace.testutils.BaseManagerTestCaseBase;
import com.researchspace.testutils.RSpaceTestUtils;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
  @Autowired private GroupManager groupManager;
  @Autowired private InstrumentEntityApiManager instrumentManager;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;

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
    fixture.enableBookings();
    String apiKey = fixture.userKey();

    MvcResult inactive =
        mockMvc
            .perform(get(USER_SUBSCRIPTION_PATH).header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ETAG, "\"inactive\""))
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(header().string("Cache-Control", containsString("private")))
            .andExpect(header().string("Cache-Control", containsString("no-transform")))
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.subscriptionUrl").value((Object) null))
            .andReturn();

    MvcResult created =
        mockMvc
            .perform(
                post(USER_SUBSCRIPTION_PATH)
                    .header("apiKey", apiKey)
                    .header(
                        HttpHeaders.IF_MATCH, inactive.getResponse().getHeader(HttpHeaders.ETAG)))
            .andExpect(status().isOk())
            .andExpect(header().exists(HttpHeaders.ETAG))
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
        .andExpect(
            header().string(HttpHeaders.ETAG, created.getResponse().getHeader(HttpHeaders.ETAG)))
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
  void userCalendarCreateRequiresTheExactCurrentEtag() throws Exception {
    fixture.enableBookings();
    String apiKey = fixture.userKey();

    mockMvc
        .perform(post(USER_SUBSCRIPTION_PATH).header("apiKey", apiKey))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.code").value("errors.api.v2.bookingCalendar.ifMatchRequired"));
    mockMvc
        .perform(
            post(USER_SUBSCRIPTION_PATH)
                .header("apiKey", apiKey)
                .header(HttpHeaders.IF_MATCH, "inactive"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
    mockMvc
        .perform(
            post(USER_SUBSCRIPTION_PATH)
                .header("apiKey", apiKey)
                .header(HttpHeaders.IF_MATCH, "\"subscription-99\""))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("errors.api.v2.bookingCalendar.subscriptionConflict"));
  }

  @Test
  void itemCalendarCreateRequiresTheExactCurrentEtag() throws Exception {
    fixture.enableBookings();
    long configurationId = readableConfiguration();
    String apiKey = fixture.userKey();

    mockMvc
        .perform(post(path(configurationId)).header("apiKey", apiKey))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.code").value("errors.api.v2.bookingCalendar.ifMatchRequired"));
    mockMvc
        .perform(
            post(path(configurationId))
                .header("apiKey", apiKey)
                .header(HttpHeaders.IF_MATCH, "inactive"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
    mockMvc
        .perform(
            post(path(configurationId))
                .header("apiKey", apiKey)
                .header(HttpHeaders.IF_MATCH, "\"subscription-99\""))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("errors.api.v2.bookingCalendar.subscriptionConflict"));
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
  void getAndPostConcealMissingUnreadableAndArchivedPrivateConfigurations() throws Exception {
    long configurationId = readableConfiguration();
    mockMvc
        .perform(get(path(Long.MAX_VALUE)).header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post(path(Long.MAX_VALUE))
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, "\"inactive\""))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get(path(configurationId)).header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post(path(configurationId))
                .header("apiKey", fixture.otherUserKey())
                .header(HttpHeaders.IF_MATCH, "\"inactive\""))
        .andExpect(status().isNotFound());

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            ignored -> {
              var configuration = configurationDao.lockById(configurationId).orElseThrow();
              configuration.setState(BookingConfigurationState.ARCHIVED);
              configurationDao.saveAndFlush(configuration);
            });
    mockMvc
        .perform(
            post(path(configurationId))
                .header("apiKey", fixture.otherUserKey())
                .header(HttpHeaders.IF_MATCH, "\"inactive\""))
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
  void deleteConcealsTheConfigurationAfterReadAccessIsLost() throws Exception {
    User subscriber = fixture.user();
    User newOwner = fixture.otherUser();
    long instrumentId = fixture.instrument(subscriber, fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    create(configurationId, fixture.userKey());

    ApiInstrument transferred = instrumentManager.getApiInstrumentById(instrumentId, subscriber);
    transferred.setOwner(new ApiUser(newOwner));
    instrumentManager.changeApiInstrumentOwner(transferred, subscriber);

    mockMvc
        .perform(get(path(configurationId)).header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete(path(configurationId)).header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());

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
  void itemLinkDoesNotReviveAfterAccessIsRestoredWithoutAnInterveningPoll() throws Exception {
    User inventoryOwner = fixture.otherUser();
    User subscriber = fixture.user();
    User pi = fixture.makeOwnerRoleVisibleTo(subscriber, inventoryOwner);
    long groupId =
        new TransactionTemplate(transactionManager)
            .execute(ignored -> userManager.get(pi.getId()).getGroups().iterator().next().getId());
    long instrumentId = fixture.instrument(inventoryOwner, fixture.marker());
    setInventorySharingMode(instrumentId, ApiInventorySharingMode.OWNER_GROUPS, inventoryOwner);
    long configurationId =
        fixture.bookingConfiguration(instrumentId, "UTC", fixture.otherUserKey());
    String oldUrl = create(configurationId, fixture.userKey());

    RSpaceTestUtils.login(pi.getUsername(), BaseManagerTestCaseBase.TESTPASSWD);
    try {
      groupManager.removeUserFromGroup(inventoryOwner.getUsername(), groupId, pi);
      groupManager.addMembersToGroup(groupId, List.of(inventoryOwner), pi.getUsername(), null, pi);
    } finally {
      RSpaceTestUtils.logout();
    }

    mockMvc
        .perform(get(path(configurationId)).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    assertNotEquals(oldUrl, create(configurationId, fixture.userKey()));
  }

  @Test
  void revocationDeletesSubscriptionCommittedAfterItsReadSnapshot() throws Exception {
    long configurationId = readableConfiguration();
    User subscriber = fixture.user();
    CountDownLatch snapshotRead = new CountDownLatch(1);
    CountDownLatch subscriptionCreated = new CountDownLatch(1);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<Void> revocation =
          pool.submit(
              () ->
                  new TransactionTemplate(transactionManager)
                      .execute(
                          ignored -> {
                            jdbcTemplate.queryForObject(
                                "select count(*) from BookingConfiguration", Long.class);
                            snapshotRead.countDown();
                            await(subscriptionCreated);
                            User disabledSubscriber = userManager.get(subscriber.getId());
                            disabledSubscriber.setEnabled(false);
                            userManager.saveUser(disabledSubscriber);
                            return null;
                          }));

      await(snapshotRead);
      try {
        create(configurationId, fixture.userKey());
      } finally {
        subscriptionCreated.countDown();
      }

      revocation.get(20, TimeUnit.SECONDS);
      assertTrue(
          Boolean.TRUE.equals(
              new TransactionTemplate(transactionManager)
                  .execute(
                      ignored ->
                          subscriptionDao
                              .findByUserIdAndConfigurationId(subscriber.getId(), configurationId)
                              .isEmpty())));
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(ignored -> userManager.get(subscriber.getId()).setEnabled(true));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void reducingInventorySharingPreservesTheOwnersCalendarLink() throws Exception {
    long instrumentId = fixture.instrument(fixture.user(), fixture.marker());
    setInventorySharingMode(instrumentId, ApiInventorySharingMode.OWNER_GROUPS, fixture.user());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String ownerUrl = create(configurationId, fixture.userKey());
    setInventorySharingMode(instrumentId, ApiInventorySharingMode.OWNER_ONLY, fixture.user());

    mockMvc
        .perform(get(path(configurationId)).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subscriptionUrl").value(ownerUrl));
  }

  @Test
  void itemLinkDoesNotReviveAfterUserReactivation() throws Exception {
    long configurationId = readableConfiguration();
    String oldUrl = create(configurationId, fixture.userKey());
    User owner = userManager.getUserByUsername(fixture.user().getUsername());
    owner.setEnabled(false);
    try {
      userManager.saveUser(owner);
    } finally {
      owner.setEnabled(true);
      userManager.saveUser(owner);
    }
    mockMvc
        .perform(get(path(configurationId)).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    assertNotEquals(oldUrl, create(configurationId, fixture.userKey()));
  }

  @Test
  void itemLinkDoesNotReviveAfterLeavingAndRejoiningItsOnlyAuthorizingGroup() throws Exception {
    User subscriber = fixture.otherUser();
    User pi = fixture.makeOwnerRoleVisibleTo(fixture.user(), subscriber);
    long groupId =
        new TransactionTemplate(transactionManager)
            .execute(ignored -> userManager.get(pi.getId()).getGroups().iterator().next().getId());
    long instrumentId = fixture.instrument(subscriber, fixture.marker());
    setInventorySharingMode(instrumentId, ApiInventorySharingMode.OWNER_GROUPS, subscriber);
    long configurationId =
        fixture.bookingConfiguration(instrumentId, "UTC", fixture.otherUserKey());
    String oldUrl = create(configurationId, fixture.userKey());
    RSpaceTestUtils.login(pi.getUsername(), BaseManagerTestCaseBase.TESTPASSWD);
    try {
      groupManager.removeUserFromGroup(subscriber.getUsername(), groupId, pi);
      groupManager.addMembersToGroup(groupId, List.of(subscriber), pi.getUsername(), null, pi);
    } finally {
      RSpaceTestUtils.logout();
    }
    mockMvc
        .perform(get(path(configurationId)).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    assertNotEquals(oldUrl, create(configurationId, fixture.userKey()));
  }

  @Test
  void managementIsForbiddenWhileBookingIsDisabled() throws Exception {
    long configurationId = readableConfiguration();
    setBookingEnabled(false);
    try {
      mockMvc
          .perform(get(path(configurationId)).header("apiKey", fixture.userKey()))
          .andExpect(status().isNotFound());
    } finally {
      setBookingEnabled(true);
    }
  }

  private long readableConfiguration() {
    long instrumentId = fixture.instrument(fixture.user(), fixture.marker());
    setInventorySharingMode(instrumentId, ApiInventorySharingMode.OWNER_ONLY, fixture.user());
    return fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
  }

  @Test
  void inheritedAccessCannotBeReplaced() throws Exception {
    long configurationId = readableConfiguration();
    String accessPath = "/api/v2/booking-configurations/" + configurationId + "/access";
    MvcResult access =
        mockMvc
            .perform(get(accessPath).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inherited").value(true))
            .andExpect(jsonPath("$.assignments.length()").value(0))
            .andReturn();
    mockMvc
        .perform(
            put(accessPath)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, access.getResponse().getHeader(HttpHeaders.ETAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignments\":[]}"))
        .andExpect(status().isForbidden());
  }

  private void setInventorySharingMode(
      long instrumentId, ApiInventorySharingMode sharingMode, User subject) {
    ApiInstrument instrument = instrumentManager.getApiInstrumentById(instrumentId, subject);
    instrument.setSharingMode(sharingMode);
    instrumentManager.updateApiInstrument(instrument, subject);
  }

  private String create(long configurationId, String apiKey) throws Exception {
    String etag =
        mockMvc
            .perform(get(path(configurationId)).header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.ETAG);
    MvcResult result =
        mockMvc
            .perform(
                post(path(configurationId))
                    .header("apiKey", apiKey)
                    .header(HttpHeaders.IF_MATCH, etag))
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

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(20, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting for concurrent test step");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
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
