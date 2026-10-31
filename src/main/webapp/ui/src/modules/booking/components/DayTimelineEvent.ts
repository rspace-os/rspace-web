import type { ReactNode } from "react";
import { type OpeningSchedule, openingIntervals } from "@/modules/booking/domain/bookingOpeningHours";
import type { BookingTimeFormat } from "@/modules/booking/domain/bookingTime";
import {
  bookingDateTimeLocale,
  bookingLocale,
  clockChangeOffsetLabel,
  dayMinuteToZonedTime,
  formatPlainDate,
  formatTimeWithDayOffset,
  formatWallClockTime,
  instantToDayMinute,
  parsePlainDate,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import i18n from "@/modules/common/i18n";

/**
 * Closed hours in a day timeline: a solid muted base with a diagonal hatch, so they stand apart from the timelines'
 * alternating hour stripes in both themes.
 */
export const CLOSED_HOURS_CLASS_NAME =
  "bg-muted bg-[repeating-linear-gradient(135deg,color-mix(in_oklab,var(--color-muted-foreground)_22%,transparent)_0_2px,transparent_2px_7px)]";

type BaseEvent = {
  id: string;
  /** Elapsed minutes from the displayed day's start in its display timezone; see instantToDayMinute. */
  startMinute: number;
  /** Same coordinates as startMinute; preserve off-day bounds and clip only for rendering. */
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
  /** Viewport center in the same elapsed-minute coordinates as BaseEvent.startMinute. */
  centerMinute: number;
};

/** Both bounds use BaseEvent's elapsed-minute coordinates, preserving off-day values until rendering. */
export type DayTimelineRange = Readonly<{ startMinute: number; endMinute: number }>;

export type DayTimelineActionRenderers = {
  renderEventActions?: (
    event: Extract<DayTimelineEvent, { kind: "booking" }>,
    period: string,
    timelineEventElement?: HTMLElement | null,
  ) => ReactNode;
  renderBlockoutActions?: (
    event: Extract<DayTimelineEvent, { kind: "blockout" }>,
    period: string,
    timelineEventElement?: HTMLElement | null,
  ) => ReactNode;
};

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

export function formatMinuteWithDayOffset(
  date: string,
  timezone: string,
  minute: number,
  timeFormat: BookingTimeFormat = "AUTOMATIC",
) {
  const time = dayMinuteToZonedTime(date, timezone, minute);
  const locale = bookingDateTimeLocale(timeFormat);
  const days = parsePlainDate(date).until(time.toPlainDate()).days;
  const zoneLabel = clockChangeOffsetLabel(time, date).trim();
  const clock = formatWallClockTime(time.toPlainTime().toString({ smallestUnit: "minute" }), locale);
  const formatted = zoneLabel
    ? i18n.t("booking:time.rangeWithOffset", { period: clock, offset: zoneLabel, lng: bookingLocale() })
    : clock;
  return formatTimeWithDayOffset(formatted, days, locale);
}

export function period(
  eventOrRange: DayTimelineEvent | DayTimelineRange,
  date: string,
  timezone: string,
  timeFormat: BookingTimeFormat = "AUTOMATIC",
) {
  const start = formatMinuteWithDayOffset(date, timezone, eventOrRange.startMinute, timeFormat);
  const end = formatMinuteWithDayOffset(date, timezone, eventOrRange.endMinute, timeFormat);
  const lng = bookingLocale();
  return i18n.t("booking:time.range", { start, end, lng });
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
  return openingRangesOnDate(date, displayTimezone, schedule).map(({ startMinute, endMinute }) => ({
    start: formatMinuteWithDayOffset(date, displayTimezone, startMinute, timeFormat),
    end: formatMinuteWithDayOffset(date, displayTimezone, endMinute, timeFormat),
  }));
}

/** Every scheduling-zone opening interval intersecting the viewer's day, as minute offsets from that day. */
export function openingRangesOnDate(
  date: string,
  displayTimezone: string,
  schedule: OpeningSchedule & { timezone: string },
): Array<{ startMinute: number; endMinute: number }> {
  return openingIntervals(schedule, schedule.timezone, zonedDayBounds(date, displayTimezone)).map((opening) => ({
    startMinute: instantToDayMinute(opening.start, date, displayTimezone),
    endMinute: instantToDayMinute(opening.end, date, displayTimezone),
  }));
}
