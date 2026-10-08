import * as v from "valibot";
import {
  type BookingDisplayPreferencesInput,
  BookingTimezoneModeSchema,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import { ALL_ISO_WEEKDAYS, OpeningHoursSchema } from "@/modules/booking/domain/bookingOpeningHours";

export const MAX_BUFFER_MINUTES = 10_080;
export const MAX_BOOKING_DURATION_MINUTES = 527_040;
export const schedulingSettingsFieldNames = [
  "slotGranularityMinutes",
  "openingHours",
  "bufferBeforeMinutes",
  "bufferAfterMinutes",
  "maxBookingDurationMinutes",
  "allowDoubleBooking",
] as const;
export const schedulingSettingsEntries = {
  slotGranularityMinutes: v.picklist([1, 5, 10, 15]),
  openingHours: OpeningHoursSchema,
  bufferBeforeMinutes: v.pipe(v.number(), v.integer(), v.minValue(0), v.maxValue(MAX_BUFFER_MINUTES)),
  bufferAfterMinutes: v.pipe(v.number(), v.integer(), v.minValue(0), v.maxValue(MAX_BUFFER_MINUTES)),
  maxBookingDurationMinutes: v.pipe(v.number(), v.integer(), v.minValue(0), v.maxValue(MAX_BOOKING_DURATION_MINUTES)),
  allowDoubleBooking: v.boolean(),
};
const bookingDisplayPreferencesEntries = {
  availabilityWindowStart: v.string(),
  availabilityWindowEnd: v.string(),
  timezoneMode: BookingTimezoneModeSchema,
  customTimezone: v.nullable(v.string()),
  institutionTimezone: v.string(),
};

export const SchedulingSettingsSchema = v.pipe(
  v.object(schedulingSettingsEntries),
  v.forward(
    v.check((settings) =>
      validMaximumBookingDuration(settings.maxBookingDurationMinutes, settings.slotGranularityMinutes),
    ),
    ["maxBookingDurationMinutes"],
  ),
);

export const BookingSettingsSchema = v.pipe(
  v.object({
    ...schedulingSettingsEntries,
    ...bookingDisplayPreferencesEntries,
  }),
  v.forward(
    v.check((settings) =>
      validMaximumBookingDuration(settings.maxBookingDurationMinutes, settings.slotGranularityMinutes),
    ),
    ["maxBookingDurationMinutes"],
  ),
);

export const BookingAdminSettingsSchema = v.pipe(
  v.object({
    ...schedulingSettingsEntries,
    ...bookingDisplayPreferencesEntries,
    defaultSharedWith: v.picklist(["ALL_USERS", "SELECTED", "ONLY_ME"]),
    selectedAccessGrantees: v.array(
      v.object({
        kind: v.picklist(["USER", "GROUP"]),
        id: v.number(),
        key: v.string(),
        name: v.string(),
        detail: v.optional(v.nullable(v.string())),
        available: v.boolean(),
      }),
    ),
    configurationVersion: v.number(),
  }),
  v.forward(
    v.check((settings) =>
      validMaximumBookingDuration(settings.maxBookingDurationMinutes, settings.slotGranularityMinutes),
    ),
    ["maxBookingDurationMinutes"],
  ),
);

export type SchedulingSettings = v.InferOutput<typeof SchedulingSettingsSchema>;
export type BookingSettings = v.InferOutput<typeof BookingSettingsSchema>;
export type BookingAdminSettings = v.InferOutput<typeof BookingAdminSettingsSchema>;
export type BookingSettingsInput = SchedulingSettings & BookingDisplayPreferencesInput;

export const DEFAULT_SCHEDULING_SETTINGS: SchedulingSettings = {
  slotGranularityMinutes: 5,
  openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
};

export function validMaximumBookingDuration(maximumMinutes: number, granularityMinutes: number): boolean {
  return (
    maximumMinutes === 0 ||
    (maximumMinutes >= granularityMinutes &&
      maximumMinutes <= MAX_BOOKING_DURATION_MINUTES &&
      maximumMinutes % granularityMinutes === 0)
  );
}
