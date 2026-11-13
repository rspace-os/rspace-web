package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ApiV2ConditionalRequest;
import com.researchspace.booking.api.v2.BookingCalendarSubscriptionController.CreatedDocument;
import com.researchspace.booking.api.v2.BookingCalendarSubscriptionController.StatusDocument;
import com.researchspace.booking.service.BookingCalendarManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(
    name = "users",
    description = "User accounts, profiles and personal settings.",
    extensions =
        @Extension(properties = @ExtensionProperty(name = "x-tag-group", value = "Platform")))
public class UserBookingCalendarSubscriptionController {

  private final BookingCalendarManager manager;

  public UserBookingCalendarSubscriptionController(BookingCalendarManager manager) {
    this.manager = manager;
  }

  @Operation(
      tags = "booking-calendar-subscriptions",
      operationId = "getUserBookingCalendarSubscription",
      summary = "Get the caller's booking calendar subscription",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Current subscription status.",
            headers = {
              @Header(
                  name = "Cache-Control",
                  schema = @Schema(types = "string", allowableValues = "private, no-store")),
              @Header(name = "ETag", ref = "#/components/headers/ETag")
            }),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "404", description = "Booking is unavailable.")
      })
  public ResponseEntity<StatusDocument> get(
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    BookingCalendarManager.Status status = manager.userStatus(caller.subject(), caller.actor());
    return ResponseEntity.ok().eTag(status.etag()).body(StatusDocument.from(status));
  }

  @Operation(
      tags = "booking-calendar-subscriptions",
      responses = {
        @ApiResponse(
            responseCode = "200",
            headers = {
              @Header(
                  name = "Cache-Control",
                  schema = @Schema(types = "string", allowableValues = "private, no-store")),
              @Header(name = "ETag", ref = "#/components/headers/ETag")
            }),
        @ApiResponse(
            responseCode = "406",
            description = "The requested representation is unavailable."),
        @ApiResponse(responseCode = "429", description = "Too many requests."),
        @ApiResponse(responseCode = "500", description = "Unexpected server error."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
      })
      @Parameter(required = true, description = "Strong ETag from the latest subscription status.")
          @RequestHeader(name = "If-Match", required = false)
          String ifMatch,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    String expectedEtag =
        ApiV2ConditionalRequest.parseStrongEtag(
            ifMatch, "errors.api.v2.bookingCalendar.ifMatchRequired");
  }

  @Operation(
      tags = "booking-calendar-subscriptions",
      operationId = "revokeUserBookingCalendarSubscription",
      summary = "Revoke the caller's booking calendar subscription",
      responses = {
        @ApiResponse(
            responseCode = "204",
            description = "The subscription is inactive.",
            headers =
                @Header(
                    name = "Cache-Control",
                    schema = @Schema(types = "string", allowableValues = "private, no-store"))),
        @ApiResponse(
            responseCode = "406",
            description = "The requested representation is unavailable."),
        @ApiResponse(responseCode = "429", description = "Too many requests."),
        @ApiResponse(responseCode = "500", description = "Unexpected server error."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "404", description = "Booking is unavailable.")
      })
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
  }
}
