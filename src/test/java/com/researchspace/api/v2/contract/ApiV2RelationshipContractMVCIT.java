package com.researchspace.api.v2.contract;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.model.User;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

/**
 * Typed relationship references, depth expansion and the default audit routes, against {@code
 * booking-configurations} because it is the only collection with a declared relationship.
 *
 * <p>Corresponds to PayloadCMS's {@code test/relationships} and the relationship half of its {@code
 * collections-rest} Querying block. The reference suite's {@code hasMany} and polymorphic arrays
 * have no equivalent here: a v2 relationship currently exposes exactly one typed reference.
 *
 * <p>Regression guard for a 500 on every response that rendered a booking-configuration document.
 * {@code ResourceRenderer} runs after the manager's transaction has closed, and the discovered
 * {@code createdBy} audit relationship read its identifier with {@code User::getId}, which
 * initialises a lazy proxy and therefore threw. The audit relationship now takes the identifier the
 * proxy already carries. Create was the only path that worked, because it holds a real entity.
 */
@ApiV2WebIntegrationTest
@Import(ApiV2RelationshipContractMVCIT.AuditClockConfiguration.class)
@DisplayName("REST API v2 relationship contract")
class ApiV2RelationshipContractMVCIT {

  private static final String CONFIGURATIONS = "/api/v2/booking-configurations";
  private static final Path AUDIT_DIRECTORY = auditDirectory();
  private static final DateTimeFormatter AUDIT_TIMESTAMP =
      DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm:ss,SSS", Locale.ENGLISH);

  @DynamicPropertySource
  static void isolatedAuditDirectory(DynamicPropertyRegistry registry) {
    registry.add("logging.dir", AUDIT_DIRECTORY::toString);
  }

  private static Path auditDirectory() {
    try {
      return Files.createTempDirectory("rspace-api-v2-audit-contract-");
    } catch (IOException error) {
      throw new ExceptionInInitializerError(error);
    }
  }

  @Configuration
  static class AuditClockConfiguration {
    @Bean
    @Primary
    Clock completedCurrentUtcDayClock() {
      return Clock.offset(Clock.system(ZoneOffset.UTC), Duration.ofDays(1));
    }
  }

  @Autowired private WebApplicationContext context;

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;
  private String sysadminKey;

