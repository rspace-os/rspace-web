package com.researchspace.api.v2.controller;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.service.BookingConfigurationDefaultsManager;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.service.FeatureFlagManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Singleton REST v2 interface for defaults copied into future booking configurations. */
@RestController
@RequestMapping("/api/v2/booking-settings")
public final class BookingSettingsController {

  public record SettingsDocument(
      long slotGranularityMinutes,
      String openingStart,
      String openingEnd,
      long bufferBeforeMinutes,
      long bufferAfterMinutes,
      long maxBookingDurationMinutes,
      boolean allowDoubleBooking,
      long configurationVersion) {

    static SettingsDocument from(BookingConfigurationDefaults defaults) {
      return new SettingsDocument(
          defaults.getSlotGranularityMinutes(),
          defaults.getOpeningStart(),
          defaults.getOpeningEnd(),
          defaults.getBufferBeforeMinutes(),
          defaults.getBufferAfterMinutes(),
          defaults.getMaxBookingDurationMinutes(),
          defaults.isAllowDoubleBooking(),
          defaults.getConfigurationVersion());
    }
  }

  public record SettingsPatch(
      @Min(value = 1, message = "{errors.api.v2.bookingConfiguration.granularity.invalid}")
          @Max(value = 15, message = "{errors.api.v2.bookingConfiguration.granularity.invalid}")
          Long slotGranularityMinutes,
      @Pattern(
              regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d",
              message = "{errors.api.v2.bookingConfiguration.openingHours.invalid}")
          String openingStart,
      @Pattern(
              regexp = "(?:(?:[01]\\d|2[0-3]):[0-5]\\d|24:00)",
              message = "{errors.api.v2.bookingConfiguration.openingHours.invalid}")
          String openingEnd,
      @Min(value = 0, message = "{errors.api.v2.bookingConfiguration.buffer.invalid}")
          @Max(value = 10_080, message = "{errors.api.v2.bookingConfiguration.buffer.invalid}")
          Long bufferBeforeMinutes,
      @Min(value = 0, message = "{errors.api.v2.bookingConfiguration.buffer.invalid}")
          @Max(value = 10_080, message = "{errors.api.v2.bookingConfiguration.buffer.invalid}")
          Long bufferAfterMinutes,
      @Min(value = 0, message = "{errors.api.v2.bookingConfiguration.maximumDuration.invalid}")
          @Max(
              value = 527_040,
              message = "{errors.api.v2.bookingConfiguration.maximumDuration.invalid}")
          Long maxBookingDurationMinutes,
      Boolean allowDoubleBooking,
      @NotNull Long configurationVersion) {

    BookingSchedulingSettings.Patch schedulingPatch() {
      return new BookingSchedulingSettings.Patch(
          slotGranularityMinutes,
          openingStart,
          openingEnd,
          bufferBeforeMinutes,
          bufferAfterMinutes,
          maxBookingDurationMinutes,
          allowDoubleBooking);
    }
  }

  private final BookingConfigurationDefaultsManager manager;
  private final FeatureFlagManager featureFlags;

  public BookingSettingsController(
      BookingConfigurationDefaultsManager manager, FeatureFlagManager featureFlags) {
    this.manager = manager;
    this.featureFlags = featureFlags;
  }

  @GetMapping
  @Operation(
      operationId = "getBookingSettings",
      summary = "Get booking configuration defaults",
      description = "Returns defaults copied once into each future bookable item.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Current booking defaults."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Booking is unavailable.")
      })
  public SettingsDocument get(
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    requireEnabled(caller.subject());
    return SettingsDocument.from(manager.getDefaults(caller.subject()));
  }

  @PatchMapping
  @Operation(
      operationId = "patchBookingSettings",
      summary = "Patch booking configuration defaults",
      description = "Changes defaults for future bookable items without modifying existing ones.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Updated booking defaults."),
        @ApiResponse(responseCode = "400", description = "The resulting settings are invalid."),
        @ApiResponse(
            responseCode = "409",
            description = "The settings changed since they were read."),
        @ApiResponse(responseCode = "403", description = "Sysadmin access is required.")
      })
  public SettingsDocument patch(
      @Valid @RequestBody SettingsPatch patch,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    requireEnabled(caller.subject());
    return SettingsDocument.from(
        manager.updateDefaults(
            patch.schedulingPatch(),
            patch.configurationVersion(),
            caller.subject(),
            caller.actor()));
  }

  private void requireEnabled(com.researchspace.model.User actor) {
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, actor)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }
}
