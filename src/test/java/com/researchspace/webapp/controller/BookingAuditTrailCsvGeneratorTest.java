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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
