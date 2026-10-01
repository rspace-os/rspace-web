package com.researchspace.api.v2.controller;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.config.BookingTimeConfig;
import com.researchspace.booking.service.BookingConfigurationDefaultsManager;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingDefaultAccessGrantee;
import com.researchspace.model.booking.BookingDefaultSharedWith;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.BookingTimezoneMode;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResourceAccessDirectoryManager;
import com.researchspace.service.resourceaccess.ResourceGranteeDirectoryEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.NotFoundException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Singleton REST v2 interface for global Booking defaults. */
@RestController
@RequestMapping("/api/v2/booking-settings")
public final class BookingSettingsController {

  public record SelectedAccessGrantee(
      String kind, long id, String key, String name, String detail, boolean available) {

    static SelectedAccessGrantee from(BookingDefaultAccessGrantee grantee) {
      boolean available =
          switch (grantee.getGranteeKind()) {
            case USER -> grantee.getUser() != null && grantee.getUser().isEnabled();
            case GROUP ->
                grantee.getGroup() != null && grantee.getGroup().getEnabledMemberSize() > 0;
            case AUDIENCE -> false;
          };
      return new SelectedAccessGrantee(
          grantee.getGranteeKind().name(),
          numericId(grantee.getGranteeKey()),
          grantee.getGranteeKey(),
          grantee.getNameSnapshot(),
          grantee.getDetailSnapshot(),
          available);
    }

    private static long numericId(String key) {
      return Long.parseLong(key.substring(key.indexOf(':') + 1));
    }
  }

  public record SettingsDocument(
      long slotGranularityMinutes,
      String openingStart,
      String openingEnd,
      long bufferBeforeMinutes,
      long bufferAfterMinutes,
      long maxBookingDurationMinutes,
      boolean allowDoubleBooking,
      String availabilityWindowStart,
      String availabilityWindowEnd,
      BookingTimezoneMode timezoneMode,
      String customTimezone,
      String institutionTimezone) {

    static SettingsDocument from(BookingConfigurationDefaults defaults, Clock institutionClock) {
      return new SettingsDocument(
          defaults.getSlotGranularityMinutes(),
          defaults.getOpeningStart(),
          defaults.getOpeningEnd(),
          defaults.getBufferBeforeMinutes(),
          defaults.getBufferAfterMinutes(),
          defaults.getMaxBookingDurationMinutes(),
          defaults.isAllowDoubleBooking(),
          defaults.getAvailabilityWindowStart(),
          defaults.getAvailabilityWindowEnd(),
          defaults.getTimezoneMode(),
          defaults.getCustomTimezone(),
          institutionClock.getZone().getId());
    }
  }

