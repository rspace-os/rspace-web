package com.researchspace.api.v2.controller;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.AbstractAppInitializor;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@ApiV2WebIntegrationTest
class BookingDisplayPreferencesControllerMVCIT {

  private static final String PATH = "/api/v2/users/me/booking-preferences";

  private final WebApplicationContext context;
  private final FeatureFlagManager featureFlags;
  private final UserManager userManager;

  @Autowired
  BookingDisplayPreferencesControllerMVCIT(
      WebApplicationContext context, FeatureFlagManager featureFlags, UserManager userManager) {
    this.context = context;
    this.featureFlags = featureFlags;
    this.userManager = userManager;
  }

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;
  private User sysadmin;
  private boolean originalBookingEnabled;

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    mockMvc = fixture.mockMvc();
    sysadmin = userManager.getUserByUsername(AbstractAppInitializor.SYSADMIN_UNAME);
    originalBookingEnabled =
        featureFlags.getFeatureFlag(BOOKING_ENABLED, sysadmin).orElseThrow().isBaselineValue();
    setBookingEnabled(true);
  }

  @AfterEach
  void tearDown() {
    setBookingEnabled(originalBookingEnabled);
    fixture.cleanUp();
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(delete(PATH)).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsYamlDisplayPreferenceReplacementBodies() throws Exception {
    mockMvc
        .perform(
            put(PATH)
                .header("apiKey", fixture.userKey())
                .contentType("application/x-yaml")
                .content(
                    """
                    availabilityWindowStart: '09:00'
                    availabilityWindowEnd: '17:00'
                    timezoneMode: INSTITUTION
                    """))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("errors.api.v2.unsupportedMediaType"));
  }

  @Test
  void inheritsTheCompleteGlobalDocumentForAUserWithoutAnOverride() throws Exception {
    mockMvc
        .perform(get(PATH).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availabilityWindowStart").isString())
        .andExpect(jsonPath("$.availabilityWindowEnd").isString())
        .andExpect(jsonPath("$.timezoneMode").isString())
        .andExpect(jsonPath("$.timeFormat").isString())
        .andExpect(jsonPath("$.institutionTimezone").isString())
        .andExpect(jsonPath("$.overridden").value(false));
  }

  @Test
  void replacementIsOwnedByTheAuthenticatedUserAndResetRestoresInheritance() throws Exception {
    mockMvc
        .perform(
            put(PATH)
                .header("apiKey", fixture.userKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availabilityWindowStart").value("09:00"))
        .andExpect(jsonPath("$.availabilityWindowEnd").value("17:00"))
        .andExpect(jsonPath("$.timezoneMode").value("CUSTOM"))
        .andExpect(jsonPath("$.customTimezone").value("America/New_York"))
        .andExpect(jsonPath("$.timeFormat").value("AUTOMATIC"))
        .andExpect(jsonPath("$.institutionTimezone").isString())
        .andExpect(jsonPath("$.overridden").value(true));

    mockMvc
        .perform(get(PATH).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customTimezone").value("America/New_York"))
        .andExpect(jsonPath("$.overridden").value(true));
    mockMvc
        .perform(get(PATH).header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overridden").value(false));

    mockMvc
        .perform(delete(PATH).header("apiKey", fixture.userKey()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(PATH).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overridden").value(false));
  }

  @Test
  void rejectsInvalidAndUnknownReplacementFields() throws Exception {
    mockMvc
        .perform(
            put(PATH)
                .header("apiKey", fixture.userKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"availabilityWindowStart":"18:00","availabilityWindowEnd":"08:00",
                     "timezoneMode":"INSTITUTION","customTimezone":null}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.code")
                .value("errors.api.v2.bookingDisplayPreferences.availabilityWindow.invalid"));

    mockMvc
        .perform(
            put(PATH)
                .header("apiKey", fixture.userKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBodyWith(Map.of("unknown", true))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void acceptsMissingAndNullCustomTimezoneForNonCustomModes() throws Exception {
    for (String body :
        List.of(
            """
            {"availabilityWindowStart":"09:00","availabilityWindowEnd":"17:00",
             "timezoneMode":"INSTITUTION"}
            """,
            """
            {"availabilityWindowStart":"09:00","availabilityWindowEnd":"17:00",
             "timezoneMode":"INSTITUTION","customTimezone":null}
            """)) {
      mockMvc
          .perform(
              put(PATH)
                  .header("apiKey", fixture.userKey())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.timezoneMode").value("INSTITUTION"))
          .andExpect(jsonPath("$.customTimezone").isEmpty());
    }
  }

  @Test
  void storesAnExplicitTimeFormatAndRejectsUnknownOnes() throws Exception {
    mockMvc
        .perform(
            put(PATH)
                .header("apiKey", fixture.userKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBodyWith(Map.of("timeFormat", "H24"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeFormat").value("H24"));
    mockMvc
        .perform(get(PATH).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeFormat").value("H24"));

    for (Object invalid : new Object[] {"NOPE", "h24", 1, true}) {
      mockMvc
          .perform(
              put(PATH)
                  .header("apiKey", fixture.userKey())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(validBodyWith(Map.of("timeFormat", invalid))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
    }
    mockMvc
        .perform(get(PATH).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeFormat").value("H24"));
  }

  @Test
  void rejectsRequestsWhenBookingIsDisabled() throws Exception {
    setBookingEnabled(false);
    mockMvc
        .perform(get(PATH).header("apiKey", fixture.userKey()))
        .andExpect(status().isForbidden());
  }

  private void setBookingEnabled(boolean enabled) {
    featureFlags
        .updateFeatureFlag(
            BOOKING_ENABLED, new FeatureFlagManager.Patch(enabled, false, null), sysadmin, sysadmin)
        .orElseThrow();
  }

  private static String validBody() {
    return validBodyWith(Map.of());
  }

  private static String validBodyWith(Map<String, ?> extra) {
    Map<String, Object> body =
        new HashMap<>(
            Map.of(
                "availabilityWindowStart", "09:00",
                "availabilityWindowEnd", "17:00",
                "timezoneMode", "CUSTOM",
                "customTimezone", "America/New_York"));
    body.putAll(extra);
    return JacksonUtil.toJson(body);
  }
}
