import { type OpeningSchedule, openingIntervals } from "@/modules/booking/domain/bookingOpeningHours";
import type { BookingTimeFormat } from "@/modules/booking/domain/bookingTime";
import {
  bookingDateTimeLocale,
  clockChangeOffsetLabel,
  dayMinuteToZonedTime,
  formatPlainDate,
  formatWallClockTime,
  instantToDayMinute,
  parsePlainDate,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";

/**
 * Closed hours in a day timeline: a solid muted base with a diagonal hatch, so they stand apart from the timelines'
 * alternating hour stripes in both themes.
 */
export const CLOSED_HOURS_CLASS_NAME =
  "bg-muted bg-[repeating-linear-gradient(135deg,color-mix(in_oklab,var(--color-muted-foreground)_22%,transparent)_0_2px,transparent_2px_7px)]";

type BaseEvent = {
  id: string;
  startMinute: number;
  endMinute: number;
  startInstant?: string;
  endInstant?: string;
  instrumentTimeZone?: string | null;
};

export type DayTimelineItem = {
  name: string;
  globalId: string | null;
  location?: { name: string; globalId: string };
};

export type DayTimelineEvent =
  | (BaseEvent & {
      kind: "booking";
      privacy: "full";
      title: string;
      bookedBy: string;
      item: DayTimelineItem;
      canEdit: boolean;
      notes?: string;
    })
  | (BaseEvent & {
      kind: "booking";
      privacy: "busy";
    })
  | (BaseEvent & {
      kind: "blockout";
      title: string;
      item: DayTimelineItem;
      createdBy?: string;
      notes?: string;
    });

export type DayTimelineViewState = {
  zoom: number;
  centerMinute: number;
};

export type DayTimelineRange = Readonly<{ startMinute: number; endMinute: number }>;

/** Machine-readable `HH:mm` for `<time dateTime>` attributes; use formatMinuteWithDayOffset for visible text. */
export function formatMinute(date: string, timezone: string, minute: number) {
  return dayMinuteToZonedTime(date, timezone, minute).toPlainTime().toString({ smallestUnit: "minute" });
}

export function dateForMinute(date: string, timezone: string, minute: number) {
  return dayMinuteToZonedTime(date, timezone, minute).toPlainDate().toString();
}

export function formatDayDate(date: string, timeFormat: BookingTimeFormat = "AUTOMATIC") {
  return formatPlainDate(date, bookingDateTimeLocale(timeFormat));
}

/** ` (+1)` / ` (-1)` for a time on a later or earlier date than the one it is read against; empty on the same date. */
export function formatDayOffset(days: number) {
  return days === 0 ? "" : ` (${days > 0 ? "+" : ""}${days})`;
}

export function formatMinuteWithDayOffset(
  date: string,
  timezone: string,
  minute: number,
  timeFormat: BookingTimeFormat = "AUTOMATIC",
) {
  const time = dayMinuteToZonedTime(date, timezone, minute);
  const dayLabel = formatDayOffset(parsePlainDate(date).until(time.toPlainDate()).days);
  // Offsets distinguish both occurrences of the repeated hour on clock-change days.
  const zoneLabel = clockChangeOffsetLabel(time, date);
  return `${formatWallClockTime(time.toPlainTime().toString({ smallestUnit: "minute" }), bookingDateTimeLocale(timeFormat))}${zoneLabel}${dayLabel}`;
}

export function period(
  event: DayTimelineEvent,
  date: string,
  timezone: string,
  timeFormat: BookingTimeFormat = "AUTOMATIC",
) {
  return `${formatMinuteWithDayOffset(date, timezone, event.startMinute, timeFormat)}–${formatMinuteWithDayOffset(date, timezone, event.endMinute, timeFormat)}`;
}

/**
 * Every scheduling-zone opening window intersecting the viewer's `date`, as display-zone times with day offsets.
 * A viewer day can intersect several windows, or none when the instrument is closed.
 */
export function openingWindowsOnDate(
  date: string,
  displayTimezone: string,
  schedule: OpeningSchedule & { timezone: string },
  timeFormat: BookingTimeFormat = "AUTOMATIC",
): Array<{ start: string; end: string }> {
  const format = (instant: string) =>
    formatMinuteWithDayOffset(date, displayTimezone, instantToDayMinute(instant, date, displayTimezone), timeFormat);
  return openingIntervals(schedule, schedule.timezone, zonedDayBounds(date, displayTimezone)).map((opening) => ({
    start: format(opening.start),
    end: format(opening.end),
  }));
}

/**
 * @deprecated Use `openingWindowsOnDate`; a viewer day can intersect several windows or none. Kept only for the
 * untracked `BookingFormTimezonePrototype` story, which shows the first window.
 */
export function openingHoursOnDate(
  date: string,
  displayTimezone: string,
  schedule: OpeningSchedule & { timezone: string },
  timeFormat: BookingTimeFormat = "AUTOMATIC",
): { start: string; end: string } {
  return openingWindowsOnDate(date, displayTimezone, schedule, timeFormat)[0] ?? { start: "", end: "" };
}