  public record AdminSettingsDocument(
      long slotGranularityMinutes,
      String openingStart,
      String openingEnd,
      long bufferBeforeMinutes,
      long bufferAfterMinutes,
      long maxBookingDurationMinutes,
      boolean allowDoubleBooking,
      String availabilityWindowStart,
      String availabilityWindowEnd,
      BookingTimezoneMode timezoneMode,
      String customTimezone,
      String institutionTimezone,
      BookingDefaultSharedWith defaultSharedWith,
      List<SelectedAccessGrantee> selectedAccessGrantees,
      long configurationVersion) {

    static AdminSettingsDocument from(
        BookingConfigurationDefaults defaults, Clock institutionClock) {
      return new AdminSettingsDocument(
          defaults.getSlotGranularityMinutes(),
          defaults.getOpeningStart(),
          defaults.getOpeningEnd(),
          defaults.getBufferBeforeMinutes(),
          defaults.getBufferAfterMinutes(),
          defaults.getMaxBookingDurationMinutes(),
          defaults.isAllowDoubleBooking(),
          defaults.getAvailabilityWindowStart(),
          defaults.getAvailabilityWindowEnd(),
          defaults.getTimezoneMode(),
          defaults.getCustomTimezone(),
          institutionClock.getZone().getId(),
          defaults.getDefaultSharedWith(),
          defaults.getSelectedAccessGrantees().stream().map(SelectedAccessGrantee::from).toList(),
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
      @Pattern(
              regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d",
              message = "{errors.api.v2.bookingDisplayPreferences.availabilityWindow.invalid}")
          String availabilityWindowStart,
      @Pattern(
              regexp = "(?:(?:[01]\\d|2[0-3]):[0-5]\\d|24:00)",
              message = "{errors.api.v2.bookingDisplayPreferences.availabilityWindow.invalid}")
          String availabilityWindowEnd,
      BookingTimezoneMode timezoneMode,
      @Size(max = 255, message = "{errors.api.v2.bookingDisplayPreferences.timeZone.invalid}")
          String customTimezone,
      BookingDefaultSharedWith defaultSharedWith,
      List<String> selectedGranteeKeys,
      @NotNull @Min(value = 0, message = "{errors.api.v2.invalidRequest}")
          Long configurationVersion) {

    @AssertTrue(message = "{errors.api.v2.bookingDisplayPreferences.timeZone.invalid}")
    public boolean isCustomTimezoneValid() {
      if (customTimezone == null) {
        return true;
      }
      if (customTimezone.isBlank()) {
        return false;
      }
      try {
        ZoneId.of(customTimezone);
        return true;
      } catch (DateTimeException ex) {
        return false;
      }
    }

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

    BookingDisplaySettings.Patch displayPatch() {
      return new BookingDisplaySettings.Patch(
          availabilityWindowStart, availabilityWindowEnd, timezoneMode, customTimezone);
    }
  }

  private final BookingConfigurationDefaultsManager manager;
  private final FeatureFlagManager featureFlags;
  private final ResourceAccessDirectoryManager directoryManager;
  private final Clock institutionClock;

  public BookingSettingsController(
      BookingConfigurationDefaultsManager manager,
      FeatureFlagManager featureFlags,
      ResourceAccessDirectoryManager directoryManager,
      @Qualifier(BookingTimeConfig.INSTITUTION_CLOCK) Clock institutionClock) {
    this.manager = manager;
    this.featureFlags = featureFlags;
    this.directoryManager = directoryManager;
    this.institutionClock = institutionClock;
  }

  @GetMapping
  @Operation(
      operationId = "getBookingSettings",
      summary = "Get global Booking defaults",
      description =
          "Returns scheduling defaults for future bookable items and account display fallbacks.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Current booking defaults."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "404", description = "Booking is unavailable.")
      })
  public SettingsDocument get(
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    requireEnabledRead(caller.subject());
    return SettingsDocument.from(manager.getDefaults(caller.subject()), institutionClock);
  }

  @GetMapping("/admin")
  @Operation(
      operationId = "getAdminBookingSettings",
      summary = "Get global Booking administration settings",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Current Booking administration settings."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Sysadmin access is required."),
        @ApiResponse(responseCode = "404", description = "Booking is unavailable.")
      })
  public AdminSettingsDocument getAdmin(
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    requireEnabledRead(caller.subject());
    return AdminSettingsDocument.from(manager.getAdminDefaults(caller.subject()), institutionClock);
  }

  @GetMapping("/access-grantees")
  public List<ResourceGranteeDirectoryEntry> accessGrantees(
      @org.springframework.web.bind.annotation.RequestParam String query,
      @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int limit,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    requireEnabledRead(caller.subject());
    return directoryManager.searchForSettings(
        ResourceAccessController.validateQuery(query),
        ResourceAccessController.validateLimit(limit),
        caller.subject());
  }

  @PatchMapping("/admin")
  @Operation(
      operationId = "patchAdminBookingSettings",
      summary = "Patch global Booking defaults",
      description =
          "Changes scheduling defaults for future items and display fallbacks for users without"
              + " an override, without modifying existing item scheduling rules.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Updated booking defaults."),
        @ApiResponse(responseCode = "400", description = "The resulting settings are invalid."),
        @ApiResponse(
            responseCode = "409",
            description = "The settings changed since they were read."),
        @ApiResponse(responseCode = "403", description = "Sysadmin access is required.")
      })
  public AdminSettingsDocument patchAdmin(
      @Valid @RequestBody SettingsPatch patch,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    requireEnabledMutation(caller.subject());
    return AdminSettingsDocument.from(
        manager.updateDefaults(
            patch.schedulingPatch(),
            patch.displayPatch(),
            patch.defaultSharedWith(),
            patch.selectedGranteeKeys(),
            patch.configurationVersion(),
            caller.subject(),
            caller.actor()),
        institutionClock);
  }

  private void requireEnabledRead(com.researchspace.model.User actor) {
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, actor)) {
      throw new NotFoundException();
    }
  }

  private void requireEnabledMutation(com.researchspace.model.User actor) {
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, actor)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }
}
