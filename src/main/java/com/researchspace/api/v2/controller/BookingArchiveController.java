package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2ResourceException;
import com.researchspace.booking.service.BookingArchiveManager;
import com.researchspace.booking.service.BookingConcurrentModificationException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Narrow archived lifecycle endpoints that do not expose the generic configuration document. */
@RestController
@RequestMapping("/api/v2/booking-configurations/{configurationId}")
public class BookingArchiveController {

  public record ArchivedBookingDocument(
      long id, String start, String end, long version, boolean canEdit, boolean canCancel) {}

  public record ArchivedSummaryDocument(
      long id,
      long version,
      BookingArchiveManager.Target target,
      boolean canUnarchive,
      boolean canCancelBookings,
      boolean calendarSubscriptionActive,
      List<ArchivedBookingDocument> futureBookings) {
    static ArchivedSummaryDocument from(BookingArchiveManager.Summary summary) {
      return new ArchivedSummaryDocument(
          summary.id(),
          summary.version(),
          summary.target(),
          summary.canUnarchive(),
          summary.canCancelBookings(),
          summary.calendarSubscriptionActive(),
          summary.futureBookings().stream()
              .map(
                  booking ->
                      new ArchivedBookingDocument(
                          booking.id(),
                          DateTimeFormatter.ISO_INSTANT.format(booking.start().toInstant()),
                          DateTimeFormatter.ISO_INSTANT.format(booking.end().toInstant()),
                          booking.version(),
                          booking.canEdit(),
                          booking.canCancel()))
              .toList());
    }
  }

  private final BookingArchiveManager manager;

  public BookingArchiveController(BookingArchiveManager manager) {
    this.manager = manager;
  }

  @GetMapping("/archived-summary")
  public ResponseEntity<ArchivedSummaryDocument> summary(
      @PathVariable Long configurationId,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    ArchivedSummaryDocument document =
        ArchivedSummaryDocument.from(manager.summary(configurationId, caller.subject()));
    return ResponseEntity.ok().eTag(Long.toString(document.version())).body(document);
  }

  @PostMapping("/unarchive")
  public ResponseEntity<Void> unarchive(
      @PathVariable Long configurationId,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    try {
      manager.unarchive(
          configurationId,
          ApiV2ConditionalRequest.parseVersion(
              ifMatch, "errors.api.v2.bookingConfiguration.ifMatchRequired"),
          caller.subject(),
          caller.actor());
      return ResponseEntity.noContent().build();
    } catch (BookingConcurrentModificationException ex) {
      throw ApiV2ResourceException.of(
          HttpStatus.PRECONDITION_FAILED,
          "errors.api.v2.bookingConfiguration.concurrentModification");
    }
  }
}
