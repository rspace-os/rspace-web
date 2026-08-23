package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.testutils.ApiV2WebIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Which REST API v2 endpoints are anonymous, and which reject an unauthenticated caller. */
@ApiV2WebIntegrationTest
@DisplayName("REST API v2 public access")
class PublicApiV2AuthenticationMVCIT {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Nested
  @DisplayName("anonymous reads")
  class AnonymousReads {

    @Test
    @DisplayName("config is served without credentials and hides internal maintenance detail")
    void configIsPublic() throws Exception {
      mockMvc
          .perform(get("/api/v2/config"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.branding").exists())
          .andExpect(jsonPath("$.nextMaintenance").doesNotExist())
          .andExpect(jsonPath("$.deploymentDescription").isString())
          .andExpect(jsonPath("$.deploymentHelpEmail").isString());
    }

    @Test
    @DisplayName("the maintenance collection is served without credentials")
    void maintenanceCollectionIsPublic() throws Exception {
      mockMvc
          .perform(get("/api/v2/maintenances"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.docs").isArray());
    }

    @Test
    @DisplayName("the maintenance count is served without credentials")
    void maintenanceCountIsPublic() throws Exception {
      mockMvc
          .perform(get("/api/v2/maintenances/count"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").isNumber());
    }

    /**
     * Spring 6 stopped treating a trailing slash as an alias for the mapped path, and RSpace does
     * not re-enable it. Pinned because it is a framework behaviour change rather than an
     * intentional API rule: this assertion used to expect 200. If a client turns out to need it,
     * the fix is an explicit mapping or a redirect, not reviving the removed {@code
     * setUseTrailingSlashMatch}.
     */
    @ParameterizedTest(name = "GET {0} is not matched")
    @ValueSource(strings = {"/api/v2/config/", "/api/v2/maintenances/"})
    @DisplayName("a trailing slash is not an alias for the mapped path")
    void trailingSlashIsNotMatched(String path) throws Exception {
      mockMvc.perform(get(path)).andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("authenticated endpoints")
  class AuthenticatedEndpoints {

    /**
     * One invocation per endpoint, so a regression names the endpoint that stopped being protected
     * rather than failing at the first of a run of assertions and hiding the rest.
     */
    @ParameterizedTest(name = "{0} {1} rejects an anonymous caller")
    @CsvSource(
        delimiter = '|',
        value = {
          "POST   | /api/v2/maintenances   | {\"startDate\":\"2026-08-01T10:00:00Z\","
              + "\"endDate\":\"2026-08-01T11:00:00Z\"}",
          "PATCH  | /api/v2/maintenances/1 | {\"message\":\"test\"}",
          "DELETE | /api/v2/maintenances/1 | ",
        })
    @DisplayName("mutating maintenance endpoints require credentials")
    void mutatingEndpointsRequireCredentials(String method, String path, String body)
        throws Exception {
      MockHttpServletRequestBuilder httpRequest =
          request(HttpMethod.valueOf(method.trim()), path.trim());
      if (body != null && !body.isBlank()) {
        httpRequest.contentType(MediaType.APPLICATION_JSON).content(body.trim());
      }

      mockMvc.perform(httpRequest).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the current-user endpoint answers 401 as an RFC 9457 problem")
    void currentUserRequiresCredentials() throws Exception {
      mockMvc
          .perform(get("/api/v2/users/me"))
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
          .andExpect(jsonPath("$.status").value(401))
          .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    @DisplayName("a 401 carries a WWW-Authenticate challenge")
    void unauthorizedCarriesAChallenge() throws Exception {
      MockHttpServletResponse response =
          mockMvc.perform(get("/api/v2/users/me")).andReturn().getResponse();

      assertAll(
          () -> assertEquals(401, response.getStatus()),
          () -> assertEquals("Bearer", response.getHeader(HttpHeaders.WWW_AUTHENTICATE)));
    }
  }
}
