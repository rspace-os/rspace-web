package com.researchspace.api.v2.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.User;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

@ApiV2WebIntegrationTest
class ResourceAccessControllerMVCIT {

  @Autowired private WebApplicationContext context;
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
  void replacementUsesStrongEtagsAndRejectsForgedReadFields() throws Exception {
    User owner = fixture.user();
    User other = fixture.makeOwnerRoleVisibleTo(fixture.otherUser(), owner);
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String path = accessPath(configurationId);

    MvcResult initial =
        mockMvc
            .perform(get(path).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ETAG, containsString("\"")))
            .andExpect(jsonPath("$.scheme").value("booking-configurations"))
            .andExpect(jsonPath("$.caller.capabilities.canManageAssignments").value(true))
            .andExpect(jsonPath("$.caller.granteeKey").value("user:" + owner.getId()))
            .andReturn();
    String initialEtag = initial.getResponse().getHeader(HttpHeaders.ETAG);

    mockMvc
        .perform(
            put(path)
                .header("apiKey", fixture.userKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignments(owner, other, true)))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.code").value("errors.api.v2.resourceAccess.ifMatchRequired"));

    mockMvc
        .perform(
            put(path)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, initialEtag)
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignments(owner, other, true)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));

    MvcResult replaced =
        mockMvc
            .perform(
                put(path)
                    .header("apiKey", fixture.userKey())
                    .header(HttpHeaders.IF_MATCH, initialEtag)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(assignments(owner, other, false)))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.assignments[?(@.grantee.key == 'user:" + other.getId() + "')]")
                    .exists())
            .andReturn();
    String currentEtag = replaced.getResponse().getHeader(HttpHeaders.ETAG);

    mockMvc
        .perform(
            put(path)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, initialEtag)
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignments(owner, other, false)))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.code").value("errors.api.v2.resourceAccess.stale"));

    mockMvc
        .perform(
            put(path)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, currentEtag)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assignments":[
                      {"granteeKey":"user:%d","role":"BOOKER"},
                      {"granteeKey":"audience:all-users","role":"NO_ACCESS"}
                    ]}
                    """
                        .formatted(owner.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("errors.api.v2.resourceAccess.ownerRequired"));
  }

  @Test
  void lowerRoleCanLeaveWithoutReadingTheAccessDocument() throws Exception {
    User owner = fixture.user();
    User booker = fixture.makeOwnerRoleVisibleTo(fixture.otherUser(), owner);
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String path = accessPath(configurationId);
    MvcResult initial =
        mockMvc
            .perform(get(path).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andReturn();

    mockMvc
        .perform(
            put(path)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, initial.getResponse().getHeader(HttpHeaders.ETAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignments(owner, booker, false)))
        .andExpect(status().isOk());

    mockMvc
        .perform(get(path).header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete(path + "/me").header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isNoContent());
  }

  @Test
  void managerCannotAssignAnOutOfDirectoryOrUnknownUser() throws Exception {
    User owner = fixture.user();
    User manager = fixture.makeOwnerRoleVisibleTo(fixture.otherUser(), owner);
    User outsider = fixture.thirdUser();
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String path = accessPath(configurationId);
    String ownerEtag =
        mockMvc
            .perform(get(path).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.ETAG);

    mockMvc
        .perform(
            put(path)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, ownerEtag)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    assignments(
                        new String[][] {
                          {"user:" + owner.getId(), "OWNER"},
                          {"user:" + manager.getId(), "MANAGER"}
                        })))
        .andExpect(status().isOk());
    MvcResult managerDocument =
        mockMvc
            .perform(get(path).header("apiKey", fixture.otherUserKey()))
            .andExpect(status().isOk())
            .andReturn();
    String managerEtag = managerDocument.getResponse().getHeader(HttpHeaders.ETAG);
    String forged =
        assignments(
            new String[][] {
              {"user:" + owner.getId(), "OWNER"},
              {"user:" + manager.getId(), "MANAGER"},
              {"user:" + outsider.getId(), "BOOKER"}
            });

    mockMvc
        .perform(
            put(path)
                .header("apiKey", fixture.otherUserKey())
                .header(HttpHeaders.IF_MATCH, managerEtag)
                .contentType(MediaType.APPLICATION_JSON)
                .content(forged))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("errors.api.v2.resourceAccess.forbidden"));
    mockMvc
        .perform(
            put(path)
                .header("apiKey", fixture.otherUserKey())
                .header(HttpHeaders.IF_MATCH, managerEtag)
                .contentType(MediaType.APPLICATION_JSON)
                .content(forged.replace("user:" + outsider.getId(), "user:999999999")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("errors.api.v2.resourceAccess.forbidden"));
    mockMvc
        .perform(get(path).header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ETAG, managerEtag))
        .andExpect(jsonPath("$.assignments.length()").value(3));
    mockMvc
        .perform(
            get("/api/v2/booking-configurations/" + configurationId)
                .header("apiKey", fixture.thirdUserKey()))
        .andExpect(status().isNotFound());
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
                .queryParam("query", "us")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v2/booking-settings/access-grantees")
                .queryParam("query", "us")
                .header("apiKey", fixture.sysadminKey()))
        .andDo(ResourceAccessControllerMVCIT::failOnUnexpectedServerError)
        .andExpect(status().isOk());
  }

  @Test
  void rejectsAnOversizedAssignmentDocumentBeforeResolvingGrantees() throws Exception {
    String grants =
        IntStream.range(0, ResourceAccessController.MAX_ASSIGNMENTS + 1)
            .mapToObj(index -> "{\"granteeKey\":\"user:" + (index + 1) + "\",\"role\":\"BOOKER\"}")
            .collect(Collectors.joining(","));

    mockMvc
        .perform(
            put("/api/v2/booking-configurations/1/access")
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignments\":[" + grants + "]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.resourceAccess.assignmentLimit"));
  }

  private static String assignments(User owner, User booker, boolean forged) {
    return """
    {"assignments":[
      {"granteeKey":"user:%d","role":"OWNER"},
      {"granteeKey":"user:%d","role":"BOOKER"%s},
      {"granteeKey":"audience:all-users","role":"NO_ACCESS"}
    ]}
    """
        .formatted(owner.getId(), booker.getId(), forged ? ",\"name\":\"forged\"" : "");
  }

  private static String assignments(String[][] assignments) {
    return "{\"assignments\":["
        + java.util.Arrays.stream(assignments)
            .map(
                assignment ->
                    "{\"granteeKey\":\"" + assignment[0] + "\",\"role\":\"" + assignment[1] + "\"}")
            .collect(Collectors.joining(","))
        + ",{\"granteeKey\":\"audience:all-users\",\"role\":\"NO_ACCESS\"}"
        + "]}";
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
