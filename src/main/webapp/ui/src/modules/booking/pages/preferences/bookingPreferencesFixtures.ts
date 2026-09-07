import type { BookingDisplayPreferencesDocument } from "@/modules/booking/domain/bookingDisplayPreferences";

export const inheritedBrowserBookingPreferences: BookingDisplayPreferencesDocument = {
  availabilityWindowStart: "08:00",
  availabilityWindowEnd: "18:00",
  timezoneMode: "BROWSER",
  customTimezone: null,
  institutionTimezone: "UTC",
  overridden: false,
};

export const customNewYorkBookingPreferences: BookingDisplayPreferencesDocument = {
  ...inheritedBrowserBookingPreferences,
  availabilityWindowStart: "09:00",
  availabilityWindowEnd: "17:00",
  timezoneMode: "CUSTOM",
  customTimezone: "America/New_York",
  overridden: true,
};

export const explicitBrowserBookingPreferences: BookingDisplayPreferencesDocument = {
  ...inheritedBrowserBookingPreferences,
  overridden: true,
};

export const institutionBookingPreferences: BookingDisplayPreferencesDocument = {
  ...inheritedBrowserBookingPreferences,
  timezoneMode: "INSTITUTION",
  overridden: true,
};
