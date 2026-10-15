package com.researchspace.api.v2.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
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
class ResourceAccessControllerMVCIT {

  private final WebApplicationContext context;
  private final BookingConfigurationDao configurationDao;
  private final PlatformTransactionManager transactionManager;

  @Autowired
  ResourceAccessControllerMVCIT(
      WebApplicationContext context,
      BookingConfigurationDao configurationDao,
      PlatformTransactionManager transactionManager) {
    this.context = context;
    this.configurationDao = configurationDao;
    this.transactionManager = transactionManager;
  }

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
  void ownerReadsInheritedAccessDocument() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String path = accessPath(configurationId);

    mockMvc
        .perform(get(path).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ETAG, containsString("\"")))
        .andExpect(jsonPath("$.scheme").value("booking-configurations"))
        .andExpect(jsonPath("$.inherited").value(true))
        .andExpect(jsonPath("$.assignments.length()").value(0))
        .andExpect(jsonPath("$.caller.capabilities.canManageAssignments").value(false))
        .andExpect(jsonPath("$.caller.capabilities.canManageOwners").value(false))
        .andExpect(jsonPath("$.caller.capabilities.canLeave").value(false))
        .andExpect(jsonPath("$.caller.granteeKey").value("user:" + owner.getId()));

    mockMvc
        .perform(
            get("/api/v2/booking-configurations/" + configurationId)
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingHours.length()").value(7))
        .andExpect(jsonPath("$.roleSources.length()").value(1))
        .andExpect(
            jsonPath("$.roleSources[0]")
                .value(equalTo(Map.of("kind", "IMPLICIT", "role", "OWNER"))))
        .andExpect(jsonPath("$.ownerHealth").doesNotExist());

    mockMvc
        .perform(
            get("/api/v2/booking-configurations/" + configurationId)
                .header("apiKey", fixture.sysadminKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ownerHealth").value(equalTo(Map.of())));
  }

  @Test
  void inheritedAccessRejectsReplacementWithMatchingEtag() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String path = accessPath(configurationId);
    MvcResult initial =
        mockMvc
            .perform(get(path).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inherited").value(true))
            .andExpect(jsonPath("$.assignments.length()").value(0))
            .andReturn();

    mockMvc
        .perform(
            put(path)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, initial.getResponse().getHeader(HttpHeaders.ETAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignments\":[]}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("errors.api.v2.resourceAccess.inheritedReadOnly"));
  }

  @Test
  void readableUserCanReadInheritedAccessButCannotLeaveOrReplace() throws Exception {
    User owner = fixture.user();
    User readable = fixture.makeOwnerRoleVisibleTo(fixture.otherUser(), owner);
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String path = accessPath(configurationId);
    MvcResult readableDocument =
        mockMvc
            .perform(get(path).header("apiKey", fixture.otherUserKey()))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ETAG, containsString("\"")))
            .andExpect(jsonPath("$.inherited").value(true))
            .andExpect(jsonPath("$.assignments.length()").value(0))
            .andExpect(jsonPath("$.caller.granteeKey").value("user:" + readable.getId()))
            .andExpect(jsonPath("$.caller.capabilities.canManageAssignments").value(false))
            .andExpect(jsonPath("$.caller.capabilities.canManageOwners").value(false))
            .andExpect(jsonPath("$.caller.capabilities.canLeave").value(false))
            .andReturn();

    mockMvc
        .perform(
            put(path)
                .header("apiKey", fixture.otherUserKey())
                .header(
                    HttpHeaders.IF_MATCH,
                    readableDocument.getResponse().getHeader(HttpHeaders.ETAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignments\":[]}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("errors.api.v2.resourceAccess.inheritedReadOnly"));

    mockMvc
        .perform(delete(path + "/me").header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("errors.api.v2.resourceAccess.inheritedReadOnly"));
  }

  @Test
  void inheritedAccessDoesNotExposeGranteeDirectory() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());

    mockMvc
        .perform(
            get(accessPath(configurationId) + "/grantees")
                .queryParam("query", "us")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("errors.api.v2.resourceAccess.forbidden"));
  }

  @Test
  void inheritedAccessMutationRemainsReadOnlyAfterArchiving() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String path = accessPath(configurationId);
    MvcResult initial =
        mockMvc
            .perform(get(path).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andReturn();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            ignored -> {
              var configuration = configurationDao.lockById(configurationId).orElseThrow();
              configuration.setState(BookingConfigurationState.ARCHIVED);
              configurationDao.saveAndFlush(configuration);
            });

    mockMvc
        .perform(
            put(path)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, initial.getResponse().getHeader(HttpHeaders.ETAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignments\":[]}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("errors.api.v2.resourceAccess.inheritedReadOnly"));

    mockMvc
        .perform(delete(path + "/me").header("apiKey", fixture.userKey()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("errors.api.v2.resourceAccess.inheritedReadOnly"));
  }

  @Test
  void unregisteredResourcesAreConcealedAndSettingsDirectoryIsSysadminOnly() throws Exception {
    fixture.enableBookings();
    mockMvc
        .perform(get("/api/v2/maintenances/1/access").header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/v2/booking-settings/access-grantees")
                .queryParam("q", "us")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v2/booking-settings/access-grantees")
                .queryParam("q", "us")
                .header("apiKey", fixture.sysadminKey()))
        .andDo(ResourceAccessControllerMVCIT::failOnUnexpectedServerError)
        .andExpect(status().isOk());
  }

  @Test
  void rejectsAnOversizedAssignmentDocumentBeforeResolvingGrantees() throws Exception {
    List<Map<String, String>> grants =
        IntStream.range(0, ResourceAccessController.MAX_ASSIGNMENTS + 1)
            .mapToObj(index -> Map.of("granteeKey", "user:" + (index + 1), "role", "BOOKER"))
            .toList();

    mockMvc
        .perform(
            put("/api/v2/booking-configurations/1/access")
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JacksonUtil.toJson(Map.of("assignments", grants))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.resourceAccess.assignmentLimit"));
  }

  private static String accessPath(long configurationId) {
    return "/api/v2/booking-configurations/" + configurationId + "/access";
  }

  private static void failOnUnexpectedServerError(MvcResult result) {
    if (result.getResponse().getStatus() >= 500 && result.getResolvedException() != null) {
      throw new AssertionError("Unexpected server error", result.getResolvedException());
    }
  }
}
