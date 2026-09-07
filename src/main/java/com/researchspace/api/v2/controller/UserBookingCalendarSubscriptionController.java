package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.service.BookingCalendarManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API v2 transport for the caller's user-wide booking calendar subscription. */
@RestController
@RequestMapping("/api/v2/users/me/booking-calendar-subscription")
public class UserBookingCalendarSubscriptionController {

  /** Current subscription document for the authenticated owner. */
  public record StatusDocument(boolean active, String updatedAt, String subscriptionUrl) {

    static StatusDocument from(BookingCalendarManager.Status status) {
      return new StatusDocument(
          status.active(), isoTimestamp(status.updatedAt()), status.subscriptionUrl());
    }
  }

  /** One-time creation response containing the newly issued subscription URL. */
  public record CreatedDocument(boolean active, String updatedAt, String subscriptionUrl) {

    static CreatedDocument from(BookingCalendarManager.Created created) {
      return new CreatedDocument(
          created.status().active(),
          isoTimestamp(created.status().updatedAt()),
          created.subscriptionUrl());
    }
  }

  private final BookingCalendarManager manager;

  public UserBookingCalendarSubscriptionController(BookingCalendarManager manager) {
    this.manager = manager;
  }

  private static String isoTimestamp(Date value) {
    return value == null ? null : DateTimeFormatter.ISO_INSTANT.format(value.toInstant());
  }

  @GetMapping
  @Operation(
      operationId = "getUserBookingCalendarSubscription",
      summary = "Get the caller's booking calendar subscription",
      responses = {
        @ApiResponse(responseCode = "200", description = "Current subscription status."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Booking is unavailable.")
      })
  public ResponseEntity<StatusDocument> get(
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    BookingCalendarManager.Status status = manager.userStatus(caller.subject(), caller.actor());
    return ResponseEntity.ok().eTag(status.etag()).body(StatusDocument.from(status));
  }

  @PostMapping
  @Operation(
      operationId = "createOrReplaceUserBookingCalendarSubscription",
      summary = "Create or replace the caller's booking calendar subscription",
      responses = {
        @ApiResponse(responseCode = "200", description = "The new subscription URL."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Booking is unavailable.")
      })
  public ResponseEntity<CreatedDocument> createOrReplace(
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    String expectedEtag =
        ApiV2ConditionalRequest.parseStrongEtag(
            ifMatch, "errors.api.v2.bookingCalendar.ifMatchRequired");
    BookingCalendarManager.Created created =
        manager.createOrRotateUser(caller.subject(), caller.actor(), expectedEtag);
    return ResponseEntity.ok().eTag(created.status().etag()).body(CreatedDocument.from(created));
  }

  @DeleteMapping
  @Operation(
      operationId = "revokeUserBookingCalendarSubscription",
      summary = "Revoke the caller's booking calendar subscription",
      responses = {
        @ApiResponse(responseCode = "204", description = "The subscription is inactive."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Booking is unavailable.")
      })
  public ResponseEntity<Void> revoke(
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    manager.revokeUser(caller.subject(), caller.actor());
    return ResponseEntity.noContent().build();
  }
}
