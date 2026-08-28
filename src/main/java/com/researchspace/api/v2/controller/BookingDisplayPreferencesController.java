package com.researchspace.api.v2.controller;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.service.BookingDisplayPreferencesManager;
import com.researchspace.booking.service.BookingDisplayPreferencesManager.ResolvedBookingDisplayPreferences;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingTimezoneMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Current-user REST v2 interface for Booking display preferences. */
@RestController
@RequestMapping("/api/v2/users/me/booking-preferences")
public final class BookingDisplayPreferencesController {

  public record Replacement(
      @NotNull
          @Pattern(
              regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d",
              message = "{errors.api.v2.bookingDisplayPreferences.availabilityWindow.invalid}")
          String availabilityWindowStart,
      @NotNull
          @Pattern(
              regexp = "(?:(?:[01]\\d|2[0-3]):[0-5]\\d|24:00)",
              message = "{errors.api.v2.bookingDisplayPreferences.availabilityWindow.invalid}")
          String availabilityWindowEnd,
      @NotNull BookingTimezoneMode timezoneMode,
      @Size(max = 255, message = "{errors.api.v2.bookingDisplayPreferences.timeZone.invalid}")
          @Schema(
              description =
                  "Required only when timezoneMode is CUSTOM; must be a valid IANA timezone.",
              example = "America/New_York",
              nullable = true)
          String customTimezone) {

    @JsonAnySetter
    void rejectUnknownField(String fieldName, Object ignoredValue) {
      throw new IllegalArgumentException(fieldName);
    }

    BookingDisplaySettings settings() {
      return new BookingDisplaySettings(
          availabilityWindowStart, availabilityWindowEnd, timezoneMode, customTimezone);
    }
  }

  @Schema(name = "BookingDisplayPreferences")
  public record Document(
      String availabilityWindowStart,
      String availabilityWindowEnd,
      BookingTimezoneMode timezoneMode,
      String customTimezone,
      @Schema(
              description = "The running institution's JVM-default IANA timezone.",
              accessMode = Schema.AccessMode.READ_ONLY)
          String institutionTimezone,
      @Schema(
              description = "Whether these values come from an explicit user override.",
              accessMode = Schema.AccessMode.READ_ONLY)
          boolean overridden) {

    static Document from(ResolvedBookingDisplayPreferences preferences) {
      return new Document(
          preferences.availabilityWindowStart(),
          preferences.availabilityWindowEnd(),
          preferences.timezoneMode(),
          preferences.customTimezone(),
          preferences.institutionTimezone(),
          preferences.overridden());
    }
  }

  private final BookingDisplayPreferencesManager manager;

  public BookingDisplayPreferencesController(BookingDisplayPreferencesManager manager) {
    this.manager = manager;
  }

  @GetMapping
  @Operation(
      operationId = "getMyBookingPreferences",
      summary = "Get resolved Booking display preferences",
      description =
          "Returns the current user's complete override or the current global defaults. Browser"
              + " mode is resolved by the client; institutionTimezone is the JVM default zone.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Resolved Booking display preferences."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Booking is unavailable.")
      })
  public Document get(@RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return Document.from(manager.get(caller.subject(), caller.actor()));
  }

  @PutMapping
  @Operation(
      operationId = "replaceMyBookingPreferences",
      summary = "Replace Booking display preferences",
      description =
          "Stores one complete override. customTimezone is required only for CUSTOM and is"
              + " discarded for BROWSER or INSTITUTION.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Stored Booking display preferences."),
        @ApiResponse(responseCode = "400", description = "The replacement document is invalid."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Booking is unavailable.")
      })
  public Document replace(
      @Valid @RequestBody Replacement replacement,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return Document.from(manager.replace(replacement.settings(), caller.subject(), caller.actor()));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "resetMyBookingPreferences",
      summary = "Reset Booking display preferences",
      description = "Deletes the logical override so subsequent reads inherit global defaults.",
      responses = {
        @ApiResponse(responseCode = "204", description = "The override was reset."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Booking is unavailable.")
      })
  public void reset(@RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    manager.reset(caller.subject(), caller.actor());
  }
}
