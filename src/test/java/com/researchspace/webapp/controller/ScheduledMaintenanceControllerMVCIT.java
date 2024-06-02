package com.researchspace.webapp.controller;

import static com.researchspace.core.util.JacksonUtil.toJson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.User;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang.RandomStringUtils;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/** Integration tests covering scheduled maintenance. */
public class ScheduledMaintenanceControllerMVCIT extends MVCTestBase {

  @Autowired MockServletContext servletContext;

  private @Autowired MaintenanceManager maintenanceManager;

  private User sysUser;
  private Principal sysUserPrincipal;
  private boolean usersInitialised;

  private SimpleDateFormat dateFormat;
  private Date dateNextHour;
  private Date dateNextTwoHours;
  private Date dateNext10mins;
  private Date dateNext20mins;

  @Before
  public void setup() throws Exception {
    initMaintenanceTestUsers();
    logoutAndLoginAs(sysUser);
    initDates();
  }

  private void initMaintenanceTestUsers() {
    if (!usersInitialised) {
      sysUser = createAndSaveUser(getRandomAlphabeticString("sysadmin"), Constants.SYSADMIN_ROLE);
      sysUserPrincipal = new MockPrincipal(sysUser.getUsername());
      usersInitialised = true;
    }
  }

  private void initDates() {
    if (dateNextHour == null) {
      dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000-00:00");

      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.MINUTE, 10);
      dateNext10mins = cal.getTime();
      cal.add(Calendar.MINUTE, 10);
      dateNext20mins = cal.getTime();

      cal.add(Calendar.HOUR_OF_DAY, 1);
      dateNextHour = cal.getTime();

      cal.add(Calendar.HOUR_OF_DAY, 1);
      dateNextTwoHours = cal.getTime();
    }
  }

  @Test
  public void testNoIncomingMaintenance() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/system/maintenance/ajax/list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(sysUserPrincipal))
            .andExpect(status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()", Matchers.is(0)))
            .andReturn();
    assertNull(result.getResolvedException());

    MvcResult nextMaintenanceResult =
        mockMvc
            .perform(
                get("/system/maintenance/ajax/nextMaintenance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(sysUserPrincipal))
            .andReturn();
    String nextMaintenance = nextMaintenanceResult.getResponse().getContentAsString();
    assertEquals("next maintenance should be empty", "", nextMaintenance);
  }

  @Test
  public void testCreateUpdateDeleteScheduledMaintenance() throws Exception {

    String startDateString = "2015-11-10T10:00:01.000-00:00";
    String endDateString = "2015-11-10T12:00:01.000-00:00";
    SMPost post = new SMPost(null, startDateString, endDateString, "Maintenance");
    String jsonString = toJson(post);
    MvcResult createResult =
        mockMvc
            .perform(
                post("/system/maintenance/ajax/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(sysUserPrincipal)
                    .content(jsonString))
            .andExpect(status().isOk())
            .andReturn();
    String createResponse = createResult.getResponse().getContentAsString();
    assertNotNull(createResponse);
    assertTrue("create maintenance response shouldn't be empty", createResponse.length() > 0);
    Long savedId = new Long(createResponse);

    ScheduledMaintenance createdMaintenance = maintenanceManager.getScheduledMaintenance(savedId);
    assertNotNull(createdMaintenance);
    assertNotNull(createdMaintenance.getMessage());

    String testUpdatedMessage = "new message";
    SMPost postUpdated = new SMPost(savedId, startDateString, endDateString, testUpdatedMessage);
    jsonString = toJson(postUpdated);
    MvcResult updateResult =
        mockMvc
            .perform(
                post("/system/maintenance/ajax/update")
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(sysUserPrincipal)
                    .content(jsonString))
            .andExpect(status().isOk())
            .andReturn();
    String updateResponse = updateResult.getResponse().getContentAsString();
    assertNotNull(updateResponse);

    ScheduledMaintenance updatedMaintenance = maintenanceManager.getScheduledMaintenance(savedId);
    assertNotNull(updatedMaintenance);
    assertEquals(
        "maintenance message should be updated",
        testUpdatedMessage,
        updatedMaintenance.getMessage());

    MvcResult deleteResult =
        mockMvc
            .perform(
                post("/system/maintenance/ajax/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(sysUserPrincipal)
                    .param("id", savedId.toString()))
            .andExpect(status().isOk())
            .andReturn();
    String deleteResponse = deleteResult.getResponse().getContentAsString();
    assertNotNull(deleteResponse);
    assertExceptionThrown(
        () -> maintenanceManager.getScheduledMaintenance(savedId),
        ObjectRetrievalFailureException.class);
  }

  @Test
  public void testActiveMaintenanceCreateRetrieveFinishNow() throws Exception {
    String oldDateString = "2015-01-01T00:00:01.000-00:00";
    String testMessage = "test maintenance message";
    SMPost post = new SMPost(null, oldDateString, dateFormat.format(dateNextHour), testMessage);
    MvcResult createResult =
        mockMvc
            .perform(
                post("/system/maintenance/ajax/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(sysUserPrincipal)
                    .content(toJson(post)))
            .andReturn();
    String createResponse = createResult.getResponse().getContentAsString();
    assertNotNull(createResponse);
    Long savedId = new Long(createResponse);

    MvcResult retrieveResult =
        mockMvc
            .perform(
                get("/system/maintenance/ajax/list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(sysUserPrincipal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", Matchers.is(1))) // one maintenance scheduled
            .andExpect(jsonPath("$.[0].id", Matchers.is(savedId.intValue())))
            .andExpect(jsonPath("$.[0].activeNow", Matchers.is(Boolean.TRUE)))
            .andExpect(jsonPath("$.[0].formattedStartDate", Matchers.is("2015-01-01 00:00")))
            .andExpect(
                jsonPath("$.[0].formattedStopUserLoginDate", Matchers.is("2014-12-31 23:50")))
            .andExpect(jsonPath("$.[0].message", Matchers.is(testMessage)))
            .andReturn();
    assertNull(retrieveResult.getResolvedException());

    MvcResult finishNowResult =
        mockMvc
            .perform(
                post("/system/maintenance/ajax/finishNow")
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(sysUserPrincipal))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(finishNowResult.getResolvedException());

    Thread.sleep(1000); // wait until next second
    MvcResult retrieveAgainResult =
        mockMvc
            .perform(
                get("/system/maintenance/ajax/list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(sysUserPrincipal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", Matchers.is(0))) // no maintenance scheduled
            .andReturn();
    assertNull(retrieveAgainResult.getResolvedException());
  }

  @Data
  @AllArgsConstructor
  static class SMPost {
    Long id;
    String startDate, endDate, message;
  }

  @Test
  public void testCreateFutureMaintenanceStopLoginDelete() throws Exception {
    SMPost post =
        new SMPost(null, dateFormat.format(dateNextHour), dateFormat.format(dateNextTwoHours), "");
    String jsonString = toJson(post);
    MvcResult createResult =
        mockMvc
            .perform(
                post("/system/maintenance/ajax/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(sysUserPrincipal)
                    .content(jsonString))
            .andReturn();
    String createResponse = createResult.getResponse().getContentAsString();
    assertNotNull(createResponse);
    Long savedId = new Long(createResponse);

    assertNextMaintenanceId(savedId);

    ScheduledMaintenance createdMaintenance = maintenanceManager.getNextScheduledMaintenance();
    assertNotNull(createdMaintenance);
    assertEquals(
        "created maintenance should be returned as next one", savedId, createdMaintenance.getId());
    assertTrue(
        "future maintenance should not stop user login", createdMaintenance.getCanUserLoginNow());

    // now add a new maintenance scheduled for earlier, and check this is returned as th
    // the next maintenance, i.e. that cache is evicted:
    SMPost earlierPost =
        new SMPost(null, dateFormat.format(dateNext10mins), dateFormat.format(dateNext20mins), "");
    jsonString = toJson(earlierPost);
    MvcResult earlier =
        mockMvc
            .perform(
                post("/system/maintenance/ajax/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(sysUserPrincipal)
                    .content(jsonString))
            .andReturn();
    Long earlierId = new Long(earlier.getResponse().getContentAsString());
    assertNextMaintenanceId(earlierId);

    MvcResult stopLoginResult =
        mockMvc
            .perform(post("/system/maintenance/ajax/stopUserLogin").principal(sysUserPrincipal))
            .andReturn();
    String stopLoginResponse = stopLoginResult.getResponse().getContentAsString();
    assertNotNull(stopLoginResponse);

    Thread.sleep(1000); // wait until next second
    ScheduledMaintenance updatedMaintenance = maintenanceManager.getNextScheduledMaintenance();
    assertNotNull(updatedMaintenance);
    assertFalse("stop login request should be applied", updatedMaintenance.getCanUserLoginNow());

    maintenanceManager.removeScheduledMaintenance(savedId, sysUser);
    maintenanceManager.removeScheduledMaintenance(earlierId, sysUser);
  }

  @Test
  public void autoValidation() throws Exception {
    SMPost post =
        new SMPost(
            null,
            dateFormat.format(dateNextHour),
            dateFormat.format(dateNextTwoHours),
            tooLongMessage());
    MvcResult createResult =
        mockMvc
            .perform(
                post("/system/maintenance/ajax/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(sysUserPrincipal)
                    .content(toJson(post)))
            .andReturn();
    assertException(createResult, IllegalArgumentException.class);
    assertTrue(
        createResult
            .getResolvedException()
            .getMessage()
            .contains(User.DEFAULT_MAXFIELD_LEN + 1 + ""));
  }

  private String tooLongMessage() {
    return RandomStringUtils.randomAlphabetic(User.DEFAULT_MAXFIELD_LEN + 1);
  }

  private void assertNextMaintenanceId(Long maintenanceId) throws Exception {
    MvcResult nextMaintenanceResult =
        mockMvc
            .perform(
                get("/system/maintenance/ajax/nextMaintenance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(sysUserPrincipal))
            .andExpect(jsonPath("$.id", Matchers.equalTo(maintenanceId.intValue())))
            .andReturn();
  }
}
