package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.service.BookingCalendarManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Positive;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API v2 transport for one caller's bookable-item calendar subscription. */
@RestController
@Validated
@RequestMapping("/api/v2/booking-configurations/{configurationId}/calendar-subscription")
public class BookingCalendarSubscriptionController {

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

  public BookingCalendarSubscriptionController(BookingCalendarManager manager) {
    this.manager = manager;
  }

  private static String isoTimestamp(Date value) {
    return value == null ? null : DateTimeFormatter.ISO_INSTANT.format(value.toInstant());
  }

  @GetMapping
  @Operation(
      operationId = "getBookingCalendarSubscription",
      summary = "Get calendar subscription status",
      description = "Returns the caller's current subscription URL when one exists.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Current subscription status."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Booking is unavailable."),
        @ApiResponse(responseCode = "404", description = "The bookable item was not found.")
      })
  public StatusDocument get(
      @PathVariable @Positive(message = "{errors.api.v2.invalidRequest}") Long configurationId,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return StatusDocument.from(manager.status(configurationId, caller.subject(), caller.actor()));
  }

  @PostMapping
  @Operation(
      operationId = "createOrReplaceBookingCalendarSubscription",
      summary = "Create or replace a calendar subscription",
      description = "Replaces any active credential and returns the new subscription URL.",
      responses = {
        @ApiResponse(responseCode = "200", description = "The new subscription URL."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Booking is unavailable."),
        @ApiResponse(responseCode = "404", description = "The bookable item was not found."),
        @ApiResponse(responseCode = "409", description = "The bookable item is archived.")
      })
  public CreatedDocument createOrReplace(
      @PathVariable @Positive(message = "{errors.api.v2.invalidRequest}") Long configurationId,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return CreatedDocument.from(
        manager.createOrRotate(configurationId, caller.subject(), caller.actor()));
  }

  @DeleteMapping
  @Operation(
      operationId = "revokeBookingCalendarSubscription",
      summary = "Revoke a calendar subscription",
      responses = {
        @ApiResponse(responseCode = "204", description = "The subscription is inactive."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Booking is unavailable."),
        @ApiResponse(responseCode = "404", description = "The bookable item was not found.")
      })
  public ResponseEntity<Void> revoke(
      @PathVariable @Positive(message = "{errors.api.v2.invalidRequest}") Long configurationId,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    manager.revoke(configurationId, caller.subject(), caller.actor());
    return ResponseEntity.noContent().build();
  }
}
