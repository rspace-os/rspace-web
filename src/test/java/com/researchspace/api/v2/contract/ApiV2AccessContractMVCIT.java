package com.researchspace.api.v2.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

/**
 * The three access layers the design describes: operation exposure, the collection {@code
 * AccessPolicy}, and row constraints folded into every read path.
 *
 * <p>Corresponds to PayloadCMS's {@code test/access-control}. The reference suite's {@code
 * overrideAccess} and sibling-data field rules have no v2 equivalent; the parts that do, notably
 * "field without read access should not show" and "should not bulk update with a read restricted
 * field query", appear here as the unreadable-field and unreadable-row cases.
 */
@ApiV2WebIntegrationTest
@DisplayName("REST API v2 access contract")
class ApiV2AccessContractMVCIT {

  private static final String MAINTENANCES = "/api/v2/maintenances";
  private static final String USERS = "/api/v2/users";

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

  @Nested
  @DisplayName("operation exposure")
  class OperationExposure {

    /**
     * {@code users} and {@code instruments} declare only {@code LIST}, {@code COUNT} and {@code
     * READ}, so the generic controller's write mappings must refuse before any policy runs. 405
     * rather than 401 or 404 is what an integrator will actually see, and it is now stated in
     * {@code RestApiV2Collections.md} rather than left for them to discover.
     */
    @ParameterizedTest(name = "{0} {1} is not an exposed operation")
    @CsvSource(
        delimiter = '|',
        value = {
          "POST   | /api/v2/users        | {}",
          "PATCH  | /api/v2/users/1      | {}",
          "DELETE | /api/v2/users/1      | ",
          "POST   | /api/v2/instruments  | {}",
          "PATCH  | /api/v2/instruments/1| {}",
          "DELETE | /api/v2/instruments/1| ",
        })
    @DisplayName("a route the resource does not expose answers 405")
    void aRouteTheResourceDoesNotExposeAnswers405(String method, String path, String body)
        throws Exception {
      MockHttpServletRequestBuilder httpRequest =
          request(HttpMethod.valueOf(method.trim()), path.trim())
              .header("apiKey", fixture.sysadminKey());
      if (body != null && !body.isBlank()) {
        httpRequest.contentType(MediaType.APPLICATION_JSON).content(body.trim());
      }

      mockMvc
          .perform(httpRequest)
          .andExpect(status().isMethodNotAllowed())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }

    /**
     * RFC 9110 requires {@code Allow} on every 405, and the two 405 paths must agree: {@code
     * ApiV2ErrorContractMVCIT} asserts it for the Spring-raised case, this one for the
     * resource-level refusal. The header names the methods the resource does serve, so a client
     * probing for routes learns something usable rather than only that this one is wrong.
     */
    @Test
    @DisplayName("a 405 from operation exposure carries Allow naming the exposed methods")
    void aResourceLevel405CarriesAllow() throws Exception {
      mockMvc
          .perform(
              post(USERS)
                  .header("apiKey", fixture.sysadminKey())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isMethodNotAllowed())
          .andExpect(header().string(HttpHeaders.ALLOW, Matchers.containsString("GET")))
          .andExpect(
              header().string(HttpHeaders.ALLOW, Matchers.not(Matchers.containsString("POST"))));
    }

