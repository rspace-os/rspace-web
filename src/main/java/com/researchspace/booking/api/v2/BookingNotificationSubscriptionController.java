package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.service.BookingNotificationSubscriptionManager;
import com.researchspace.booking.service.BookingNotificationSubscriptionManager.Preferences;
import com.researchspace.booking.service.BookingNotificationSubscriptionManager.Status;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Current-user notification defaults and personal instrument subscriptions. */
@RestController
@RequestMapping("/api/v2")
public class BookingNotificationSubscriptionController {
  private static final String PREFERENCES = "/users/me/booking-notification-preferences";
  private static final String SUBSCRIPTIONS = "/users/me/booking-notification-subscriptions";
  private static final String ITEM = "/booking-configurations/{id}/notification-subscription";

  public record PreferenceReplacement(
      @NotNull Boolean autoSubscribeOwnedItems, @NotNull Boolean notifyOwnActions) {}

  public record SubscriptionReplacement(@NotNull Boolean enabled, @NotNull @Min(-1) Long version) {}

  public record Lookup(
      @NotNull @Size(min = 1, max = 100) List<@NotNull @Positive Long> configurationIds) {}

  public record BulkReplacement(
      @NotNull @Size(min = 1, max = 100) List<@NotNull @Positive Long> configurationIds,
      @NotNull Boolean enabled) {}

  public record StatusList(List<Status> docs) {}

  public record UnsubscribeResult(int updatedCount) {}

  private final BookingNotificationSubscriptionManager manager;

  public BookingNotificationSubscriptionController(BookingNotificationSubscriptionManager manager) {
    this.manager = manager;
  }

  @GetMapping(PREFERENCES)
  @Operation(
      operationId = "getMyBookingNotificationPreferences",
      summary = "Get the caller's booking notification preferences",
      responses = {
        @ApiResponse(responseCode = "200", description = "Current automatic subscription default.")
      })
  public Preferences preferences(
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return manager.getPreferences(caller.subject(), caller.actor());
  }

  @PutMapping(value = PREFERENCES, consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "replaceMyBookingNotificationPreferences",
      summary = "Change personal notification preferences without changing existing subscriptions",
      responses = {
        @ApiResponse(responseCode = "200", description = "Saved automatic subscription default.")
      })
  public Preferences replacePreferences(
      @Valid @RequestBody PreferenceReplacement body,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return manager.replacePreferences(
        body.autoSubscribeOwnedItems(), body.notifyOwnActions(), caller.subject(), caller.actor());
  }

  @GetMapping(ITEM)
  @Operation(
      operationId = "getMyBookingNotificationSubscription",
      summary = "Get the caller's personal subscription and effective event delivery",
      responses = {
        @ApiResponse(responseCode = "200", description = "Current personal subscription.")
      })
  public Status get(
      @PathVariable long id,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return manager.get(id, caller.subject(), caller.actor());
  }

  @PutMapping(value = ITEM, consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "replaceMyBookingNotificationSubscription",
      summary = "Update the caller's personal subscription with optimistic concurrency",
      responses = {
        @ApiResponse(responseCode = "200", description = "Saved personal subscription."),
        @ApiResponse(
            responseCode = "409",
            description = "The subscription changed since it was read.")
      })
  public Status replace(
      @PathVariable long id,
      @Valid @RequestBody SubscriptionReplacement body,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return manager.replace(id, body.enabled(), body.version(), caller.subject(), caller.actor());
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
        manager.getMany(body.configurationIds(), caller.subject(), caller.actor()));
  }

  @PutMapping(value = SUBSCRIPTIONS, consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "replaceMyBookingNotificationSubscriptions",
      summary = "Subscribe or unsubscribe from selected readable instruments",
      description =
          "Applies the explicit choice atomically to up to 100 instruments. All must be readable by"
              + " the caller. Existing defaults and global event preferences are preserved.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Saved personal subscriptions.")
      })
  public StatusList replaceMany(
      @Valid @RequestBody BulkReplacement body,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return new StatusList(
        manager.replaceMany(
            body.configurationIds(), body.enabled(), caller.subject(), caller.actor()));
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
