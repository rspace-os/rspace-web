package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.researchspace.model.User;
import com.researchspace.service.audit.search.IAuditFileSearch;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebAppConfiguration
public class ActivityApiControllerMVCIT extends API_MVC_TestBase {

  private static final String ACTIVITY_URL = "/activity";
  @Autowired IAuditFileSearch srch;
  @Autowired ActivityApiController eventApiController;

  @Before
  public void setup() throws Exception {
    srch.setLogFilePrefix("RSLog");
    srch.setLogFolder(new File("src/test/resources/TestResources"));
    // override default 6 months so works with test log files
    eventApiController.setMaxAuditSearchRange(Duration.ofDays(10_000));
    super.setUp();
  }

  @Test
  public void testGetAuditEventsBasic() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = userApiKeyMgr.createKeyForUser(sysadmin).getApiKey();

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, ACTIVITY_URL, sysadmin)
                    .param("orderBy", "date asc"))
            .andExpect(jsonPath("$.totalHits").value(26))
            .andExpect(jsonPath("$.activities.length()").value(20))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    String json = result.getResponse().getContentAsString();
    String selfLink =
        JsonPath.parse(json)
            .read("$._links[?(@.rel == 'self')].link", List.class)
            .get(0)
            .toString();
    // check order by is preserved
    assertTrue(selfLink.contains("date%20asc"));
  }

  @Test
  public void testGetAuditEventsByDate() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = userApiKeyMgr.createKeyForUser(sysadmin).getApiKey();

    mockMvc
        .perform(
            createBuilderForGet(API_VERSION.ONE, apiKey, ACTIVITY_URL, sysadmin)
                // mix up from and to date
                .param("dateTo", "2014-05-17")
                .param("dateFrom", "2016-05-17"))
        .andExpect(status().is4xxClientError())
        .andExpect(jsonPath("$.internalCode").value(400_02));

    mockMvc
        .perform(
            createBuilderForGet(API_VERSION.ONE, apiKey, ACTIVITY_URL, sysadmin)
                // bad OID
                .param("oid", "XXXXX"))
        .andExpect(status().is4xxClientError())
        .andExpect(jsonPath("$.internalCode").value(400_02));

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, ACTIVITY_URL, sysadmin)
                    .param("dateFrom", "2014-05-17")
                    .param("dateTo", "2016-05-17"))
            .andExpect(jsonPath("$.totalHits").value(3))
            .andExpect(jsonPath("$.activities.length()").value(3))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());

    MvcResult result2 =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, ACTIVITY_URL, sysadmin)
                    .param("dateFrom", "2014-05-17")
                    .param("dateTo", "2016-05-17")
                    .param("actions", "SIGN", "RESTORE"))
            .andExpect(jsonPath("$.totalHits").value(2))
            .andExpect(jsonPath("$.activities.length()").value(2))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    String json = result2.getResponse().getContentAsString();
    String selfLink =
        JsonPath.parse(json)
            .read("$._links[?(@.rel == 'self')].link", List.class)
            .get(0)
            .toString();
    assertEquals(
        "http://localhost:8080/api/v1/activity?pageNumber=0&dateTo=2016-05-17&dateFrom=2014-05-17&actions=SIGN&actions=RESTORE",
        selfLink);

    // assert timestamps are in ISO8601 format
    List<String> timestamps = JsonPath.parse(json).read("$..timestamp", List.class);
    assertEquals(2, timestamps.size());
    for (String tstamp : timestamps) {
      assertNotNull(Instant.parse(tstamp));
    }
  }

  @Test
  public void testGetAuditEventsByUsername() throws Exception {
    // sysadmin has permission to see all.
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = userApiKeyMgr.createKeyForUser(sysadmin).getApiKey();
    final int EXPECTED_U1A_EVENTS = 3;
    final int EXPECTED_U2B_EVENTS = 23;
    MockHttpServletRequestBuilder reqBuilder =
        createBuilderForGet(API_VERSION.ONE, apiKey, ACTIVITY_URL, sysadmin);
    assertNHitsForUser(EXPECTED_U1A_EVENTS, EXPECTED_U1A_EVENTS, reqBuilder, "user1a");
    reqBuilder = createBuilderForGet(API_VERSION.ONE, apiKey, ACTIVITY_URL, sysadmin);
    final int pageSize = 20;
    assertNHitsForUser(EXPECTED_U2B_EVENTS, pageSize, reqBuilder, "user2b");
    // multiple users are additive
    assertNHitsForUser(
        EXPECTED_U2B_EVENTS + EXPECTED_U1A_EVENTS, pageSize, reqBuilder, "user2b", "user1a");
  }

  @Test
  public void testGetAuditEventsByUsernameRestrictedByPermission() throws Exception {
    // sysadmin has permission to see all.
    RSpaceTestUtils.login("user2b", "user1234");
    User user2b = userMgr.getUserByUsername("user2b");
    initUser(user2b);
    String apiKey = userApiKeyMgr.createKeyForUser(user2b).getApiKey();
    RSpaceTestUtils.logout();
    final int EXPECTED_U2B_EVENTS = 23;
    MockHttpServletRequestBuilder reqBuilder =
        createBuilderForGet(API_VERSION.ONE, apiKey, ACTIVITY_URL, user2b);
    assertNHitsForUser(EXPECTED_U2B_EVENTS, 20, reqBuilder, new String[] {});

    reqBuilder = createBuilderForGet(API_VERSION.ONE, apiKey, ACTIVITY_URL, user2b);
    // shouldn't retrieve logs from unauthorised person
    assertNHitsForUser(0, 0, reqBuilder, "user1a");
  }

  private MvcResult assertNHitsForUser(
      int expectedEventCount,
      int hitsOnPage,
      MockHttpServletRequestBuilder reqBuilder,
      String... usernames)
      throws Exception {
    if (usernames.length > 0) {
      reqBuilder.param("usernames", usernames);
    }
    return mockMvc
        .perform(reqBuilder)
        .andExpect(jsonPath("$.totalHits").value(expectedEventCount))
        .andExpect(jsonPath("$.activities.length()").value(hitsOnPage))
        .andExpect(status().isOk())
        .andReturn();
  }
}