    @Test
    @DisplayName("an unregistered resource name answers 404")
    void anUnregisteredResourceNameAnswers404() throws Exception {
      mockMvc
          .perform(get("/api/v2/widgets").header("apiKey", fixture.sysadminKey()))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("collection policy")
  class CollectionPolicy {

    /**
     * The design draws this distinction explicitly: an anonymous caller has not identified itself,
     * so it gets 401 with a challenge; an identified caller that the policy refuses gets 403.
     */
    @Test
    @DisplayName("an anonymous write is 401 and an authenticated but unprivileged write is 403")
    void anonymousIs401AndUnprivilegedIs403() throws Exception {
      String body =
          """
          {"startDate":"2030-01-01T00:00:00Z","endDate":"2030-01-01T01:00:00Z"}\
          """;

      mockMvc
          .perform(post(MAINTENANCES).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isUnauthorized())
          .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

      mockMvc
          .perform(
              post(MAINTENANCES)
                  .header("apiKey", fixture.userKey())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }

    @Test
    @DisplayName("read and write access are independent: an unprivileged caller still reads")
    void readAndWriteAccessAreIndependent() throws Exception {
      long id = fixture.maintenance(future(), future().plus(1, ChronoUnit.HOURS));

      mockMvc
          .perform(get(MAINTENANCES + "/" + id).header("apiKey", fixture.userKey()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id));
    }

    @ParameterizedTest(name = "{0} {1} is refused for an ordinary user")
    @CsvSource(
        delimiter = '|',
        value = {
          "PATCH  | /api/v2/maintenances | {\"message\":\"x\"}",
          "DELETE | /api/v2/maintenances | ",
        })
    @DisplayName("bulk mutation is refused by the same policy as single mutation")
    void bulkMutationIsRefusedByTheSamePolicy(String method, String path, String body)
        throws Exception {
      MockHttpServletRequestBuilder httpRequest =
          request(HttpMethod.valueOf(method.trim()), path.trim())
              .header("apiKey", fixture.userKey())
              .param("where", "id==1");
      if (body != null && !body.isBlank()) {
        httpRequest.contentType(MediaType.APPLICATION_JSON).content(body.trim());
      }

      mockMvc.perform(httpRequest).andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("row constraints")
  class RowConstraints {

    /**
     * The anonymous read rule is a row constraint, and the design says the framework combines it
     * with the client filter across list, count and read-by-id. A past window is therefore
     * invisible to an anonymous caller on all three routes and visible to an authenticated one.
     *
     * <p>Regression guard for a 500 that used to hit every anonymous {@code GET
     * /api/v2/maintenances/{id}}, whatever the id: {@code AbstractCollectionManager.getResource}
     * built {@code ResourceRequest.unpaged(idFilter)} for the constrained branch and handed it to
     * the paginated Blaze query, which rejects a page with no order by. It now carries the
     * collection's default sort. The unconstrained branch never had the problem, so only a
     * constrained caller could see it.
     */
    @Test
    @DisplayName("an anonymous read constraint applies to list, count and read alike")
    void anAnonymousReadConstraintAppliesEverywhere() throws Exception {
      Instant pastStart = Instant.now().minus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
      long past = fixture.maintenance(pastStart, pastStart.plus(1, ChronoUnit.HOURS));
      String mine = "message==" + fixture.marker();

      mockMvc.perform(get(MAINTENANCES + "/" + past)).andExpect(status().isNotFound());
      mockMvc
          .perform(get(MAINTENANCES).param("where", mine))
          .andExpect(jsonPath("$.totalDocs").value(0));
      mockMvc
          .perform(get(MAINTENANCES + "/count").param("where", mine))
          .andExpect(jsonPath("$.totalDocs").value(0));

      mockMvc
          .perform(get(MAINTENANCES + "/" + past).header("apiKey", fixture.userKey()))
          .andExpect(status().isOk());
      mockMvc
          .perform(get(MAINTENANCES).header("apiKey", fixture.userKey()).param("where", mine))
          .andExpect(jsonPath("$.totalDocs").value(1));
    }

    /** An unreadable row is absent, not refused: 403 would confirm that the row exists. */
    @Test
    @DisplayName("a row the caller may not read is 404 rather than 403")
    void anUnreadableRowIsNotFound() throws Exception {
      long otherUserId = fixture.otherUser().getId();

      mockMvc
          .perform(get(USERS + "/" + otherUserId).header("apiKey", fixture.userKey()))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }

    @Test
    @DisplayName("a self-scoped collection lists only the caller's own row")
    void aSelfScopedCollectionListsOnlyTheCallersOwnRow() throws Exception {
      long callerId = fixture.user().getId();
      fixture.otherUser();

      mockMvc
          .perform(get(USERS).header("apiKey", fixture.userKey()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").value(1))
          .andExpect(jsonPath("$.docs[0].id").value(callerId));

      mockMvc
          .perform(get(USERS + "/count").header("apiKey", fixture.userKey()))
          .andExpect(jsonPath("$.totalDocs").value(1));
    }

    /**
     * The row constraint must be inside the query rather than applied to an already-paged result,
     * or a filter that a caller may not satisfy would still consume page slots.
     */
    @Test
    @DisplayName("a client filter cannot widen a row constraint")
    void aClientFilterCannotWidenARowConstraint() throws Exception {
      long otherUserId = fixture.otherUser().getId();

      mockMvc
          .perform(
              get(USERS).header("apiKey", fixture.userKey()).param("where", "id==" + otherUserId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").value(0));
    }

    @Test
    @DisplayName("a system administrator is not narrowed by the self constraint")
    void aSystemAdministratorIsNotNarrowed() throws Exception {
      long otherUserId = fixture.otherUser().getId();

      mockMvc
          .perform(get(USERS + "/" + otherUserId).header("apiKey", fixture.sysadminKey()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(otherUserId));
    }
  }

  @Nested
  @DisplayName("domain rules the policy cannot express")
  class DomainRules {

    /**
     * The declared {@code ApiV2ErrorMapping} must convert the manager's domain exception into the
     * mapped status and stable code rather than letting it reach the generic 500 handler.
     */
    @Test
    @DisplayName("a mapped domain exception becomes its declared status and code")
    void aMappedDomainExceptionBecomesItsDeclaredStatus() throws Exception {
      Instant when = future();

      mockMvc
          .perform(
              post(MAINTENANCES)
                  .header("apiKey", fixture.sysadminKey())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"startDate":"%s","endDate":"%s"}\
                      """
                          .formatted(when, when.minus(1, ChronoUnit.HOURS))))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("errors.api.v2.maintenance.window"))
          .andExpect(jsonPath("$.title").isString());
    }

    /**
     * An unexpected server error must not leak internals, matching payload's error-handler test.
     */
    @Test
    @DisplayName("an error body carries a stable code and no internal detail")
    void anErrorBodyCarriesAStableCodeAndNoInternalDetail() throws Exception {
      mockMvc
          .perform(get(MAINTENANCES).param("where", "id==notanumber"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").isString())
          .andExpect(jsonPath("$.stackTrace").doesNotExist())
          .andExpect(jsonPath("$.exception").doesNotExist());
    }
  }

  @Nested
  @DisplayName("soft delete")
  class SoftDelete {

    /**
     * {@code softDeleteAccess} does not replace {@code deleteAccess}. A resource may explicitly opt
     * into both commands on its canonical item URI, but a read-only resource opts into neither:
     * {@code instruments} hides trashed rows through a row constraint and exposes no delete route
     * at all, so a client cannot reach either behaviour by guessing.
     */
    @Test
    @DisplayName("a read-only collection with soft-deleted rows exposes no delete route")
    void aReadOnlyCollectionExposesNoDeleteRoute() throws Exception {
      mockMvc
          .perform(delete("/api/v2/instruments/1").header("apiKey", fixture.userKey()))
          .andExpect(status().isMethodNotAllowed());
      mockMvc
          .perform(
              patch("/api/v2/instruments/1")
                  .header("apiKey", fixture.userKey())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"deleted\":true}"))
          .andExpect(status().isMethodNotAllowed());
    }
  }

  private static Instant future() {
    return Instant.now().plus(600, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
  }
}
