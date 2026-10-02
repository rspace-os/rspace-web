package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingTimeFormat;
import com.researchspace.model.booking.BookingTimezoneMode;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingDisplayPreferencesManagerTest {

  private final UserManager userManager = mock(UserManager.class);
  private final BookingConfigurationDefaultsManager defaultsManager =
      mock(BookingConfigurationDefaultsManager.class);
  private final FeatureFlagManager featureFlags = mock(FeatureFlagManager.class);
  private final Clock institutionClock =
      Clock.fixed(Instant.parse("2026-08-28T12:00:00Z"), ZoneId.of("Europe/Berlin"));
  private final BookingDisplayPreferencesManager manager =
      new BookingDisplayPreferencesManagerImpl(
          userManager, new ObjectMapper(), defaultsManager, featureFlags, institutionClock);

  private User subject;
  private User actor;

  @BeforeEach
  void setUp() {
    subject = user(7L, "subject");
    actor = user(9L, "actor");
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)).thenReturn(true);
    when(defaultsManager.getDefaults(subject)).thenReturn(defaults());
  }

  @Test
  void blankPreferenceInheritsTheCompleteCurrentGlobalDocument() {
    preference("");

    var resolved = manager.get(subject, actor);

    assertEquals("08:00", resolved.availabilityWindowStart());
    assertEquals("18:00", resolved.availabilityWindowEnd());
    assertEquals(BookingTimezoneMode.BROWSER, resolved.timezoneMode());
    assertEquals(BookingTimeFormat.AUTOMATIC, resolved.timeFormat());
    assertEquals("Europe/Berlin", resolved.institutionTimezone());
    assertFalse(resolved.overridden());
  }

  @Test
  void inheritsTheGlobalTimeFormat() {
    BookingConfigurationDefaults defaults = defaults();
    defaults.setTimeFormat(BookingTimeFormat.H12);
    when(defaultsManager.getDefaults(subject)).thenReturn(defaults);
    preference("");

    assertEquals(BookingTimeFormat.H12, manager.get(subject, actor).timeFormat());
  }

  @Test
  void storesAndReadsBackAnExplicitTimeFormat() throws Exception {
    BookingDisplaySettings twentyFourHour =
        new BookingDisplaySettings(
            "08:00", "18:00", BookingTimezoneMode.BROWSER, null, BookingTimeFormat.H24);

    var resolved = manager.replace(twentyFourHour, subject, actor);

    org.mockito.ArgumentCaptor<String> stored = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(userManager)
        .setPreference(eq(Preference.BOOKING_DISPLAY_PREFERENCES), stored.capture(), eq("subject"));
    assertTrue(stored.getValue().contains("\"timeFormat\":\"H24\""), stored.getValue());
    assertEquals(BookingTimeFormat.H24, resolved.timeFormat());
    preference(stored.getValue());
    assertEquals(BookingTimeFormat.H24, manager.get(subject, actor).timeFormat());
  }

  @Test
  void aStoredDocumentFromBeforeTheTimeFormatReadsAsAutomatic() {
    preference(
        """
        {"version":1,"availabilityWindowStart":"09:00","availabilityWindowEnd":"17:00",
        "timezoneMode":"INSTITUTION","customTimezone":null}
        """);

    var resolved = manager.get(subject, actor);

    assertTrue(resolved.overridden());
    assertEquals("09:00", resolved.availabilityWindowStart());
    assertEquals(BookingTimeFormat.AUTOMATIC, resolved.timeFormat());
  }

  @Test
  void replaceStoresOneVersionedDocumentAndReturnsAnOverride() {
    BookingDisplaySettings custom =
        new BookingDisplaySettings(
            "09:00", "17:00", BookingTimezoneMode.CUSTOM, "America/New_York");

    var resolved = manager.replace(custom, subject, actor);

    verify(userManager)
        .setPreference(
            eq(Preference.BOOKING_DISPLAY_PREFERENCES),
            org.mockito.ArgumentMatchers.contains("\"version\":1"),
            eq("subject"));
    assertEquals("America/New_York", resolved.customTimezone());
    assertTrue(resolved.overridden());
  }

  @Test
  void resetWritesTheSupportedBlankValue() {
    manager.reset(subject, actor);

    verify(userManager)
        .setPreference(Preference.BOOKING_DISPLAY_PREFERENCES, "", subject.getUsername());
  }

  @Test
  void corruptAndUnsupportedDocumentsFallBackWithoutExposingTheirContents() {
    preference("{not-json");
    assertFalse(manager.get(subject, actor).overridden());

    preference(
        """
        {"version":2,"availabilityWindowStart":"09:00","availabilityWindowEnd":"17:00",
        "timezoneMode":"INSTITUTION","customTimezone":null}
        """);
    assertFalse(manager.get(subject, actor).overridden());
  }

  @Test
  void rejectsInvalidSettingsAndUnavailableCallers() {
    assertThrows(
        InvalidBookingDisplaySettingsException.class,
        () ->
            manager.replace(
                new BookingDisplaySettings("18:00", "08:00", BookingTimezoneMode.INSTITUTION, null),
                subject,
                actor));

    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)).thenReturn(false);
    assertThrows(AuthorizationException.class, () -> manager.get(subject, actor));
  }

  @Test
  void notificationPreferenceLookupFallsBackWithoutThrowingForUnavailableRecipients() {
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)).thenReturn(false);

    assertTrue(manager.getForNotificationRecipient(subject).isEmpty());

    when(subject.isEnabled()).thenReturn(false);
    assertTrue(manager.getForNotificationRecipient(subject).isEmpty());
  }

  @Test
  void notificationSnapshotUsesLoadedPreferencesWithoutApplicationCache() {
    when(subject.getValueForPreference(Preference.BOOKING_DISPLAY_PREFERENCES))
        .thenReturn(
            new UserPreference(
                Preference.BOOKING_DISPLAY_PREFERENCES,
                subject,
                "{\"version\":1,\"availabilityWindowStart\":\"08:00\",\"availabilityWindowEnd\":\"18:00\","
                    + "\"timezoneMode\":\"CUSTOM\",\"customTimezone\":\"Asia/Tokyo\"}"));
    assertEquals("Asia/Tokyo", manager.resolveForNotificationSnapshot(subject).customTimezone());
    org.mockito.Mockito.verifyNoInteractions(userManager);
  }

  private void preference(String value) {
    UserPreference preference = mock(UserPreference.class);
    when(preference.getValue()).thenReturn(value);
    when(userManager.getPreferenceForUser(subject, Preference.BOOKING_DISPLAY_PREFERENCES))
        .thenReturn(preference);
  }

  private static User user(long id, String username) {
    User user = mock(User.class);
    when(user.getId()).thenReturn(id);
    when(user.getUsername()).thenReturn(username);
    when(user.isEnabled()).thenReturn(true);
    when(user.isAccountLocked()).thenReturn(false);
    return user;
  }

  private static BookingConfigurationDefaults defaults() {
    BookingConfigurationDefaults defaults = new BookingConfigurationDefaults();
    defaults.setId(BookingConfigurationDefaults.SINGLETON_ID);
    BookingDisplaySettings.defaults().applyTo(defaults);
    return defaults;
  }
}
