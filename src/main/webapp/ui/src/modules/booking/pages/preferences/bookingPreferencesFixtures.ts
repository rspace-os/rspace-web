import type { BookingDisplayPreferences } from "@/modules/common/api/v2/generated/models/bookingDisplayPreferences";

export const inheritedBrowserBookingPreferences: BookingDisplayPreferences = {
  availabilityWindowStart: "08:00",
  availabilityWindowEnd: "18:00",
  timezoneMode: "BROWSER",
  customTimezone: null,
  timeFormat: "AUTOMATIC",
  institutionTimezone: "UTC",
  overridden: false,
};

export const customNewYorkBookingPreferences: BookingDisplayPreferences = {
  ...inheritedBrowserBookingPreferences,
  availabilityWindowStart: "09:00",
  availabilityWindowEnd: "17:00",
  timezoneMode: "CUSTOM",
  customTimezone: "America/New_York",
  timeFormat: "AUTOMATIC",
  overridden: true,
};

export const institutionBookingPreferences: BookingDisplayPreferences = {
  ...inheritedBrowserBookingPreferences,
  timezoneMode: "INSTITUTION",
  overridden: true,
};
