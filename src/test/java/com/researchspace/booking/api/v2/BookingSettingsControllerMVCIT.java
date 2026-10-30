package com.researchspace.booking.api.v2;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

@ApiV2WebIntegrationTest
class BookingSettingsControllerMVCIT {

  private final WebApplicationContext context;
  private final BookingConfigurationDefaultsManager settingsManager;
  private final FeatureFlagManager featureFlags;
  private final UserManager userManager;

  @Autowired
  BookingSettingsControllerMVCIT(
      WebApplicationContext context,
      BookingConfigurationDefaultsManager settingsManager,
      FeatureFlagManager featureFlags,
      UserManager userManager) {
    this.context = context;
    this.settingsManager = settingsManager;
    this.featureFlags = featureFlags;
    this.userManager = userManager;
  }

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
            originalSettings.openingHours(),
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
            patch("/api/v2/booking-settings/admin")
                .header("apiKey", fixture.userKey())
                .header("If-Match", "\"" + version + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(true)))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsRequestsWhenBookingIsDisabled() throws Exception {
    setBookingEnabled(false);
    mockMvc
        .perform(get("/api/v2/booking-settings").header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsInvalidOpeningHoursThroughTheSharedCodec() throws Exception {
    mockMvc
        .perform(
            patch("/api/v2/booking-settings/admin")
                .header("apiKey", fixture.sysadminKey())
                .header(
                    "If-Match",
                    "\"" + settingsManager.getDefaults(sysadmin).getConfigurationVersion() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"openingHours":[{"dayOfWeek":1,"start":"8:00","end":"17:00"}]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.code").value("errors.api.v2.bookingConfiguration.openingHours.invalid"));
  }

  @Test
  void rejectsAnInvalidCustomTimezone() throws Exception {
    mockMvc
        .perform(
            patch("/api/v2/booking-settings/admin")
                .header("apiKey", fixture.sysadminKey())
                .header(
                    "If-Match",
                    "\"" + settingsManager.getDefaults(sysadmin).getConfigurationVersion() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"timezoneMode":"CUSTOM","customTimezone":"Not/A_Timezone"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
  }

  @Test
  void rejectsANegativeConfigurationVersion() throws Exception {
    mockMvc
        .perform(
            patch("/api/v2/booking-settings/admin")
                .header("apiKey", fixture.sysadminKey())
                .header("If-Match", "\"" + -1 + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
  }

  @Test
  void rejectsAMaximumThatDoesNotAlignWithGranularity() throws Exception {
    mockMvc
        .perform(
            patch("/api/v2/booking-settings/admin")
                .header("apiKey", fixture.sysadminKey())
                .header(
                    "If-Match",
                    "\"" + settingsManager.getDefaults(sysadmin).getConfigurationVersion() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"maxBookingDurationMinutes":7}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.code").value("errors.api.v2.bookingConfiguration.maximumDuration.invalid"));
  }

  @Test
  void rejectsAnUnreadableRequestBody() throws Exception {
    mockMvc
        .perform(
            patch("/api/v2/booking-settings/admin")
                .header("apiKey", fixture.sysadminKey())
                .header(
                    "If-Match",
                    "\"" + settingsManager.getDefaults(sysadmin).getConfigurationVersion() + "\"")
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
            patch("/api/v2/booking-settings/admin")
                .header("apiKey", fixture.sysadminKey())
                .header(
                    "If-Match",
                    "\"" + settingsManager.getDefaults(sysadmin).getConfigurationVersion() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(requested)))
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
            patch("/api/v2/booking-settings/admin")
                .header("apiKey", fixture.sysadminKey())
                .header(
                    "If-Match",
                    "\"" + settingsManager.getDefaults(sysadmin).getConfigurationVersion() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"maxBookingDurationMinutes":60}
                    """))
        .andDo(BookingSettingsControllerMVCIT::failOnUnexpectedServerError)
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
        .andExpect(header().string("ETag", "\"" + before.getConfigurationVersion() + "\""))
        .andExpect(header().string("Cache-Control", "no-store, no-transform, private"))
        .andExpect(jsonPath("$.availabilityWindowStart").isString())
        .andExpect(jsonPath("$.availabilityWindowEnd").isString())
        .andExpect(jsonPath("$.timezoneMode").isString())
        .andExpect(jsonPath("$.institutionTimezone").isString())
        .andExpect(jsonPath("$.defaultSharedWith").doesNotExist())
        .andExpect(jsonPath("$.selectedAccessGrantees").doesNotExist())
        .andExpect(jsonPath("$.configurationVersion").doesNotExist());

    mockMvc
        .perform(get("/api/v2/booking-settings/admin").header("apiKey", fixture.sysadminKey()))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"" + before.getConfigurationVersion() + "\""))
        .andExpect(jsonPath("$.defaultSharedWith").isString())
        .andExpect(jsonPath("$.selectedAccessGrantees").isArray())
        .andExpect(jsonPath("$.configurationVersion").isNumber());

    mockMvc
        .perform(
            patch("/api/v2/booking-settings/admin")
                .header("apiKey", fixture.sysadminKey())
                .header(
                    "If-Match",
                    "\"" + settingsManager.getDefaults(sysadmin).getConfigurationVersion() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"availabilityWindowStart":"09:00","availabilityWindowEnd":"17:00",
                     "timezoneMode":"CUSTOM","customTimezone":"America/New_York"}
                    """))
        .andDo(BookingSettingsControllerMVCIT::failOnUnexpectedServerError)
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
  void rejectsAStaleVersionWithPreconditionFailed() throws Exception {
    BookingConfigurationDefaults before = settingsManager.getDefaults(sysadmin);
    long version = before.getConfigurationVersion();

    mockMvc
        .perform(
            patch("/api/v2/booking-settings/admin")
                .header("apiKey", fixture.sysadminKey())
                .header("If-Match", "\"" + version + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(!before.isAllowDoubleBooking())))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/v2/booking-settings/admin")
                .header("apiKey", fixture.sysadminKey())
                .header("If-Match", "\"" + version + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(before.isAllowDoubleBooking())))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.code").value("errors.api.v2.bookingConfiguration.stale"));
  }

  private void setBookingEnabled(boolean enabled) {
    featureFlags
        .updateFeatureFlag(
            BOOKING_ENABLED, new FeatureFlagManager.Patch(enabled, false, null), sysadmin, sysadmin)
        .orElseThrow();
  }

  private static String body(boolean allowDoubleBooking) {
    return """
    {"allowDoubleBooking":%s}
    """
        .formatted(allowDoubleBooking);
  }

  private static void failOnUnexpectedServerError(MvcResult result) throws Exception {
    if (result.getResponse().getStatus() >= 500) {
      throw new AssertionError(
          "Unexpected server error: " + result.getResponse().getContentAsString(),
          result.getResolvedException());
    }
  }
}
