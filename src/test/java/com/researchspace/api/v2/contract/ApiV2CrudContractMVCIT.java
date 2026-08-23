package com.researchspace.api.v2.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * The standard collection CRUD contract over real HTTP, against {@code maintenances} because it is
 * the only collection that exposes every operation in {@code ResourceOperation}.
 *
 * <p>Structured after PayloadCMS's {@code test/collections-rest/int.spec.ts}, whose response
 * envelope, bulk {@code docs} shape and formatted bulk errors this API deliberately mirrors. Only
 * the assertions that need production wiring live here; document parsing and access-function logic
 * are covered by {@code ApiV2DocumentParserTest} and {@code ApiV2ResourceAccessTest} against mocks.
 */
@ApiV2WebIntegrationTest
@DisplayName("REST API v2 collection CRUD contract")
class ApiV2CrudContractMVCIT {

  private static final String MAINTENANCES = "/api/v2/maintenances";

  @Autowired private WebApplicationContext context;

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;
  private String sysadminKey;

  /** Far enough ahead that no other test's rows fall in the same window. */
  private final Instant start =
      Instant.now().plus(400, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

  private final Instant end = start.plus(1, ChronoUnit.HOURS);

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    mockMvc = fixture.mockMvc();
    sysadminKey = fixture.sysadminKey();
  }

  @AfterEach
  void tearDown() {
    fixture.cleanUp();
  }

  /** Selects only the rows this test method created. */
  private String mine() {
    return "message==" + fixture.marker();
  }

  @Nested
  @DisplayName("single-document routes")
  class SingleDocument {

    @Test
    @DisplayName("create answers 201 with the stored document and a generated id")
    void createAnswersTheStoredDocument() throws Exception {
      mockMvc
          .perform(
              post(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"startDate":"%s","endDate":"%s","message":"%s"}\
                      """
                          .formatted(start, end, fixture.marker())))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").isNumber())
          .andExpect(jsonPath("$.startDate").value(start.toString()))
          .andExpect(jsonPath("$.endDate").value(end.toString()))
          .andExpect(jsonPath("$.message").value(fixture.marker()));
    }

    /**
     * The record is an allowlist, so the response must contain its components and nothing else.
     * {@code ScheduledMaintenance} also exposes {@code formattedStartDate} and {@code
     * formattedEndDate}; only the declared components may appear.
     *
     * <p>Field count rather than a spot check, because an allowlist that silently gains a field is
     * the failure mode that matters, and {@code doesNotExist} can only ever name fields someone
     * already thought of.
     */
    @Test
    @DisplayName("a document contains exactly the declared fields")
    void aDocumentContainsExactlyTheDeclaredFields() throws Exception {
      long id = fixture.maintenance(start, end);

      mockMvc
          .perform(get(MAINTENANCES + "/" + id).header("apiKey", sysadminKey))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.*", Matchers.hasSize(6)))
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.startDate").exists())
          .andExpect(jsonPath("$.endDate").exists())
          .andExpect(jsonPath("$.stopUserLoginDate").exists())
          .andExpect(jsonPath("$.message").exists())
          .andExpect(jsonPath("$.canUserLoginNow").isBoolean())
          .andExpect(jsonPath("$.formattedStartDate").doesNotExist());
    }

    /**
     * {@code auditFields = false} on this resource, so the discovered audit fields must be absent.
     */
    @Test
    @DisplayName("a resource that opts out of audit fields does not publish them")
    void auditFieldsAreAbsentWhenTheResourceOptsOut() throws Exception {
      long id = fixture.maintenance(start, end);

      mockMvc
          .perform(get(MAINTENANCES + "/" + id).header("apiKey", sysadminKey))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.createdAt").doesNotExist())
          .andExpect(jsonPath("$.updatedAt").doesNotExist())
          .andExpect(jsonPath("$.createdBy").doesNotExist())
          .andExpect(jsonPath("$.updatedBy").doesNotExist());
    }

    @Test
    @DisplayName("an unknown id answers 404 as a problem")
    void anUnknownIdAnswersNotFound() throws Exception {
      mockMvc
          .perform(get(MAINTENANCES + "/999999999").header("apiKey", sysadminKey))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
          .andExpect(jsonPath("$.code").isString());
    }

    /** The spec's ID parser must reject a segment it cannot convert rather than throw. */
    @ParameterizedTest(name = "id segment \"{0}\" is rejected")
    @ValueSource(strings = {"not-a-number", "1.5", "9999999999999999999999"})
    @DisplayName("an id the parser cannot convert is a client error")
    void anUnparseableIdIsAClientError(String id) throws Exception {
      mockMvc
          .perform(get(MAINTENANCES + "/" + id).header("apiKey", sysadminKey))
          .andExpect(status().is4xxClientError())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }

    @Test
    @DisplayName("a patch changes only the supplied fields")
    void aPatchChangesOnlyTheSuppliedFields() throws Exception {
      long id = fixture.maintenance(start, end);

      mockMvc
          .perform(
              patch(MAINTENANCES + "/" + id)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"stopUserLoginDate":"%s"}\
                      """
                          .formatted(start)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.stopUserLoginDate").value(start.toString()))
          .andExpect(jsonPath("$.startDate").value(start.toString()))
          .andExpect(jsonPath("$.endDate").value(end.toString()));
    }

    @Test
    @DisplayName("an explicit null clears a nullable field")
    void anExplicitNullClearsANullableField() throws Exception {
      long id = fixture.maintenance(start, end);

      mockMvc
          .perform(
              patch(MAINTENANCES + "/" + id)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"stopUserLoginDate\":null}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.stopUserLoginDate").value(Matchers.nullValue()))
          // Present-with-null rather than omitted: ResourceRenderer puts every selected field, so
          // the field count is unchanged by clearing one.
          .andExpect(jsonPath("$.*", Matchers.hasSize(6)));
    }

