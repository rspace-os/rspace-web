package com.researchspace.api.v2.contract;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.WebApplicationContext;

/** Inventory's filter-only search retains caller-relative Booking and Inventory visibility. */
@ApiV2WebIntegrationTest
class InventoryBookableSearchMVCIT {
  @Autowired private WebApplicationContext context;
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
}
