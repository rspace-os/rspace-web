package com.researchspace.webapp.controller;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditData;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import com.researchspace.testutils.TestFactory;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

public class BookingAuditTrailCsvGeneratorTest {
  AuditTrailSearchResultCsvGenerator auditTrailSearchResultCsvGenerator =
      new AuditTrailSearchResultCsvGenerator();

  @BeforeEach
  public void setUp() {
    ReflectionTestUtils.setField(
        auditTrailSearchResultCsvGenerator,
        "messages",
        new MessageSourceUtils(new JsonMessageSource()));
  }

  User anyUser = TestFactory.createAnyUser("any");

  @Test
  public void bookingCsvUsesReadableTypeAndActionLabels() throws IOException {
    AuditDomain bookingDomain = AuditDomain.BOOKING;
    ISearchResults<AuditTrailSearchResult> results =
        new SearchResultsImpl<>(
            toList(
                createAuditSearchResult(
                    bookingDomain, AuditAction.WRITE, "bookings:11", "CANCELLED", null),
                createAuditSearchResult(
                    bookingDomain,
                    AuditAction.DELETE,
                    "booking-configurations:12",
                    "ARCHIVED",
                    null),
                createAuditSearchResult(
                    bookingDomain,
                    AuditAction.DELETE,
                    "booking-configurations:13",
                    "ARCHIVED",
                    "permanent=true"),
                createAuditSearchResult(
                    AuditDomain.UNKNOWN, AuditAction.WRITE, "bookings:14", "CANCELLED", null)),
            0,
            4);

    ResponseEntity<String> csv =
        auditTrailSearchResultCsvGenerator.convertToCsv(results, defaultSearchConfig());

    assertEquals("Cancelled", getCellByRowColumn(csv, 2, 2));
    assertEquals("Booking", getCellByRowColumn(csv, 2, 3));
    assertEquals("Archived", getCellByRowColumn(csv, 3, 2));
    assertEquals("Booking", getCellByRowColumn(csv, 3, 3));
    assertEquals("Permanently deleted", getCellByRowColumn(csv, 4, 2));
    assertEquals("Booking", getCellByRowColumn(csv, 4, 3));
    assertEquals("Cancelled", getCellByRowColumn(csv, 5, 2));
    assertEquals("Booking", getCellByRowColumn(csv, 5, 3));
  }

  @ParameterizedTest
  @MethodSource("bookingCsvActionCases")
  public void bookingCsvLabelsActions(
      AuditDomain domain,
      AuditAction action,
      String id,
      String state,
      String description,
      String expected)
      throws IOException {
    AuditTrailSearchResult result = createAuditSearchResult(domain, action, id, state, description);

    ResponseEntity<String> csv =
        auditTrailSearchResultCsvGenerator.convertToCsv(
            singleResult(result), defaultSearchConfig());

    assertEquals(expected, getCellByRowColumn(csv, 2, 2));
  }

  private static Stream<Arguments> bookingCsvActionCases() {
    return Stream.of(
        Arguments.of(
            AuditDomain.BOOKING, AuditAction.CREATE, "bookings:21", "CONFIRMED", null, "Created"),
        Arguments.of(
            AuditDomain.BOOKING, AuditAction.WRITE, "bookings:21", "CONFIRMED", null, "Changed"),
        Arguments.of(
            AuditDomain.BOOKING,
            AuditAction.RESTORE,
            "booking-configurations:22",
            "ACTIVE",
            null,
            "Restored"),
        Arguments.of(
            AuditDomain.BOOKING, AuditAction.DELETE, "bookings:21", "CONFIRMED", null, "DELETE"),
        Arguments.of(AuditDomain.RECORD, AuditAction.RESTORE, "SD23", "ACTIVE", null, "RESTORE"));
  }