    @Test
    @DisplayName("an explicit null on a non-nullable field is rejected")
    void anExplicitNullOnANonNullableFieldIsRejected() throws Exception {
      long id = fixture.maintenance(start, end);

      mockMvc
          .perform(
              patch(MAINTENANCES + "/" + id)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"startDate\":null}"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
          .andExpect(jsonPath("$.invalidParams[0].name").value("startDate"));
    }

    @Test
    @DisplayName("delete removes the row")
    void deleteRemovesTheRow() throws Exception {
      long id = fixture.maintenance(start, end);

      mockMvc
          .perform(delete(MAINTENANCES + "/" + id).header("apiKey", sysadminKey))
          .andExpect(status().isOk());
      mockMvc
          .perform(get(MAINTENANCES + "/" + id).header("apiKey", sysadminKey))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("request-body validation")
  class BodyValidation {

    @Test
    @DisplayName("malformed JSON is a problem body, not a stack trace")
    void malformedJsonIsAProblemBody() throws Exception {
      mockMvc
          .perform(
              post(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"startDate\":"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
          .andExpect(jsonPath("$.detail").isString())
          .andExpect(
              jsonPath("$.detail").value(Matchers.not(Matchers.containsString("com.fasterxml"))));
    }

    @Test
    @DisplayName("a missing required field names the field and the reason")
    void aMissingRequiredFieldNamesTheField() throws Exception {
      mockMvc
          .perform(
              post(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"startDate":"%s"}\
                      """
                          .formatted(start)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.invalidParams[?(@.name == 'endDate')]").exists());
    }

    @Test
    @DisplayName("an unknown field is rejected rather than ignored")
    void anUnknownFieldIsRejected() throws Exception {
      mockMvc
          .perform(
              post(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"startDate":"%s","endDate":"%s","nosuchfield":1}\
                      """
                          .formatted(start, end)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.invalidParams[?(@.name == 'nosuchfield')]").exists());
    }

    @ParameterizedTest(name = "{0} may not be supplied on create")
    @ValueSource(strings = {"id", "canUserLoginNow"})
    @DisplayName("a read-only field is refused on create")
    void aReadOnlyFieldIsRefusedOnCreate(String field) throws Exception {
      mockMvc
          .perform(
              post(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"startDate":"%s","endDate":"%s","%s":1}\
                      """
                          .formatted(start, end, field)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.invalidParams[?(@.name == '%s')]".formatted(field)).exists());
    }

    @Test
    @DisplayName("a scalar of the wrong shape is rejected")
    void aScalarOfTheWrongShapeIsRejected() throws Exception {
      mockMvc
          .perform(
              post(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"startDate":"not-a-date","endDate":"%s"}\
                      """
                          .formatted(end)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.invalidParams[?(@.name == 'startDate')]").exists());
    }
  }

  @Nested
  @DisplayName("bulk routes")
  class Bulk {

    @Test
    @DisplayName("bulk create answers 201 with the documents in input order")
    void bulkCreateAnswersDocumentsInInputOrder() throws Exception {
      mockMvc
          .perform(
              post(MAINTENANCES + "/bulk")
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"docs":[
                        {"startDate":"%s","endDate":"%s","message":"%s"},
                        {"startDate":"%s","endDate":"%s","message":"%s"}
                      ]}\
                      """
                          .formatted(
                              start,
                              end,
                              fixture.marker() + "-a",
                              start.plus(2, ChronoUnit.HOURS),
                              start.plus(3, ChronoUnit.HOURS),
                              fixture.marker() + "-b")))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.docs", Matchers.hasSize(2)))
          .andExpect(jsonPath("$.docs[0].message").value(fixture.marker() + "-a"))
          .andExpect(jsonPath("$.docs[1].message").value(fixture.marker() + "-b"))
          .andExpect(jsonPath("$.errors").doesNotExist());

      cleanUpBulkRows();
    }

    /**
     * One invalid document must fail the whole batch, and the invalid parameter must carry its
     * array position so a client can point at the offending element.
     */
    @Test
    @DisplayName("one invalid document fails the batch and is named by array position")
    void oneInvalidDocumentFailsTheBatch() throws Exception {
      mockMvc
          .perform(
              post(MAINTENANCES + "/bulk")
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"docs":[
                        {"startDate":"%s","endDate":"%s","message":"%s"},
                        {"startDate":"%s"}
                      ]}\
                      """
                          .formatted(start, end, fixture.marker(), start)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.invalidParams[?(@.name == 'docs[1].endDate')]").exists());

      mockMvc
          .perform(get(MAINTENANCES).header("apiKey", sysadminKey).param("where", mine()))
          .andExpect(jsonPath("$.totalDocs").value(0));
    }

    @ParameterizedTest(name = "{0} is rejected")
    @ValueSource(
        strings = {
          "{\"docs\":[]}",
          "{}",
          "{\"docs\":[{\"startDate\":\"2030-01-01T00:00:00Z\",\"endDate\":\"2030-01-01T01:00:00Z\"}],\"extra\":1}"
        })
    @DisplayName("the bulk envelope must be exactly a non-empty docs array")
    void theBulkEnvelopeMustBeExactlyANonEmptyDocsArray(String body) throws Exception {
      mockMvc
          .perform(
              post(MAINTENANCES + "/bulk")
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }

    @Test
    @DisplayName("a bulk update without a where filter is refused")
    void aBulkUpdateWithoutAWhereFilterIsRefused() throws Exception {
      mockMvc
          .perform(
              patch(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"message\":\"everything\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }

    @Test
    @DisplayName("a bulk delete without a where filter is refused")
    void aBulkDeleteWithoutAWhereFilterIsRefused() throws Exception {
      mockMvc
          .perform(delete(MAINTENANCES).header("apiKey", sysadminKey))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }

    @Test
    @DisplayName("a bulk update touches only the matched rows")
    void aBulkUpdateTouchesOnlyTheMatchedRows() throws Exception {
      long matched = fixture.maintenance(start, end);
      long untouched =
          fixture.maintenance(start.plus(2, ChronoUnit.HOURS), start.plus(3, ChronoUnit.HOURS));

      mockMvc
          .perform(
              patch(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", "id==" + matched)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"stopUserLoginDate\":\"%s\"}".formatted(start)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.docs", Matchers.hasSize(1)))
          .andExpect(jsonPath("$.docs[0].id").value(matched))
          .andExpect(jsonPath("$.errors").doesNotExist());

      // Not "has no stopUserLoginDate": ScheduledMaintenance.setStartDate derives one on create, so
      // the untouched row already has a value. What must hold is that it is not the patched one.
      mockMvc
          .perform(get(MAINTENANCES + "/" + untouched).header("apiKey", sysadminKey))
          .andExpect(jsonPath("$.stopUserLoginDate").value(Matchers.not(start.toString())));
    }

    @Test
    @DisplayName("a bulk delete removes only the matched rows")
    void aBulkDeleteRemovesOnlyTheMatchedRows() throws Exception {
      long deleted = fixture.maintenance(start, end);
      long kept =
          fixture.maintenance(start.plus(2, ChronoUnit.HOURS), start.plus(3, ChronoUnit.HOURS));

      mockMvc
          .perform(
              delete(MAINTENANCES).header("apiKey", sysadminKey).param("where", "id==" + deleted))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.docs", Matchers.hasSize(1)))
          .andExpect(jsonPath("$.docs[0].id").value(deleted));

      mockMvc
          .perform(get(MAINTENANCES + "/" + deleted).header("apiKey", sysadminKey))
          .andExpect(status().isNotFound());
      mockMvc
          .perform(get(MAINTENANCES + "/" + kept).header("apiKey", sysadminKey))
          .andExpect(status().isOk());
    }

    /** Bulk-created rows are not tracked by the fixture, so remove them by marker. */
    private void cleanUpBulkRows() throws Exception {
      mockMvc.perform(
          delete(MAINTENANCES)
              .header("apiKey", sysadminKey)
              .param("where", "message=contains=" + fixture.marker()));
    }
  }

  @Nested
  @DisplayName("response metadata")
  class ResponseMetadata {

    @Test
    @DisplayName("an authenticated response is not cached and declares its language")
    void anAuthenticatedResponseIsNotCachedAndDeclaresItsLanguage() throws Exception {
      mockMvc
          .perform(get(MAINTENANCES).header("apiKey", sysadminKey))
          .andExpect(status().isOk())
          .andExpect(
              header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("no-store")))
          .andExpect(header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("private")))
          .andExpect(header().exists(HttpHeaders.CONTENT_LANGUAGE))
          .andExpect(
              header()
                  .string(HttpHeaders.VARY, Matchers.containsString(HttpHeaders.ACCEPT_LANGUAGE)));
    }

    /**
     * The design states the server uses the deployment locale and does not select an unavailable
     * one from {@code Accept-Language}. Pinned because silently honouring the header would change
     * every error message a client sees.
     */
    @Test
    @DisplayName("an unavailable Accept-Language does not change the response language")
    void anUnavailableAcceptLanguageDoesNotChangeTheResponseLanguage() throws Exception {
      String deployment =
          mockMvc
              .perform(get(MAINTENANCES).header("apiKey", sysadminKey))
              .andReturn()
              .getResponse()
              .getHeader(HttpHeaders.CONTENT_LANGUAGE);

      String requested =
          mockMvc
              .perform(
                  get(MAINTENANCES)
                      .header("apiKey", sysadminKey)
                      .header(HttpHeaders.ACCEPT_LANGUAGE, "zz-ZZ"))
              .andReturn()
              .getResponse()
              .getHeader(HttpHeaders.CONTENT_LANGUAGE);

      assertEquals(deployment, requested);
    }
  }
}
