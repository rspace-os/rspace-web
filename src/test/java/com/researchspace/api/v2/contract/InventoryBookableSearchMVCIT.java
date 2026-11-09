package com.researchspace.api.v2.contract;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.booking.service.BookingConfigurationDefaultsManager;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingDefaultAccessGrantee;
import com.researchspace.model.booking.BookingDefaultSharedWith;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.AbstractAppInitializor;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.util.List;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;

/** Inventory's filter-only search retains caller-relative Booking and Inventory visibility. */
@ApiV2WebIntegrationTest
class InventoryBookableSearchMVCIT {
  private final WebApplicationContext context;
  private final BookingConfigurationDefaultsManager settingsManager;
  private final UserManager userManager;
  private final JdbcTemplate jdbcTemplate;

  @Autowired
  InventoryBookableSearchMVCIT(
      WebApplicationContext context,
      BookingConfigurationDefaultsManager settingsManager,
      UserManager userManager,
      JdbcTemplate jdbcTemplate) {
    this.context = context;
    this.settingsManager = settingsManager;
    this.userManager = userManager;
    this.jdbcTemplate = jdbcTemplate;
  }

  private ApiV2Fixture fixture;
  private SecurityManager previousSecurityManager;

  @BeforeEach
  void setUp() {
    previousSecurityManager = SecurityUtils.getSecurityManager();
    SecurityUtils.setSecurityManager(context.getBean("securityManagerTest", SecurityManager.class));
    ThreadContext.unbindSubject();
    fixture = ApiV2Fixture.in(context);
  }

  @AfterEach
  void tearDown() {
    try {
      fixture.cleanUp();
    } finally {
      ThreadContext.unbindSubject();
      SecurityUtils.setSecurityManager(previousSecurityManager);
    }
  }

  @Test
  void filtersWithoutTextAndRetainsPermissionsAndTextValidation() throws Exception {
    var parent = fixture.container(fixture.user(), fixture.marker());
    long bookable = fixture.instrumentIn(fixture.user(), fixture.marker(), parent);
    long ordinary = fixture.instrument(fixture.user(), fixture.marker());
    long privateBookable = fixture.instrument(fixture.otherUser(), fixture.marker());
    fixture.bookingConfiguration(bookable, "UTC", fixture.userKey());
    fixture.bookingConfiguration(privateBookable, "UTC", fixture.otherUserKey());
    long thirdUserInstrument = fixture.instrument(fixture.thirdUser(), fixture.marker());

    for (String query : new String[] {"", "   "}) {
      fixture
          .mockMvc()
          .perform(
              get("/api/inventory/v1/search")
                  .header("apiKey", fixture.userKey())
                  .principal(() -> fixture.user().getUsername())
                  .param("resultType", "INSTRUMENT")
                  .param("query", query)
                  .param("bookable", "true"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.records[*].id", containsInAnyOrder((int) bookable)));
      fixture
          .mockMvc()
          .perform(
              get("/api/inventory/v1/search")
                  .header("apiKey", fixture.userKey())
                  .principal(() -> fixture.user().getUsername())
                  .param("resultType", "INSTRUMENT")
                  .param("query", query)
                  .param("bookable", "false"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.records[*].id", containsInAnyOrder((int) ordinary)));
    }

    fixture
        .mockMvc()
        .perform(
            get("/api/inventory/v1/search")
                .header("apiKey", fixture.userKey())
                .principal(() -> fixture.user().getUsername())
                .param("resultType", "INSTRUMENT")
                .param("parentGlobalId", parent.getGlobalId())
                .param("bookable", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.records").isEmpty());
    fixture
        .mockMvc()
        .perform(
            get("/api/inventory/v1/search")
                .header("apiKey", fixture.thirdUserKey())
                .principal(() -> fixture.thirdUser().getUsername())
                .param("resultType", "INSTRUMENT")
                .param("bookable", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.records[*].id", containsInAnyOrder((int) thirdUserInstrument)));
    fixture
        .mockMvc()
        .perform(
            get("/api/inventory/v1/search")
                .header("apiKey", fixture.thirdUserKey())
                .principal(() -> fixture.thirdUser().getUsername())
                .param("resultType", "INSTRUMENT")
                .param("bookable", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.records").isEmpty());

    fixture
        .mockMvc()
        .perform(
            get("/api/inventory/v1/search")
                .header("apiKey", fixture.userKey())
                .principal(() -> fixture.user().getUsername())
                .param("resultType", "INSTRUMENT"))
        .andExpect(status().isUnprocessableEntity());
    fixture
        .mockMvc()
        .perform(
            get("/api/inventory/v1/search")
                .header("apiKey", fixture.sysadminKey())
                .principal(() -> "sysadmin1")
                .param("query", "*")
                .param("bookable", "true"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void sysadminBookableFilterIncludesOnlyMeConfigurationWithoutPersistedAssignment()
      throws Exception {
    User sysadmin = userManager.getUserByUsername(AbstractAppInitializor.SYSADMIN_UNAME);
    BookingConfigurationDefaults original = settingsManager.getAdminDefaults(sysadmin);
    BookingDefaultSharedWith originalSharing = original.getDefaultSharedWith();
    List<String> originalGrantees =
        original.getSelectedAccessGrantees().stream()
            .map(BookingDefaultAccessGrantee::getGranteeKey)
            .toList();
    settingsManager.updateDefaults(
        BookingSchedulingSettings.Patch.empty(),
        BookingDisplaySettings.Patch.empty(),
        BookingDefaultSharedWith.ONLY_ME,
        List.of(),
        original.getConfigurationVersion(),
        sysadmin,
        sysadmin);
    try {
      long bookable = fixture.instrument(fixture.otherUser(), fixture.marker() + " bookable");
      long ordinary = fixture.instrument(fixture.otherUser(), fixture.marker() + " ordinary");
      long configuration = fixture.bookingConfiguration(bookable, "UTC", fixture.otherUserKey());
      assertEquals(
          0,
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM ResourceRoleAssignment assignment "
                  + "JOIN BookingConfiguration configuration "
                  + "ON configuration.resourceAccess_id = assignment.resourceAccess_id "
                  + "WHERE configuration.id = ? AND assignment.user_id = ?",
              Integer.class,
              configuration,
              sysadmin.getId()));

      fixture
          .mockMvc()
          .perform(
              get("/api/inventory/v1/search")
                  .header("apiKey", fixture.sysadminKey())
                  .principal(() -> sysadmin.getUsername())
                  .param("resultType", "INSTRUMENT")
                  .param("query", "\"" + fixture.marker() + "\"")
                  .param("bookable", "true"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.records[*].id", containsInAnyOrder((int) bookable)));
      fixture
          .mockMvc()
          .perform(
              get("/api/inventory/v1/search")
                  .header("apiKey", fixture.sysadminKey())
                  .principal(() -> sysadmin.getUsername())
                  .param("resultType", "INSTRUMENT")
                  .param("query", "\"" + fixture.marker() + "\"")
                  .param("bookable", "false"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.records[*].id", containsInAnyOrder((int) ordinary)));
    } finally {
      BookingConfigurationDefaults current = settingsManager.getAdminDefaults(sysadmin);
      settingsManager.updateDefaults(
          BookingSchedulingSettings.Patch.empty(),
          BookingDisplaySettings.Patch.empty(),
          originalSharing,
          originalGrantees,
          current.getConfigurationVersion(),
          sysadmin,
          sysadmin);
    }
  }
}
