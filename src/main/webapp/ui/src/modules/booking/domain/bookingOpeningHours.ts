import * as v from "valibot";
import type { BookingOpeningHoursEntry } from "@/modules/common/api/v2/generated/models/bookingOpeningHoursEntry";
import { requireBookingValue } from "./bookingMapping";

/** ISO 8601 weekdays, matching Java's DayOfWeek numbering. */
export const ALL_ISO_WEEKDAYS: readonly number[] = [1, 2, 3, 4, 5, 6, 7];

const WALL_TIME = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
export const OpeningHoursStartSchema = v.pipe(v.string(), v.regex(WALL_TIME));
export const OpeningHoursEndSchema = v.union([v.pipe(v.string(), v.regex(WALL_TIME)), v.literal("24:00")]);

export type OpeningHoursEntry = BookingOpeningHoursEntry;

/** Check schedule policy and sort without mutating the wire response. */
export function mapOpeningHours(entries: readonly BookingOpeningHoursEntry[]): OpeningHoursEntry[] {
  if (entries.length < 1 || entries.length > 7) throw new Error("Invalid opening-hours schedule length");
  const days = new Set<number>();
  const mapped = entries.map((entry) => {
    const dayOfWeek = requireBookingValue(entry.dayOfWeek, "dayOfWeek");
    const start = requireBookingValue(entry.start, "start");
    const end = requireBookingValue(entry.end, "end");
    if (
      !Number.isInteger(dayOfWeek) ||
      dayOfWeek < 1 ||
      dayOfWeek > 7 ||
      days.has(dayOfWeek) ||
      !validOpeningHours(start, end)
    )
      throw new Error("Invalid opening-hours entry");
    days.add(dayOfWeek);
    return { dayOfWeek, start, end };
  });
  return mapped.toSorted((left, right) => left.dayOfWeek - right.dayOfWeek);
}

export type OpeningInterval = Pick<BookingOpeningHoursEntry, "start" | "end">;

/** A localized weekday name; 2024-01-01 is a Monday. */
export function formatIsoWeekday(dayOfWeek: number, width: "long" | "short" = "long", locale = "en-US"): string {
  return new Intl.DateTimeFormat(locale, { weekday: width, timeZone: "UTC" }).format(
    new Date(Date.UTC(2024, 0, dayOfWeek)),
  );
}

/** A 24:00 end closes at the next midnight and follows any valid start. */
export function validOpeningHours(start: string, end: string): boolean {
  if (!WALL_TIME.test(start)) return false;
  if (end === "24:00") return true;
  return WALL_TIME.test(end) && start < end;
}

export function isOpenAllDay(hours: OpeningInterval | null | undefined): boolean {
  return hours?.start === "00:00" && hours.end === "24:00";
}

/** The last valid custom pair that Open all day can restore. */
export function customOpeningHours(hours: OpeningInterval): OpeningInterval | undefined {
  return validOpeningHours(hours.start, hours.end) && !isOpenAllDay(hours)
    ? { start: hours.start, end: hours.end }
    : undefined;
}