  @Test
  public void bookingNameIsTheRecordedNameTargetOrConfiguration() throws IOException {
    AuditTrailSearchResult deleted =
        createAuditSearchResult(
            AuditDomain.BOOKING,
            AuditAction.DELETE,
            "booking-configurations:24",
            "ARCHIVED",
            "permanent=true");
    deleted.getEvent().getData().getData().put("targetName", "Confocal microscope");
    deleted.getEvent().getData().getData().put("deletedAt", "2026-09-28T18:29:48.555718112Z");
    AuditTrailSearchResult configuration =
        createAuditSearchResult(
            AuditDomain.BOOKING, AuditAction.WRITE, "booking-configurations:25", "ACTIVE", null);
    configuration
        .getEvent()
        .getData()
        .getData()
        .put("target", Map.of("type", "INSTRUMENT", "id", 5));
    AuditTrailSearchResult booking =
        createAuditSearchResult(
            AuditDomain.BOOKING, AuditAction.WRITE, "bookings:26", "CONFIRMED", null);
    booking
        .getEvent()
        .getData()
        .getData()
        .put("bookingConfigurationId", "booking-configurations:25");
    AuditTrailSearchResult defaults =
        createAuditSearchResult(
            AuditDomain.BOOKING, AuditAction.WRITE, "booking-settings:1", null, null);

    ResponseEntity<String> csv =
        auditTrailSearchResultCsvGenerator.convertToCsv(
            new SearchResultsImpl<>(toList(deleted, configuration, booking, defaults), 0, 4),
            defaultSearchConfig());

    assertEquals("Confocal microscope", getCellByRowColumn(csv, 2, 5));
    assertEquals("IN5", getCellByRowColumn(csv, 3, 5));
    assertEquals("booking-configurations:25", getCellByRowColumn(csv, 4, 5));
    assertEquals("n/a", getCellByRowColumn(csv, 5, 5));
    assertThat(csv.getBody()).contains("Deleted at (UTC): 2026-09-28T18:29:48Z;");
    assertThat(csv.getBody()).doesNotContain("555718112");
  }

  @Test
  public void bookingDescriptionPutsRecordedDetailsBeforeTheLoggedDescription() throws IOException {
    AuditTrailSearchResult deleted =
        createAuditSearchResult(
            AuditDomain.BOOKING,
            AuditAction.DELETE,
            "booking-configurations:13",
            "ARCHIVED",
            "permanent=true; subject=user2b");
    deleted.getEvent().getData().getData().put("openDays", List.of(1, 3));

    ResponseEntity<String> csv =
        auditTrailSearchResultCsvGenerator.convertToCsv(
            singleResult(deleted), defaultSearchConfig());

    assertEquals("Permanently deleted", getCellByRowColumn(csv, 2, 2));
    assertThat(csv.getBody())
        .contains(
            "\"Status: ARCHIVED; Open days: Monday, Wednesday; permanent=true; subject=user2b\"");
  }

  @Test
  public void bookingStateDoesNotChangeNonBookingCsvSemantics() throws IOException {
    ISearchResults<AuditTrailSearchResult> results =
        singleResult(
            createAuditSearchResult(
                AuditDomain.RECORD, AuditAction.WRITE, "record:15", "CANCELLED", null));

    ResponseEntity<String> csv =
        auditTrailSearchResultCsvGenerator.convertToCsv(results, defaultSearchConfig());

    assertEquals("WRITE", getCellByRowColumn(csv, 2, 2));
    assertEquals("RECORD", getCellByRowColumn(csv, 2, 3));
  }

  @Test
  public void malformedLegacyBookingIdKeepsUnknownTypeAndRawAction() throws IOException {
    ISearchResults<AuditTrailSearchResult> results =
        singleResult(
            createAuditSearchResult(
                AuditDomain.UNKNOWN, AuditAction.WRITE, "bookings:evil", "CANCELLED", null));

    ResponseEntity<String> csv =
        auditTrailSearchResultCsvGenerator.convertToCsv(results, defaultSearchConfig());

    assertEquals("WRITE", getCellByRowColumn(csv, 2, 2));
    assertEquals("UNKNOWN", getCellByRowColumn(csv, 2, 3));
  }

  @Test
  public void bookingDeleteRequiresExactPermanentMarker() throws IOException {
    ISearchResults<AuditTrailSearchResult> results =
        singleResult(
            createAuditSearchResult(
                AuditDomain.BOOKING,
                AuditAction.DELETE,
                "booking-configurations:15",
                "ARCHIVED",
                "note=not-permanent=true"));

    ResponseEntity<String> csv =
        auditTrailSearchResultCsvGenerator.convertToCsv(results, defaultSearchConfig());

    assertEquals("Archived", getCellByRowColumn(csv, 2, 2));
  }

  private String getCellByRowColumn(ResponseEntity<String> results, int row, int column) {
    String csvLine = results.getBody().split("\\n")[row];
    String desc = csvLine.split(",")[column];
    return desc;
  }

  private AuditTrailUISearchConfig defaultSearchConfig() {
    return new AuditTrailUISearchConfig();
  }

  private AuditTrailSearchResult createAuditSearchResult(
      AuditDomain domain, AuditAction action, String id, String state, String description) {
    AuditData data = new AuditData();
    data.getData().put("id", id);
    if (state != null) {
      data.getData().put("state", state);
    }
    HistoricData historicData =
        new HistoricData(domain, action, anyUser.getFullName(), data, anyUser.getUniqueName());
    historicData.setDescription(description);
    return new AuditTrailSearchResult(historicData, Instant.now().toEpochMilli());
  }

  private ISearchResults<AuditTrailSearchResult> singleResult(AuditTrailSearchResult result) {
    return new SearchResultsImpl<>(toList(result), 0, 1);
  }
}
