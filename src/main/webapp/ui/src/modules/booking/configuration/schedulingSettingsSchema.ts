import * as v from "valibot";
import { mapBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import {
  INCREMENT_UNITS,
  validIncrementForSchedule,
  validMaximumForIncrement,
} from "@/modules/booking/domain/bookingIncrement";
import { requireBookingValue as required } from "@/modules/booking/domain/bookingMapping";
import { ALL_ISO_WEEKDAYS, mapOpeningHours, validOpeningHours } from "@/modules/booking/domain/bookingOpeningHours";
import type { BookingSettingsControllerAdminSettingsDocument } from "@/modules/common/api/v2/generated/models/bookingSettingsControllerAdminSettingsDocument";
import type { BookingSettingsControllerSettingsDocument } from "@/modules/common/api/v2/generated/models/bookingSettingsControllerSettingsDocument";
import { BookingOpeningHoursEntrySchema } from "@/modules/common/api/v2/generated/valibot/schemas";

export const MAX_BUFFER_MINUTES = 10_080;
export const MAX_BOOKING_DURATION_MINUTES = 527_040;
export const schedulingSettingsFieldNames = [
  "slotGranularityMinutes",
  "incrementUnit",
  "openingHours",
  "bufferBeforeMinutes",
  "bufferAfterMinutes",
  "maxBookingDurationMinutes",
  "allowDoubleBooking",
] as const;
export const schedulingSettingsEntries = {
  slotGranularityMinutes: v.pipe(v.number(), v.integer(), v.minValue(1), v.maxValue(MAX_BOOKING_DURATION_MINUTES)),
  incrementUnit: v.picklist(INCREMENT_UNITS),
  openingHours: v.pipe(
    v.array(
      v.pipe(
        BookingOpeningHoursEntrySchema,
        v.forward(
          v.check((entry) => validOpeningHours(entry.start, entry.end)),
          ["end"],
        ),
      ),
    ),
    v.minLength(1),
    v.maxLength(7),
    v.check((entries) => new Set(entries.map((entry) => entry.dayOfWeek)).size === entries.length),
    v.transform((entries) => mapOpeningHours(entries)),
  ),
  bufferBeforeMinutes: v.pipe(v.number(), v.integer(), v.minValue(0), v.maxValue(MAX_BUFFER_MINUTES)),
  bufferAfterMinutes: v.pipe(v.number(), v.integer(), v.minValue(0), v.maxValue(MAX_BUFFER_MINUTES)),
  maxBookingDurationMinutes: v.pipe(v.number(), v.integer(), v.minValue(0), v.maxValue(MAX_BOOKING_DURATION_MINUTES)),
  allowDoubleBooking: v.boolean(),
};
export const SchedulingSettingsSchema = v.pipe(
  v.object(schedulingSettingsEntries),
  v.forward(
    v.check((settings) => validIncrementForSchedule(settings)),
    ["slotGranularityMinutes"],
  ),
  v.forward(
    v.check((settings) => validMaximumForIncrement(settings.maxBookingDurationMinutes, settings)),
    ["maxBookingDurationMinutes"],
  ),
);

export type SchedulingSettings = v.InferOutput<typeof SchedulingSettingsSchema>;
export type BookingSettings = Required<Omit<BookingSettingsControllerSettingsDocument, "slotGranularityMinutes">> &
  Pick<SchedulingSettings, "slotGranularityMinutes">;
export type BookingAdminSettings = Required<
  Omit<BookingSettingsControllerAdminSettingsDocument, "slotGranularityMinutes">
> &
  Pick<SchedulingSettings, "slotGranularityMinutes">;

export function mapBookingSettings(input: BookingSettingsControllerSettingsDocument): BookingSettings {
  const scheduling = {
    slotGranularityMinutes: required(input.slotGranularityMinutes, "slotGranularityMinutes"),
    incrementUnit: required(input.incrementUnit, "incrementUnit"),
    openingHours: mapOpeningHours(required(input.openingHours, "openingHours")),
    bufferBeforeMinutes: required(input.bufferBeforeMinutes, "bufferBeforeMinutes"),
    bufferAfterMinutes: required(input.bufferAfterMinutes, "bufferAfterMinutes"),
    maxBookingDurationMinutes: required(input.maxBookingDurationMinutes, "maxBookingDurationMinutes"),
    allowDoubleBooking: required(input.allowDoubleBooking, "allowDoubleBooking"),
  };
  const settings = v.parse(SchedulingSettingsSchema, scheduling);
  const { overridden: _overridden, ...display } = mapBookingDisplayPreferences({ ...input, overridden: false });
  return { ...settings, ...display };
}

export function mapBookingAdminSettings(input: BookingSettingsControllerAdminSettingsDocument): BookingAdminSettings {
  return {
    ...mapBookingSettings(input),
    configurationVersion: required(input.configurationVersion, "configurationVersion"),
  };
}

export const DEFAULT_SCHEDULING_SETTINGS: SchedulingSettings = {
  slotGranularityMinutes: 15,
  incrementUnit: "MINUTES",
  openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
};
