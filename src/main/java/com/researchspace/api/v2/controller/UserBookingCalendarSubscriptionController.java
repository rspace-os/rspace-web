package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.service.BookingCalendarManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API v2 transport for the caller's own booking calendar links. */
@RestController
@RequestMapping("/api/v2/users/me")
public class UserBookingCalendarSubscriptionController {

  private static final String SUBSCRIPTION = "/booking-calendar-subscription";
  private static final String ITEM_SUBSCRIPTIONS = "/bookable-item-calendar-subscriptions";

  /** Current subscription document for the authenticated owner. */
  public record StatusDocument(boolean active, String updatedAt, String subscriptionUrl) {

    static StatusDocument from(BookingCalendarManager.Status status) {
      return new StatusDocument(
          status.active(), isoTimestamp(status.updatedAt()), status.subscriptionUrl());
    }
  }

  /** Create or rotate response containing the caller's current subscription URL. */
  public record CreatedDocument(boolean active, String updatedAt, String subscriptionUrl) {

    static CreatedDocument from(BookingCalendarManager.Created created) {
      return new CreatedDocument(
          created.status().active(),
          isoTimestamp(created.status().updatedAt()),
          created.subscriptionUrl());
    }
  }

  /** One of the caller's bookable-item calendar links. */
  public record ItemLinkDocument(
      long configurationId,
      String itemGlobalId,
      String itemName,
      String updatedAt,
      String subscriptionUrl) {

    static ItemLinkDocument from(BookingCalendarManager.ItemLink link) {
      return new ItemLinkDocument(
          link.configurationId(),
          "IN" + link.targetId(),
          link.itemName(),
          isoTimestamp(link.updatedAt()),
          link.subscriptionUrl());
    }
  }

  private final BookingCalendarManager manager;

  public UserBookingCalendarSubscriptionController(BookingCalendarManager manager) {
    this.manager = manager;
  }

  private static String isoTimestamp(Date value) {
    return value == null ? null : DateTimeFormatter.ISO_INSTANT.format(value.toInstant());
  }

  @GetMapping(SUBSCRIPTION)
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

  @PostMapping(SUBSCRIPTION)
  @Operation(
      operationId = "createUserBookingCalendarSubscription",
      summary = "Create the caller's booking calendar subscription",
      description =
          "Returns the caller's existing subscription URL unchanged, or issues one when none"
              + " exists. Repeating the request is safe. Use the rotate operation to replace a"
              + " URL.",
      responses = {
        @ApiResponse(responseCode = "200", description = "The existing subscription URL."),
        @ApiResponse(responseCode = "201", description = "A newly issued subscription URL."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Booking is unavailable.")
      })
  public ResponseEntity<CreatedDocument> create(
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    BookingCalendarManager.Created created = manager.createUser(caller.subject(), caller.actor());
    return ResponseEntity.status(created.newlyIssued() ? HttpStatus.CREATED : HttpStatus.OK)
        .eTag(created.status().etag())
        .body(CreatedDocument.from(created));
  }

  @PostMapping(SUBSCRIPTION + "/rotate")
  @Operation(
      operationId = "rotateUserBookingCalendarSubscription",
      summary = "Replace the caller's booking calendar subscription URL",
      description =
          "Requires the current status ETag in If-Match. Issues a new URL; calendars using the old"
              + " one stop updating.",
      responses = {
        @ApiResponse(responseCode = "200", description = "The new subscription URL."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Booking is unavailable."),
        @ApiResponse(
            responseCode = "409",
            description = "The subscription is missing or changed since it was read."),
        @ApiResponse(responseCode = "428", description = "If-Match is required.")
      })
  public ResponseEntity<CreatedDocument> rotate(
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    String expectedEtag =
        ApiV2ConditionalRequest.parseStrongEtag(
            ifMatch, "errors.api.v2.bookingCalendar.ifMatchRequired");
    BookingCalendarManager.Created rotated =
        manager.rotateUser(caller.subject(), caller.actor(), expectedEtag);
    return ResponseEntity.ok().eTag(rotated.status().etag()).body(CreatedDocument.from(rotated));
  }

  @DeleteMapping(SUBSCRIPTION)
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

  @GetMapping(ITEM_SUBSCRIPTIONS)
  @Operation(
      operationId = "listMyBookableItemCalendarSubscriptions",
      summary = "List the caller's bookable-item calendar links",
      description =
          "Returns each active bookable item the caller still has a calendar link for and can"
              + " read, with its current URL, ordered by item name.",
      responses = {
        @ApiResponse(responseCode = "200", description = "The caller's item links."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Booking is unavailable.")
      })
  public List<ItemLinkDocument> itemLinks(
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return manager.itemLinks(caller.subject(), caller.actor()).stream()
        .map(ItemLinkDocument::from)
        .toList();
  }
}