  private User owner;
  private long instrumentId;

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    mockMvc = fixture.mockMvc();
    sysadminKey = fixture.sysadminKey();
    owner = fixture.user();
    instrumentId = fixture.instrument(owner, "Confocal microscope " + fixture.marker());
  }

  @AfterEach
  void tearDown() {
    fixture.cleanUp();
  }

  @Nested
  @DisplayName("reference rendering")
  class ReferenceRendering {

    @Test
    @DisplayName("a reference renders as relationTo, value and the target's global id")
    void aReferenceRendersAsRelationToValueAndGlobalId() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "Europe/Berlin");

      mockMvc
          .perform(get(CONFIGURATIONS + "/" + id).header("apiKey", fixture.userKey()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.target.relationTo").value("booking-instruments"))
          .andExpect(jsonPath("$.target.value").value(instrumentId))
          .andExpect(jsonPath("$.target.globalId").value("IN" + instrumentId));
    }

    /**
     * The envelope must not change with depth: only {@code value} becomes a document, and {@code
     * globalId} stays alongside it.
     */
    @Test
    @DisplayName("depth expands value into the target document without changing the envelope")
    void depthExpandsValueIntoTheTargetDocument() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "Europe/Berlin");

      mockMvc
          .perform(
              get(CONFIGURATIONS + "/" + id)
                  .header("apiKey", fixture.userKey())
                  .param("depth", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.target.relationTo").value("booking-instruments"))
          .andExpect(jsonPath("$.target.globalId").value("IN" + instrumentId))
          .andExpect(jsonPath("$.target.value.id").value(instrumentId))
          .andExpect(jsonPath("$.target.value.name").value(Matchers.containsString("Confocal")));
    }

    @Test
    @DisplayName("Booking access returns only the safe target reference without Inventory access")
    void bookingAccessReturnsSafeTargetReferenceWithoutInventoryAccess() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "Europe/Berlin");

      mockMvc
          .perform(get(CONFIGURATIONS + "/" + id).header("apiKey", fixture.otherUserKey()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id))
          .andExpect(jsonPath("$.target.relationTo").value("booking-instruments"))
          .andExpect(jsonPath("$.target.value").value(instrumentId))
          .andExpect(jsonPath("$.target.globalId").value("IN" + instrumentId))
          .andExpect(jsonPath("$.target.owner").doesNotExist())
          .andExpect(jsonPath("$.target.parentContainerName").doesNotExist());

      mockMvc
          .perform(
              get(CONFIGURATIONS)
                  .header("apiKey", fixture.otherUserKey())
                  .param("where", "id==" + id))
          .andExpect(jsonPath("$.totalDocs").value(1));
    }

    @Test
    @DisplayName("a depth beyond the permitted maximum is refused")
    void aDepthBeyondTheMaximumIsRefused() throws Exception {
      mockMvc
          .perform(get(CONFIGURATIONS).header("apiKey", fixture.userKey()).param("depth", "11"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }
  }

  @Nested
  @DisplayName("reference input")
  class ReferenceInput {

    @ParameterizedTest(name = "target {0} is refused")
    @ValueSource(
        strings = {
          "123", // bare id, not a reference object
          "{\"value\":123}", // no relationTo
          "{\"relationTo\":\"instruments\"}", // no value
          "{\"relationTo\":\"maintenances\",\"value\":1}", // resource type not accepted
          "{\"relationTo\":\"instruments\",\"value\":999999999}", // no such target
        })
    @DisplayName("a malformed or unresolvable reference is a validation error naming the field")
    void aMalformedReferenceIsAValidationError(String target) throws Exception {
      mockMvc
          .perform(
              post(CONFIGURATIONS)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"enabled\":true,\"target\":%s}".formatted(target)))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
          .andExpect(jsonPath("$.invalidParams[?(@.name == 'target')]").exists());
    }

    /**
     * A target the writer cannot read must fail exactly like one that does not exist, or the error
     * itself becomes an existence oracle.
     */
    @Test
    @DisplayName("a globalId that disagrees with relationTo and value is refused")
    void aGlobalIdThatDisagreesIsRefused() throws Exception {
      mockMvc
          .perform(
              post(CONFIGURATIONS)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"enabled":true,"target":{"relationTo":"booking-instruments","value":%d,"globalId":"IN%d"}}\
                      """
                          .formatted(instrumentId, instrumentId + 7)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.invalidParams[?(@.name == 'target')]").exists());
    }

    @Test
    @DisplayName("the target cannot be changed on update")
    void theTargetCannotBeChangedOnUpdate() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");

      mockMvc
          .perform(
              patch(CONFIGURATIONS + "/" + id)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"target\":\"IN%d\"}".formatted(instrumentId)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.invalidParams[?(@.name == 'target')]").exists());
    }

    @Test
    @DisplayName("the global-id shorthand is not accepted on create")
    void theGlobalIdShorthandIsNotAcceptedOnCreate() throws Exception {
      mockMvc
          .perform(
              post(CONFIGURATIONS)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"enabled\":true,\"target\":\"IN%d\"}".formatted(instrumentId)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.invalidParams[?(@.name == 'target')]").exists());
    }

    @Test
    @DisplayName("an expanded target document is not accepted as write input")
    void anExpandedTargetDocumentIsNotAcceptedAsInput() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");

      mockMvc
          .perform(
              patch(CONFIGURATIONS + "/" + id)
                  .header("apiKey", sysadminKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"target":{"relationTo":"booking-instruments","value":{"id":%d,"name":"x"}}}\
                      """
                          .formatted(instrumentId)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.invalidParams[?(@.name == 'target')]").exists());
    }
  }

  @Nested
  @DisplayName("filtering through a relationship")
  class FilteringThroughARelationship {

    /**
     * Compiles to a correlated {@code EXISTS} that carries the target's own read rule. Only a real
     * database can show that Blaze accepts the subquery and that the rule is inside it.
     */
    @Test
    @DisplayName("a filter on a target field selects through the relationship")
    void aFilterOnATargetFieldSelects() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");

      mockMvc
          .perform(
              get(CONFIGURATIONS)
                  .header("apiKey", fixture.userKey())
                  .param("where", "target.name=contains=" + fixture.marker()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").value(1))
          .andExpect(jsonPath("$.docs[0].id").value(id));
    }

    @Test
    @DisplayName("a target filter uses the safe Booking projection without Inventory access")
    void aTargetFilterUsesTheSafeBookingProjection() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");

      mockMvc
          .perform(
              get(CONFIGURATIONS)
                  .header("apiKey", fixture.otherUserKey())
                  .param("where", "target.name=contains=" + fixture.marker()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").value(1))
          .andExpect(jsonPath("$.docs[0].id").value(id));
    }

    @Test
    @DisplayName("a filter on an unknown target field is refused")
    void aFilterOnAnUnknownTargetFieldIsRefused() throws Exception {
      mockMvc
          .perform(
              get(CONFIGURATIONS)
                  .header("apiKey", fixture.userKey())
                  .param("where", "target.nosuchfield==1"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }
  }

  @Nested
  @DisplayName("the current-user relationship alias")
  class CurrentUserRelationshipAlias {

    @Test
    @DisplayName("list and count resolve createdBy.value me to the authenticated user")
    void listAndCountResolveValueToTheAuthenticatedUser() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");
      String where = "id==%d;createdBy.value==me".formatted(id);

      mockMvc
          .perform(get(CONFIGURATIONS).header("apiKey", sysadminKey).param("where", where))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").value(1))
          .andExpect(jsonPath("$.docs[0].id").value(id));

      mockMvc
          .perform(
              get(CONFIGURATIONS + "/count").header("apiKey", sysadminKey).param("where", where))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").value(1));
    }

    @Test
    @DisplayName("createdBy.id accepts me and resolves it for each authenticated user")
    void idAliasResolvesForEachAuthenticatedUser() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");
      String where = "id==%d;createdBy.id==me".formatted(id);

      mockMvc
          .perform(get(CONFIGURATIONS).header("apiKey", sysadminKey).param("where", where))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").value(1))
          .andExpect(jsonPath("$.docs[0].id").value(id));

      mockMvc
          .perform(get(CONFIGURATIONS).header("apiKey", fixture.userKey()).param("where", where))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").value(0));
    }

    @Test
    @DisplayName("bulk update accepts me on a relationship value")
    void bulkUpdateAcceptsValueAlias() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");

      mockMvc
          .perform(
              patch(CONFIGURATIONS)
                  .header("apiKey", sysadminKey)
                  .param("where", "id==%d;createdBy.value==me".formatted(id))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"enabled\":false}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.docs", Matchers.hasSize(1)))
          .andExpect(jsonPath("$.docs[0].id").value(id))
          .andExpect(jsonPath("$.docs[0].enabled").value(false));
    }

    @Test
    @DisplayName("bulk delete accepts me on a relationship id")
    void bulkDeleteAcceptsIdAlias() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");

      mockMvc
          .perform(
              delete(CONFIGURATIONS)
                  .header("apiKey", sysadminKey)
                  .param("where", "id==%d;createdBy.id==me".formatted(id)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.docs", Matchers.hasSize(1)))
          .andExpect(jsonPath("$.docs[0].id").value(id));

      mockMvc
          .perform(get(CONFIGURATIONS + "/" + id).header("apiKey", sysadminKey))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("discovered audit fields and the default audit routes")
  class Audit {

    /** {@code auditFields = true}, and a {@code User} property becomes a relationship to users. */
    @Test
    @DisplayName("audit fields are published without being declared in the record")
    void auditFieldsArePublished() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");

      mockMvc
          .perform(get(CONFIGURATIONS + "/" + id).header("apiKey", sysadminKey))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.createdAt").isString())
          .andExpect(jsonPath("$.updatedAt").isString())
          .andExpect(jsonPath("$.createdBy.relationTo").value("users"))
          .andExpect(jsonPath("$.createdBy.value").isNumber());
    }

    @Test
    @DisplayName("a discovered audit date is filterable and sortable")
    void aDiscoveredAuditDateIsQueryable() throws Exception {
      fixture.bookingConfiguration(instrumentId, "UTC");

      mockMvc
          .perform(
              get(CONFIGURATIONS)
                  .header("apiKey", sysadminKey)
                  .param("where", "createdAt=ge=1970-01-01T00:00:00Z")
                  .param("sort", "-createdAt,id"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.docs").isArray());
    }

    @Test
    @DisplayName("the audit route binds and reuses a daily snapshot")
    void theAuditRouteBindsAndReusesADailySnapshot() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");
      writeAuditEvent(id);

      MvcResult first =
          mockMvc
              .perform(get(CONFIGURATIONS + "/" + id + "/audit").header("apiKey", sysadminKey))
              .andExpect(status().isOk())
              .andExpect(
                  header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("private")))
              .andExpect(
                  header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("no-store")))
              .andExpect(jsonPath("$.docs[0].eventId").isString())
              .andExpect(jsonPath("$.snapshotDate").isString())
              .andExpect(jsonPath("$.snapshotFingerprint").isString())
              .andExpect(jsonPath("$.totalDocs").isNumber())
              .andExpect(jsonPath("$.page").value(1))
              .andExpect(jsonPath("$.hasPrevPage").value(false))
              .andReturn();

      JsonNode firstPage =
          com.fasterxml.jackson.databind.json.JsonMapper.builder()
              .build()
              .readTree(first.getResponse().getContentAsString());
      String snapshotDate = firstPage.path("snapshotDate").asText();
      String snapshotFingerprint = firstPage.path("snapshotFingerprint").asText();
      assertTrue(firstPage.path("docs").path(0).path("eventId").asText().matches("^[0-9a-f]{64}$"));
      assertTrue(snapshotDate.matches("^\\d{4}-\\d{2}-\\d{2}$"));
      assertTrue(snapshotFingerprint.matches("^[0-9a-f]{64}$"));

      mockMvc
          .perform(
              get(CONFIGURATIONS + "/" + id + "/audit")
                  .header("apiKey", sysadminKey)
                  .param("page", "2")
                  .param("limit", "1")
                  .param("snapshotDate", snapshotDate)
                  .param("snapshotFingerprint", snapshotFingerprint))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.snapshotDate").value(snapshotDate))
          .andExpect(jsonPath("$.snapshotFingerprint").value(snapshotFingerprint))
          .andExpect(jsonPath("$.page").value(2));

      mockMvc
          .perform(get(CONFIGURATIONS + "/" + id + "/audit/count").header("apiKey", sysadminKey))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").isNumber());
    }

    @Test
    @DisplayName("malformed and one-sided snapshot parameters are localized client errors")
    void malformedAndOneSidedSnapshotsAreRefused() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");

      mockMvc
          .perform(
              get(CONFIGURATIONS + "/" + id + "/audit")
                  .header("apiKey", sysadminKey)
                  .param("snapshotDate", "not-a-date")
                  .param("snapshotFingerprint", "not-a-fingerprint"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("errors.api.v2.audit.snapshot.invalid"));

      mockMvc
          .perform(
              get(CONFIGURATIONS + "/" + id + "/audit")
                  .header("apiKey", sysadminKey)
                  .param("snapshotDate", "2026-08-25"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("errors.api.v2.audit.snapshot.invalid"));
    }

    @Test
    @DisplayName("the audit route requires credentials")
    void theAuditRouteRequiresCredentials() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");

      mockMvc
          .perform(get(CONFIGURATIONS + "/" + id + "/audit"))
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }

    @Test
    @DisplayName("the audit route answers 404 for a row the caller cannot read")
    void theAuditRouteHidesUnreadableRows() throws Exception {
      mockMvc
          .perform(get(CONFIGURATIONS + "/999999999/audit").header("apiKey", fixture.userKey()))
          .andExpect(status().isNotFound());
    }

    /**
     * A refusal, not a clamp. The search used to answer 200 for a six-year window and return 183
     * days with nothing saying so, so a client paginating "2020 to 2026" to exhaustion believed it
     * had the whole period. Returning a different answer to the question asked is worse than
     * refusing the question.
     */
    @Test
    @DisplayName("an audit window wider than the permitted span is refused, not silently clamped")
    void anAuditWindowWiderThanThePermittedSpanIsRefused() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");

      mockMvc
          .perform(
              get(CONFIGURATIONS + "/" + id + "/audit")
                  .header("apiKey", sysadminKey)
                  .param("dateFrom", "2020-01-01T00:00:00Z")
                  .param("dateTo", "2026-01-01T00:00:00Z"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("errors.api.v2.audit.range.tooWide"));
    }

    /** A window inside the limit is served normally. */
    @Test
    @DisplayName("an audit window inside the permitted span is served")
    void anAuditWindowInsideThePermittedSpanIsServed() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");

      mockMvc
          .perform(
              get(CONFIGURATIONS + "/" + id + "/audit")
                  .header("apiKey", sysadminKey)
                  .param("dateFrom", "2026-01-01T00:00:00Z")
                  .param("dateTo", "2026-03-01T00:00:00Z"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").isNumber());
    }

    /** An inverted window is a genuine client error and is refused. */
    @Test
    @DisplayName("an audit window whose start is after its end is refused")
    void anInvertedAuditWindowIsRefused() throws Exception {
      long id = fixture.bookingConfiguration(instrumentId, "UTC");

      mockMvc
          .perform(
              get(CONFIGURATIONS + "/" + id + "/audit")
                  .header("apiKey", sysadminKey)
                  .param("dateFrom", "2026-06-01T00:00:00Z")
                  .param("dateTo", "2026-01-01T00:00:00Z"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }
  }

  private static void writeAuditEvent(long configurationId) throws IOException {
    LocalDate completedUtcDay = LocalDate.now(ZoneOffset.UTC);
    LocalDateTime localTimestamp =
        completedUtcDay
            .atTime(12, 0)
            .atZone(ZoneOffset.UTC)
            .withZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime();
    String line =
        "%s - domain:UNKNOWN action:CREATE [{\"data\":{\"id\":\"booking-configurations:%d\",\"enabled\":true}}] sysadmin1(System Admin)%n"
            .formatted(AUDIT_TIMESTAMP.format(localTimestamp), configurationId);
    Files.writeString(AUDIT_DIRECTORY.resolve("RSLogs.txt"), line, StandardCharsets.UTF_8);
  }
}
