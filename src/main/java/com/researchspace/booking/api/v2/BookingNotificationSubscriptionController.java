package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ApiV2ConditionalRequest;
import com.researchspace.booking.service.BookingNotificationSubscriptionManager;
import com.researchspace.booking.service.BookingNotificationSubscriptionManager.PreferenceChanges;
import com.researchspace.booking.service.BookingNotificationSubscriptionManager.Preferences;
import com.researchspace.booking.service.BookingNotificationSubscriptionManager.Status;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Current-user notification defaults and personal instrument subscriptions. */
@RestController
@RequestMapping("/api/v2")
@Tag(
    name = "booking-configurations",
    description = "Bookable resource configurations and related subscription settings.",
    extensions =
        @Extension(properties = @ExtensionProperty(name = "x-tag-group", value = "Booking")))
@Tag(
    name = "users",
    description = "User accounts, profiles and personal settings.",
    extensions =
        @Extension(properties = @ExtensionProperty(name = "x-tag-group", value = "Platform")))
public class BookingNotificationSubscriptionController {
  private static final String PREFERENCES = "/users/me/booking-notification-preferences";
  private static final String SUBSCRIPTIONS = "/users/me/booking-notification-subscriptions";
  private static final String ITEM = "/booking-configurations/{id}/notification-subscription";

  public record PreferenceReplacement(
      @NotNull Boolean autoSubscribeOwnedItems,
      @NotNull Boolean notifyOwnActions,
      @NotNull Boolean notifyOnCreated,
      @NotNull Boolean notifyOnCancelled) {
    PreferenceChanges changes() {
      return new PreferenceChanges(
          autoSubscribeOwnedItems, notifyOwnActions, notifyOnCreated, notifyOnCancelled);
    }
  }

  public record SubscriptionReplacement(@NotNull Boolean enabled) {}

  public record Lookup(
      @NotNull @Size(min = 1, max = 100) List<@NotNull @Positive Long> configurationIds) {}

  public record SubscriptionStatus(
      long configurationId,
      boolean enabled,
      boolean createdEnabled,
      boolean cancelledEnabled,
      boolean emailEnabled) {
    static SubscriptionStatus from(Status status) {
      return new SubscriptionStatus(
          status.configurationId(),
          status.enabled(),
          status.createdEnabled(),
          status.cancelledEnabled(),
          status.emailEnabled());
    }
  }

  public record StatusList(List<SubscriptionStatus> docs) {}

  public record UnsubscribeResult(int updatedCount) {}

  private final BookingNotificationSubscriptionManager manager;

  public BookingNotificationSubscriptionController(BookingNotificationSubscriptionManager manager) {
    this.manager = manager;
  }

  @GetMapping(PREFERENCES)
  @Operation(
      operationId = "getMyBookingNotificationPreferences",
      summary = "Get the caller's booking notification preferences",
      description =
          "Returns the default for new owned bookable instruments, the booking created and"
              + " cancelled event preferences shared with My Profile, and the read-only effective"
              + " My Profile email delivery preference.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Current booking notification preferences.")
      })
  public Preferences preferences(
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return manager.getPreferences(caller.subject(), caller.actor());
  }

  @PutMapping(value = PREFERENCES, consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "replaceMyBookingNotificationPreferences",
      summary = "Replace the caller's booking notification preferences",
      description =
          "Replaces the automatic subscription default and both booking event preferences. All"
              + " four are required. Existing subscriptions and email delivery are unchanged.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Saved booking notification preferences.")
      })
  public Preferences replacePreferences(
      @Valid @RequestBody PreferenceReplacement body,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return manager.replacePreferences(body.changes(), caller.subject(), caller.actor());
  }

  @GetMapping(ITEM)
  @Operation(
      operationId = "getMyBookingNotificationSubscription",
      summary = "Get the caller's personal subscription and effective event delivery",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Current personal subscription.",
            headers = {
              @Header(
                  name = "Cache-Control",
                  schema = @Schema(types = "string", allowableValues = "no-transform")),
              @Header(name = "ETag", ref = "#/components/headers/ETag")
            })
      })
  public ResponseEntity<SubscriptionStatus> get(
      @PathVariable long id,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return versioned(manager.get(id, caller.subject(), caller.actor()));
  }

  @PutMapping(value = ITEM, consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "replaceMyBookingNotificationSubscription",
      summary = "Update the caller's personal subscription with optimistic concurrency",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Saved personal subscription.",
            headers = {
              @Header(
                  name = "Cache-Control",
                  schema = @Schema(types = "string", allowableValues = "no-transform")),
              @Header(name = "ETag", ref = "#/components/headers/ETag")
            }),
        @ApiResponse(
            responseCode = "412",
            description = "The subscription changed since it was read."),
        @ApiResponse(responseCode = "400", description = "The validator is malformed."),
        @ApiResponse(responseCode = "428", description = "If-Match is required.")
      })
  public ResponseEntity<SubscriptionStatus> replace(
      @PathVariable long id,
      @Valid @RequestBody SubscriptionReplacement body,
      @Parameter(required = true, description = "ETag from the subscription GET.")
          @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
          String ifMatch,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    String expectedTag =
        ApiV2ConditionalRequest.parseStrongEtag(
            ifMatch, "errors.api.v2.bookingNotifications.ifMatchRequired");
    return versioned(
        manager.replace(id, body.enabled(), expectedTag, caller.subject(), caller.actor()));
  }

  @PostMapping(value = SUBSCRIPTIONS + "/lookup", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "lookupMyBookingNotificationSubscriptions",
      summary = "Read personal subscriptions for up to 100 readable instruments",
      responses = {
        @ApiResponse(responseCode = "200", description = "Current personal subscriptions.")
      })
  public StatusList lookup(
      @Valid @RequestBody Lookup body,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return new StatusList(
        manager.getMany(body.configurationIds(), caller.subject(), caller.actor()).stream()
            .map(SubscriptionStatus::from)
            .toList());
  }

  private static ResponseEntity<SubscriptionStatus> versioned(Status status) {
    return ResponseEntity.ok()
        .eTag(BookingNotificationSubscriptionManager.etag(status))
        .header(HttpHeaders.CACHE_CONTROL, "no-transform")
        .body(SubscriptionStatus.from(status));
  }

  @DeleteMapping(SUBSCRIPTIONS)
  @Operation(
      operationId = "unsubscribeMyBookingNotifications",
      summary =
          "Unsubscribe from all instruments without changing the automatic subscription default",
      responses = {
        @ApiResponse(responseCode = "200", description = "Number of subscriptions disabled.")
      })
  public UnsubscribeResult unsubscribeAll(
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return new UnsubscribeResult(manager.unsubscribeAll(caller.subject(), caller.actor()));
  }
}
