package com.researchspace.api.v2.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * RSQL filtering, sorting, pagination and field selection compiled against the real database.
 *
 * <p>Mirrors the Operators, pagination and sort blocks of PayloadCMS's {@code
 * test/collections-rest/int.spec.ts}, minus the parts of that suite this API has no equivalent for
 * (geospatial {@code near}/{@code within}/{@code intersects}, localisation, {@code
 * pagination=false} and {@code limit=0}).
 *
 * <p>One deliberate deviation from the reference: PayloadCMS ignores a sort on a field that does
 * not exist, while this API rejects it. The stricter behaviour is asserted here on purpose.
 */
@ApiV2WebIntegrationTest
@DisplayName("REST API v2 query contract")
class ApiV2QueryContractMVCIT {

  private static final String MAINTENANCES = "/api/v2/maintenances";
  private static final String INSTRUMENTS = "/api/v2/instruments";

  @Autowired private WebApplicationContext context;

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;
  private String sysadminKey;

  private final Instant base =
      Instant.now().plus(500, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

  private long first;
  private long second;
  private long third;

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

  /** Three rows an hour apart, so ordering and range comparisons have something to bite on. */
  private void threeRows() {
    first = fixture.maintenance(base, base.plus(30, ChronoUnit.MINUTES));
    second = fixture.maintenance(base.plus(1, ChronoUnit.HOURS), base.plus(90, ChronoUnit.MINUTES));
    third = fixture.maintenance(base.plus(2, ChronoUnit.HOURS), base.plus(150, ChronoUnit.MINUTES));
  }

  /** Restricts a query to this test method's rows. */
  private String and(String expression) {
    return "message==" + fixture.marker() + ";" + expression;
  }

  private String mine() {
    return "message==" + fixture.marker();
  }

  @Nested
  @DisplayName("operators")
  class Operators {

    @Test
    @DisplayName("equals and not_equals select on an exact value")
    void equalsAndNotEquals() throws Exception {
      threeRows();

      mockMvc
          .perform(
              get(MAINTENANCES).header("apiKey", sysadminKey).param("where", and("id==" + second)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").value(1))
          .andExpect(jsonPath("$.docs[0].id").value(second));

      mockMvc
          .perform(
              get(MAINTENANCES).header("apiKey", sysadminKey).param("where", and("id!=" + second)))
          .andExpect(jsonPath("$.totalDocs").value(2));
    }

    @Test
    @DisplayName("in and out select on a value set")
    void inAndOut() throws Exception {
      threeRows();

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", and("id=in=(%d,%d)".formatted(first, third))))
          .andExpect(jsonPath("$.totalDocs").value(2));

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", and("id=out=(%d,%d)".formatted(first, third))))
          .andExpect(jsonPath("$.totalDocs").value(1))
          .andExpect(jsonPath("$.docs[0].id").value(second));
    }

    @ParameterizedTest(name = "{0} matches {1} of three rows")
    @CsvSource({
      "startDate=gt=%s, 2",
      "startDate=ge=%s, 3",
      "startDate=lt=%s, 0",
      "startDate=le=%s, 1",
    })
    @DisplayName("ordered comparisons work on a date field")
    void orderedComparisonsOnADate(String template, int expected) throws Exception {
      threeRows();

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", and(template.formatted(base))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").value(expected));
    }

    @Test
    @DisplayName("contains and a wildcard equality match a substring")
    void containsAndWildcardEquality() throws Exception {
      threeRows();

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", "message=contains=" + fixture.marker()))
          .andExpect(jsonPath("$.totalDocs").value(3));

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", "message==*" + fixture.marker() + "*"))
          .andExpect(jsonPath("$.totalDocs").value(3));
    }

    @Test
    @DisplayName("exists distinguishes a set value from an absent one on a text field")
    void existsOnAText() throws Exception {
      threeRows();

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", and("message=exists=true")))
          .andExpect(jsonPath("$.totalDocs").value(3));
    }

    @Test
    @DisplayName("and narrows, or widens")
    void andNarrowsOrWidens() throws Exception {
      threeRows();

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", and("id==%d;id==%d".formatted(first, second))))
          .andExpect(jsonPath("$.totalDocs").value(0));

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", and("(id==%d,id==%d)".formatted(first, second))))
          .andExpect(jsonPath("$.totalDocs").value(2));
    }

    /**
     * The operator set is a property of the field type, not of RSQL. A comparison a type does not
     * support must be a client error rather than silently doing something else.
     */
    @ParameterizedTest(name = "{0} is not a supported comparison")
    @ValueSource(
        strings = {
          "startDate=contains=x", // date has no substring matching
          "startDate=exists=true", // date has no existence operator
          "message=gt=a", // text is unordered
        })
    @DisplayName("an operator the field type does not support is refused")
    void anUnsupportedOperatorIsRefused(String expression) throws Exception {
      mockMvc
          .perform(get(MAINTENANCES).header("apiKey", sysadminKey).param("where", expression))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }

    /**
     * Regression guard for the decision recorded in "Booleans should not support in and notIn": a
     * two-valued type gains nothing from set membership and the SQL it compiles to is surprising.
     */
    @ParameterizedTest(name = "boolean {0} is refused")
    @ValueSource(strings = {"deleted=in=(true,false)", "deleted=out=(true)"})
    @DisplayName("a boolean field does not accept set membership")
    void aBooleanFieldDoesNotAcceptSetMembership(String expression) throws Exception {
      mockMvc
          .perform(get(INSTRUMENTS).header("apiKey", fixture.userKey()).param("where", expression))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }

    @Test
    @DisplayName("a boolean field still accepts equality")
    void aBooleanFieldStillAcceptsEquality() throws Exception {
      mockMvc
          .perform(
              get(INSTRUMENTS).header("apiKey", fixture.userKey()).param("where", "deleted==false"))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("sorting")
  class Sorting {

    @Test
    @DisplayName("the collection default sort applies when the client supplies none")
    void theDefaultSortApplies() throws Exception {
      threeRows();

      mockMvc
          .perform(get(MAINTENANCES).header("apiKey", sysadminKey).param("where", mine()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.docs[0].id").value(first))
          .andExpect(jsonPath("$.docs[2].id").value(third));
    }

    @Test
    @DisplayName("a leading minus reverses the order")
    void aLeadingMinusReversesTheOrder() throws Exception {
      threeRows();

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", mine())
                  .param("sort", "-startDate"))
          .andExpect(jsonPath("$.docs[0].id").value(third))
          .andExpect(jsonPath("$.docs[2].id").value(first));
    }

    @ParameterizedTest(name = "sort={0} is refused")
    @ValueSource(
        strings = {
          "nosuchfield", // unknown
          "canUserLoginNow", // declared sortable = false
          "startDate,startDate", // repeated field
          "a,b,c,d,e,f", // beyond MAX_SORT_FIELDS
        })
    @DisplayName("an unusable sort is a client error rather than being ignored")
    void anUnusableSortIsAClientError(String sort) throws Exception {
      mockMvc
          .perform(get(MAINTENANCES).header("apiKey", sysadminKey).param("sort", sort))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }

    @Test
    @DisplayName("a derived field with no column can be neither filtered nor sorted")
    void aDerivedFieldCanBeNeitherFilteredNorSorted() throws Exception {
      String userKey = fixture.userKey();

      mockMvc
          .perform(get(INSTRUMENTS).header("apiKey", userKey).param("sort", "globalId"))
          .andExpect(status().isBadRequest());
      mockMvc
          .perform(get(INSTRUMENTS).header("apiKey", userKey).param("where", "globalId==IN1"))
          .andExpect(status().isBadRequest());
    }

    /** {@code deleted} is declared filterable but not sortable, so the two must be independent. */
    @Test
    @DisplayName("filterable and sortable are independent capabilities")
    void filterableAndSortableAreIndependent() throws Exception {
      String userKey = fixture.userKey();

      mockMvc
          .perform(get(INSTRUMENTS).header("apiKey", userKey).param("where", "deleted==false"))
          .andExpect(status().isOk());
      mockMvc
          .perform(get(INSTRUMENTS).header("apiKey", userKey).param("sort", "deleted"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("pagination")
  class Pagination {

    @Test
    @DisplayName("the envelope reports the position of the page within the result set")
    void theEnvelopeReportsThePosition() throws Exception {
      threeRows();

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", mine())
                  .param("limit", "2")
                  .param("page", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.docs", Matchers.hasSize(2)))
          .andExpect(jsonPath("$.totalDocs").value(3))
          .andExpect(jsonPath("$.limit").value(2))
          .andExpect(jsonPath("$.page").value(1))
          .andExpect(jsonPath("$.pagingCounter").value(1))
          .andExpect(jsonPath("$.totalPages").value(2))
          .andExpect(jsonPath("$.hasPrevPage").value(false))
          .andExpect(jsonPath("$.hasNextPage").value(true))
          .andExpect(jsonPath("$.prevPage").doesNotExist())
          .andExpect(jsonPath("$.nextPage").value(2));

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", mine())
                  .param("limit", "2")
                  .param("page", "2"))
          .andExpect(jsonPath("$.docs", Matchers.hasSize(1)))
          .andExpect(jsonPath("$.pagingCounter").value(3))
          .andExpect(jsonPath("$.hasPrevPage").value(true))
          .andExpect(jsonPath("$.hasNextPage").value(false))
          .andExpect(jsonPath("$.prevPage").value(1))
          .andExpect(jsonPath("$.nextPage").doesNotExist());
    }

    /** A page past the end has no neighbour to point at, so both links must stay null. */
    @Test
    @DisplayName("a page past the end is empty and links nowhere")
    void aPagePastTheEndIsEmpty() throws Exception {
      threeRows();

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", mine())
                  .param("limit", "2")
                  .param("page", "9"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.docs", Matchers.hasSize(0)))
          .andExpect(jsonPath("$.totalDocs").value(3))
          .andExpect(jsonPath("$.hasPrevPage").value(false))
          .andExpect(jsonPath("$.hasNextPage").value(false));
    }

    @Test
    @DisplayName("count answers the same total as the list without returning documents")
    void countAgreesWithList() throws Exception {
      threeRows();

      mockMvc
          .perform(
              get(MAINTENANCES + "/count").header("apiKey", sysadminKey).param("where", mine()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").value(3))
          .andExpect(jsonPath("$.docs").doesNotExist());
    }

    @ParameterizedTest(name = "limit={0} is refused")
    @ValueSource(strings = {"0", "-1", "101"})
    @DisplayName("a limit outside the permitted range is refused")
    void aLimitOutsideTheRangeIsRefused(String limit) throws Exception {
      mockMvc
          .perform(get(MAINTENANCES).header("apiKey", sysadminKey).param("limit", limit))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }
  }

  @Nested
  @DisplayName("field selection")
  class FieldSelection {

    @Test
    @DisplayName("fields[] returns an inclusive projection that always keeps the id")
    void inclusiveProjectionKeepsTheId() throws Exception {
      threeRows();

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", mine())
                  .param("fields[maintenances]", "message"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.docs[0].id").exists())
          .andExpect(jsonPath("$.docs[0].message").exists())
          .andExpect(jsonPath("$.docs[0].startDate").doesNotExist())
          .andExpect(jsonPath("$.docs[0].endDate").doesNotExist());
    }

    @Test
    @DisplayName("exclude[] removes the named fields and keeps the rest")
    void exclusiveProjectionRemovesTheNamedFields() throws Exception {
      threeRows();

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", mine())
                  .param("exclude[maintenances]", "message,stopUserLoginDate"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.docs[0].id").exists())
          .andExpect(jsonPath("$.docs[0].startDate").exists())
          .andExpect(jsonPath("$.docs[0].message").doesNotExist())
          .andExpect(jsonPath("$.docs[0].stopUserLoginDate").doesNotExist());
    }

    /** The response always contains the ID, so excluding it must not be able to remove it. */
    @Test
    @DisplayName("the id survives an attempt to exclude it")
    void theIdSurvivesAnAttemptToExcludeIt() throws Exception {
      threeRows();

      mockMvc
          .perform(
              get(MAINTENANCES)
                  .header("apiKey", sysadminKey)
                  .param("where", mine())
                  .param("exclude[maintenances]", "id"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.docs[0].id").exists());
    }

    @ParameterizedTest(name = "{0}={1} is refused")
    @CsvSource({
      "fields[maintenances], nosuchfield",
      "exclude[maintenances], nosuchfield",
    })
    @DisplayName("a projection naming an unknown field is refused")
    void aProjectionNamingAnUnknownFieldIsRefused(String parameter, String value) throws Exception {
      mockMvc
          .perform(get(MAINTENANCES).header("apiKey", sysadminKey).param(parameter, value))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }
  }
}
