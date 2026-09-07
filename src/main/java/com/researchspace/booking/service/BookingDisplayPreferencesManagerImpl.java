package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.booking.config.BookingTimeConfig;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingTimezoneMode;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import java.time.Clock;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("bookingDisplayPreferencesManager")
@Transactional
public class BookingDisplayPreferencesManagerImpl implements BookingDisplayPreferencesManager {

  private static final Logger log =
      LoggerFactory.getLogger(BookingDisplayPreferencesManagerImpl.class);
  private static final int CURRENT_VERSION = 1;

  private record StoredPreference(
      int version,
      String availabilityWindowStart,
      String availabilityWindowEnd,
      BookingTimezoneMode timezoneMode,
      String customTimezone) {

    static StoredPreference from(BookingDisplaySettings settings) {
      return new StoredPreference(
          CURRENT_VERSION,
          settings.availabilityWindowStart(),
          settings.availabilityWindowEnd(),
          settings.timezoneMode(),
          settings.customTimezone());
    }

    BookingDisplaySettings settings() {
      return new BookingDisplaySettings(
          availabilityWindowStart, availabilityWindowEnd, timezoneMode, customTimezone);
    }
  }

  private final UserManager userManager;
  private final ObjectMapper objectMapper;
  private final BookingConfigurationDefaultsManager defaultsManager;
  private final FeatureFlagManager featureFlags;
  private final Clock institutionClock;

  public BookingDisplayPreferencesManagerImpl(
      UserManager userManager,
      ObjectMapper objectMapper,
      BookingConfigurationDefaultsManager defaultsManager,
      FeatureFlagManager featureFlags,
      @Qualifier(BookingTimeConfig.INSTITUTION_CLOCK) Clock institutionClock) {
    this.userManager = userManager;
    this.objectMapper = objectMapper;
    this.defaultsManager = defaultsManager;
    this.featureFlags = featureFlags;
    this.institutionClock = institutionClock;
  }

  @Override
  public ResolvedBookingDisplayPreferences get(User subject, User actor) {
    requireAccess(subject, actor);
    UserPreference preference =
        userManager.getPreferenceForUser(subject, Preference.BOOKING_DISPLAY_PREFERENCES);
    String value = preference.getValue();
    if (value == null || value.isBlank()) {
      return resolved(globalDefaults(subject), false);
    }
    BookingDisplaySettings stored = readStored(value, subject);
    return stored == null ? resolved(globalDefaults(subject), false) : resolved(stored, true);
  }

  @Override
  public ResolvedBookingDisplayPreferences replace(
      BookingDisplaySettings settings, User subject, User actor) {
    requireAccess(subject, actor);
    BookingDisplaySettings normalized =
        new BookingDisplaySettings(
            settings.availabilityWindowStart(),
            settings.availabilityWindowEnd(),
            settings.timezoneMode(),
            settings.customTimezone());
    BookingConfigurationDefaultsManagerImpl.requireValid(normalized);
    try {
      userManager.setPreference(
          Preference.BOOKING_DISPLAY_PREFERENCES,
          objectMapper.writeValueAsString(StoredPreference.from(normalized)),
          subject.getUsername());
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not serialize Booking display preferences", ex);
    }
    return resolved(normalized, true);
  }

  @Override
  public void reset(User subject, User actor) {
    requireAccess(subject, actor);
    userManager.setPreference(
        Preference.BOOKING_DISPLAY_PREFERENCES,
        Preference.BOOKING_DISPLAY_PREFERENCES.getDefaultValue(),
        subject.getUsername());
  }

  private BookingDisplaySettings globalDefaults(User subject) {
    BookingConfigurationDefaults defaults = defaultsManager.getDefaults(subject);
    BookingDisplaySettings settings = BookingDisplaySettings.from(defaults);
    BookingConfigurationDefaultsManagerImpl.requireValid(settings);
    return settings;
  }

  private BookingDisplaySettings readStored(String value, User subject) {
    Integer version = null;
    try {
      JsonNode root = objectMapper.readTree(value);
      JsonNode versionNode = root == null ? null : root.get("version");
      if (versionNode != null && versionNode.canConvertToInt()) {
        version = versionNode.intValue();
      }
      if (version == null || version != CURRENT_VERSION) {
        warnInvalid(subject, version);
        return null;
      }
      BookingDisplaySettings settings =
          objectMapper.treeToValue(root, StoredPreference.class).settings();
      BookingConfigurationDefaultsManagerImpl.requireValid(settings);
      return settings;
    } catch (JsonProcessingException | RuntimeException ex) {
      warnInvalid(subject, version);
      return null;
    }
  }

  private void warnInvalid(User subject, Integer version) {
    log.warn(
        "Ignoring invalid Booking display preferences for user [{}] (id [{}], version [{}])",
        subject.getUsername(),
        subject.getId(),
        version == null ? "unknown" : version);
  }

  private ResolvedBookingDisplayPreferences resolved(
      BookingDisplaySettings settings, boolean overridden) {
    return new ResolvedBookingDisplayPreferences(
        settings.availabilityWindowStart(),
        settings.availabilityWindowEnd(),
        settings.timezoneMode(),
        settings.customTimezone(),
        institutionClock.getZone().getId(),
        overridden);
  }

  private void requireAccess(User subject, User actor) {
    if (!active(subject) || !active(actor)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private static boolean active(User user) {
    return user != null && user.isEnabled() && !user.isAccountLocked();
  }
}
