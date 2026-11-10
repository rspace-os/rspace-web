import * as v from "valibot";
import { mapBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { requireBookingValue as required } from "@/modules/booking/domain/bookingMapping";
import { ALL_ISO_WEEKDAYS, mapOpeningHours, validOpeningHours } from "@/modules/booking/domain/bookingOpeningHours";
import type { BookingSettingsControllerAdminSettingsDocument } from "@/modules/common/api/v2/generated/models/bookingSettingsControllerAdminSettingsDocument";
import type { BookingSettingsControllerSettingsDocument } from "@/modules/common/api/v2/generated/models/bookingSettingsControllerSettingsDocument";
import { BookingOpeningHoursEntrySchema } from "@/modules/common/api/v2/generated/valibot/schemas";

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
    v.check((settings) =>
      validMaximumBookingDuration(settings.maxBookingDurationMinutes, settings.slotGranularityMinutes),
    ),
    ["maxBookingDurationMinutes"],
  ),
);

export type SchedulingSettings = v.InferOutput<typeof SchedulingSettingsSchema>;
export type BookingSettings = Required<Omit<BookingSettingsControllerSettingsDocument, "slotGranularityMinutes">> &
  Pick<SchedulingSettings, "slotGranularityMinutes">;
export type BookingAdminSettings = BookingSettings &
  Required<Pick<BookingSettingsControllerAdminSettingsDocument, "configurationVersion" | "defaultSharedWith">> & {
    selectedAccessGrantees: {
      kind: "USER" | "GROUP";
      id: number;
      key: string;
      name: string;
      detail?: string | null;
      available: boolean;
    }[];
  };

export function mapBookingSettings(input: BookingSettingsControllerSettingsDocument): BookingSettings {
  const scheduling = {
    slotGranularityMinutes: required(input.slotGranularityMinutes, "slotGranularityMinutes"),
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
    defaultSharedWith: v.parse(
      v.picklist(["ALL_USERS", "SELECTED", "ONLY_ME"]),
      required(input.defaultSharedWith, "defaultSharedWith"),
    ),
    selectedAccessGrantees: required(input.selectedAccessGrantees, "selectedAccessGrantees").map((grantee) => ({
      ...grantee,
      kind: v.parse(v.picklist(["USER", "GROUP"]), required(grantee.kind, "selectedAccessGrantees.kind")),
      id: required(grantee.id, "selectedAccessGrantees.id"),
      key: required(grantee.key, "selectedAccessGrantees.key"),
      name: required(grantee.name, "selectedAccessGrantees.name"),
      available: required(grantee.available, "selectedAccessGrantees.available"),
    })),
  };
}

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
