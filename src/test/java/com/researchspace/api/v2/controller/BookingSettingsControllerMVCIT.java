package com.researchspace.api.v2.controller;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.booking.service.BookingConfigurationDefaultsManager;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.AbstractAppInitializor;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@ApiV2WebIntegrationTest
class BookingSettingsControllerMVCIT {

  @Autowired private WebApplicationContext context;
  @Autowired private BookingConfigurationDefaultsManager settingsManager;
  @Autowired private FeatureFlagManager featureFlags;
  @Autowired private UserManager userManager;

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;
  private User sysadmin;
  private boolean originalBookingEnabled;
  private BookingSchedulingSettings originalSettings;
  private BookingDisplaySettings originalDisplaySettings;

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    mockMvc = fixture.mockMvc();
    sysadmin = userManager.getUserByUsername(AbstractAppInitializor.SYSADMIN_UNAME);
    originalSettings = BookingSchedulingSettings.from(settingsManager.getDefaults(sysadmin));
    originalDisplaySettings = BookingDisplaySettings.from(settingsManager.getDefaults(sysadmin));
    originalBookingEnabled =
        featureFlags.getFeatureFlag(BOOKING_ENABLED, sysadmin).orElseThrow().isBaselineValue();
    setBookingEnabled(true);
  }

  @AfterEach
  void tearDown() {
    BookingConfigurationDefaults current = settingsManager.getDefaults(sysadmin);
    settingsManager.updateDefaults(
        new BookingSchedulingSettings.Patch(
            originalSettings.slotGranularityMinutes(),
            originalSettings.openingStart(),
            originalSettings.openingEnd(),
            originalSettings.bufferBeforeMinutes(),
            originalSettings.bufferAfterMinutes(),
            originalSettings.maxBookingDurationMinutes(),
            originalSettings.allowDoubleBooking()),
        new BookingDisplaySettings.Patch(
            originalDisplaySettings.availabilityWindowStart(),
            originalDisplaySettings.availabilityWindowEnd(),
            originalDisplaySettings.timezoneMode(),
            originalDisplaySettings.customTimezone()),
        current.getConfigurationVersion(),
        sysadmin,
        sysadmin);
    setBookingEnabled(originalBookingEnabled);
    fixture.cleanUp();
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v2/booking-settings")).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsAUserWithoutSysadminRole() throws Exception {
    fixture.user();
    long version = settingsManager.getDefaults(sysadmin).getConfigurationVersion();

    mockMvc
        .perform(
            patch("/api/v2/booking-settings")
                .header("apiKey", fixture.userKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(version, true)))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsRequestsWhenBookingIsDisabled() throws Exception {
    setBookingEnabled(false);
    mockMvc
        .perform(get("/api/v2/booking-settings").header("apiKey", fixture.userKey()))
        .andExpect(status().isForbidden());
  }

  @Test
  void validatesTheBoundRequestBeforeCallingTheManager() throws Exception {
    long version = settingsManager.getDefaults(sysadmin).getConfigurationVersion();

    mockMvc
        .perform(
            patch("/api/v2/booking-settings")
                .header("apiKey", fixture.sysadminKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"openingStart":"8:00","configurationVersion":%d}
                    """
                        .formatted(version)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
  }

  @Test
  void rejectsAnInvalidCustomTimezone() throws Exception {
    long version = settingsManager.getDefaults(sysadmin).getConfigurationVersion();

    mockMvc
        .perform(
            patch("/api/v2/booking-settings")
                .header("apiKey", fixture.sysadminKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"timezoneMode":"CUSTOM","customTimezone":"Not/A_Timezone",
                     "configurationVersion":%d}
                    """
                        .formatted(version)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
  }

  @Test
  void rejectsANegativeConfigurationVersion() throws Exception {
    mockMvc
        .perform(
            patch("/api/v2/booking-settings")
                .header("apiKey", fixture.sysadminKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"configurationVersion\":-1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
  }

  @Test
  void rejectsAMaximumThatDoesNotAlignWithGranularity() throws Exception {
    long version = settingsManager.getDefaults(sysadmin).getConfigurationVersion();

    mockMvc
        .perform(
            patch("/api/v2/booking-settings")
                .header("apiKey", fixture.sysadminKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"maxBookingDurationMinutes":7,"configurationVersion":%d}
                    """
                        .formatted(version)))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.code").value("errors.api.v2.bookingConfiguration.maximumDuration.invalid"));
  }

  @Test
  void rejectsAnUnreadableRequestBody() throws Exception {
    mockMvc
        .perform(
            patch("/api/v2/booking-settings")
                .header("apiKey", fixture.sysadminKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
  }

  @Test
  void persistsAValidPatchAndIncrementsTheSingletonVersion() throws Exception {
    BookingConfigurationDefaults before = settingsManager.getDefaults(sysadmin);
    long version = before.getConfigurationVersion();
    boolean requested = !before.isAllowDoubleBooking();

    mockMvc
        .perform(
            patch("/api/v2/booking-settings")
                .header("apiKey", fixture.sysadminKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(version, requested)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowDoubleBooking").value(requested))
        .andExpect(jsonPath("$.configurationVersion").value(version + 1));

    BookingConfigurationDefaults persisted = settingsManager.getDefaults(sysadmin);
    assertEquals(requested, persisted.isAllowDoubleBooking());
    assertEquals(version + 1, persisted.getConfigurationVersion());
  }

  @Test
  void persistsMaximumBookingDuration() throws Exception {
    BookingConfigurationDefaults before = settingsManager.getDefaults(sysadmin);

    mockMvc
        .perform(
            patch("/api/v2/booking-settings")
                .header("apiKey", fixture.sysadminKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"maxBookingDurationMinutes":60,"configurationVersion":%d}
                    """
                        .formatted(before.getConfigurationVersion())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxBookingDurationMinutes").value(60));

    assertEquals(60, settingsManager.getDefaults(sysadmin).getMaxBookingDurationMinutes());
  }

  @Test
  void returnsAndPersistsDisplayDefaultsWithTheInstitutionTimezone() throws Exception {
    BookingConfigurationDefaults before = settingsManager.getDefaults(sysadmin);

    mockMvc
        .perform(get("/api/v2/booking-settings").header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availabilityWindowStart").isString())
        .andExpect(jsonPath("$.availabilityWindowEnd").isString())
        .andExpect(jsonPath("$.timezoneMode").isString())
        .andExpect(jsonPath("$.institutionTimezone").isString());

    mockMvc
        .perform(
            patch("/api/v2/booking-settings")
                .header("apiKey", fixture.sysadminKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"availabilityWindowStart":"09:00","availabilityWindowEnd":"17:00",
                     "timezoneMode":"CUSTOM","customTimezone":"America/New_York",
                     "configurationVersion":%d}
                    """
                        .formatted(before.getConfigurationVersion())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availabilityWindowStart").value("09:00"))
        .andExpect(jsonPath("$.availabilityWindowEnd").value("17:00"))
        .andExpect(jsonPath("$.timezoneMode").value("CUSTOM"))
        .andExpect(jsonPath("$.customTimezone").value("America/New_York"))
        .andExpect(jsonPath("$.institutionTimezone").isString());

    BookingDisplaySettings persisted =
        BookingDisplaySettings.from(settingsManager.getDefaults(sysadmin));
    assertEquals("09:00", persisted.availabilityWindowStart());
    assertEquals("17:00", persisted.availabilityWindowEnd());
    assertEquals("America/New_York", persisted.customTimezone());
  }

  @Test
  void rejectsAStaleVersionWithConflict() throws Exception {
    BookingConfigurationDefaults before = settingsManager.getDefaults(sysadmin);
    long version = before.getConfigurationVersion();

    mockMvc
        .perform(
            patch("/api/v2/booking-settings")
                .header("apiKey", fixture.sysadminKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(version, !before.isAllowDoubleBooking())))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/v2/booking-settings")
                .header("apiKey", fixture.sysadminKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(version, before.isAllowDoubleBooking())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("errors.api.v2.bookingConfiguration.stale"));
  }

  private void setBookingEnabled(boolean enabled) {
    featureFlags
        .updateFeatureFlag(
            BOOKING_ENABLED, new FeatureFlagManager.Patch(enabled, false, null), sysadmin, sysadmin)
        .orElseThrow();
  }

  private static String body(long version, boolean allowDoubleBooking) {
    return """
    {"allowDoubleBooking":%s,"configurationVersion":%d}
    """
        .formatted(allowDoubleBooking, version);
  }
}
