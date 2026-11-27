import { Temporal } from "@js-temporal/polyfill";
import * as React from "react";
import type { DayTimelineEvent } from "@/modules/booking/components/DayTimeline";
import { proposeDayRange, type TimelinePolicy } from "@/modules/booking/creation/timelineRange";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { incrementRule } from "@/modules/booking/domain/bookingIncrement";
import {
  addCalendarDays,
  bookingDateTimeLocale,
  bookingDisplayLocale,
  sliceAcrossZonedDay,
  wallClockToDayMinute,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import { firstDayOfWeek } from "@/modules/common/i18n/localeFormat";
import { epochMillisecondsOf } from "@/modules/common/time/instant";
import { BookingActions } from "./BookingEventActions";

export const calendarLayouts = ["time-grid", "resources", "agenda", "availability"] as const;
export type CalendarLayout = (typeof calendarLayouts)[number];
export const calendarViews = ["day", "week", "month"] as const;
export type CalendarView = (typeof calendarViews)[number];
export const allowedViews: Record<CalendarLayout, readonly CalendarView[]> = {
  "time-grid": calendarViews,
  resources: ["day", "week"],
  agenda: calendarViews,
  availability: ["day"],
};
export type BookingCalendarResource = NonNullable<BookingListDocument["target"]> & {
  location?: { name: string; globalId: string } | null;
};

export function resourceLocation(resource: BookingCalendarResource) {
  if (resource.location !== undefined) return resource.location ?? undefined;
  const parent = resource.value.parentContainer?.value;
  return typeof parent === "object" && parent !== null ? parent : undefined;
}

/** Epoch milliseconds at noon UTC on `date`, for formatting a calendar date with `timeZone: "UTC"`. */
function utcNoon(date: string): number {
  return Temporal.PlainDate.from(date).toZonedDateTime({ timeZone: "UTC", plainTime: "12:00" }).epochMilliseconds;
}

export function firstOfMonth(date: string): string {
  return `${date.slice(0, 8)}01`;
}

/** The first day of the week holding `date`; `weekStart` is an ISO weekday and defaults to the browser region's, like the clock. */
export function startOfWeek(date: string, weekStart = firstDayOfWeek(bookingDateTimeLocale())): string {
  const value = Temporal.PlainDate.from(date);
  return value.subtract({ days: (value.dayOfWeek - weekStart + 7) % 7 }).toString();
}

export function monthGridStart(date: string): string {
  return startOfWeek(firstOfMonth(date));
}

function monthDayCount(date: string): number {
  return Temporal.PlainDate.from(firstOfMonth(date)).daysInMonth;
}

export function datesFrom(start: string, count: number): readonly string[] {
  return Array.from({ length: count }, (_, index) => addCalendarDays(start, index));
}

export function calendarDates(date: string, view: CalendarView): readonly string[] {
  if (view === "day") return [date];
  if (view === "week") return datesFrom(startOfWeek(date), 7);
  return datesFrom(monthGridStart(date), 42);
}

export function periodDates(date: string, view: CalendarView): readonly string[] {
  if (view !== "month") return calendarDates(date, view);
  return datesFrom(firstOfMonth(date), monthDayCount(date));
}

/**
 * The first free proposal starts at the first whole minute in a gap, with a whole number of increments.
 */
export function nextFreeRange(
  events: readonly BookingListDocument[],
  date: string,
  timezone: string,
  startWindow: number,
  endWindow: number,
  policy: TimelinePolicy,
  // Day-minute ranges, such as closed hours, that a proposal must also avoid.
  unavailable: readonly { startMinute: number; endMinute: number }[] = [],
): { startMinute: number; endMinute: number } | undefined {
  startWindow = wallClockToDayMinute(date, timezone, startWindow);
  endWindow = wallClockToDayMinute(date, timezone, endWindow);
  if (endWindow <= startWindow) return undefined;
  if (!Number.isFinite(policy.slotGranularityMinutes) || policy.slotGranularityMinutes <= 0) return undefined;
  const rule = incrementRule(policy);
  const dayMinutes = zonedDayBounds(date, timezone).elapsedMinutes;
  if (rule.kind === "days" || rule.minutes > dayMinutes) {
    const nominal = rule.kind === "days" ? rule.days * 1440 : rule.minutes;
    return proposeDayRange({ startMinute: startWindow, endMinute: startWindow + nominal }, date, timezone, policy);
  }
  const preferredDuration = Math.ceil(Math.min(60, endWindow - startWindow) / rule.minutes) * rule.minutes;
  const maximumDuration = policy.maxBookingDurationMinutes > 0 ? policy.maxBookingDurationMinutes : Infinity;
  const occupied = [
    ...events.map((event) => sliceAcrossZonedDay(event.start, event.end, date, timezone)),
    ...unavailable,
  ].toSorted((left, right) => left.startMinute - right.startMinute);
  // The first free minute in a gap leaves the most room for whole increments.
  const gaps: Array<{ from: number; to: number }> = [];
  let cursor = Math.ceil(startWindow);
  for (const block of occupied) {
    if (block.startMinute >= endWindow) break;
    if (block.startMinute > cursor) gaps.push({ from: cursor, to: Math.min(block.startMinute, endWindow) });
    cursor = Math.max(cursor, Math.ceil(block.endMinute));
  }
  if (cursor < endWindow) gaps.push({ from: cursor, to: endWindow });
  for (const { from, to } of gaps) {
    const duration = Math.floor(Math.min(preferredDuration, to - from, maximumDuration) / rule.minutes) * rule.minutes;
    if (duration <= 0) continue;
    return { startMinute: from, endMinute: from + duration };
  }
  return undefined;
}

export function shiftDate(date: string, view: CalendarView, delta: number): string {
  if (view === "day") return addCalendarDays(date, delta);
  if (view === "week") return addCalendarDays(date, delta * 7);
  return Temporal.PlainDate.from(date).with({ day: 1 }).add({ months: delta }).toString();
}

export function occursOn(event: BookingListDocument, date: string, timezone: string): boolean {
  const bounds = zonedDayBounds(date, timezone);
  return (
    event.start.epochMilliseconds < epochMillisecondsOf(bounds.end) &&
    event.end.epochMilliseconds > epochMillisecondsOf(bounds.start)
  );
}

export function eventsOn(
  events: readonly BookingListDocument[],
  date: string,
  timezone: string,
  editors?: ReadonlyMap<number, BookingListDocument>,
) {
  return events
    .filter((event) => occursOn(editors?.get(event.id) ?? event, date, timezone))
    .toSorted((left, right) => Temporal.Instant.compare(left.start, right.start));
}

export function timelineEditorPositions(
  editors: ReadonlyMap<number, BookingListDocument> | undefined,
  date: string,
  timezone: string,
) {
  return new Map(
    [...(editors?.values() ?? [])].map((event) => [
      String(event.id),
      sliceAcrossZonedDay(event.start, event.end, date, timezone),
    ]),
  );
}

export function formatDate(date: string, options: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(bookingDisplayLocale(), { ...options, timeZone: "UTC" }).format(utcNoon(date));
}

export function scrollCalendarWithArrowKeys(event: React.KeyboardEvent<HTMLElement>) {
  if (
    event.defaultPrevented ||
    event.target !== event.currentTarget ||
    (event.key !== "ArrowLeft" && event.key !== "ArrowRight")
  )
    return;
  event.preventDefault();
  event.currentTarget.scrollBy({ left: event.key === "ArrowLeft" ? -240 : 240, behavior: "smooth" });
}

export function useScrollToToday(date: string, view: CalendarView, today: string) {
  const scrollRegionRef = React.useRef<HTMLElement>(null);
  React.useLayoutEffect(() => {
    if (view === "day") return;
    const scrollRegion = scrollRegionRef.current;
    const todayElement = scrollRegion?.querySelector<HTMLElement>(`[data-calendar-date="${today}"]`);
    if (!scrollRegion || !todayElement) return;
    scrollRegion.scrollTo({
      left: todayElement.offsetLeft - (scrollRegion.clientWidth - todayElement.clientWidth) / 2,
      top: todayElement.offsetTop - (scrollRegion.clientHeight - todayElement.clientHeight) / 2,
    });
  }, [date, today, view]);
  return scrollRegionRef;
}

export function actionsFor(events: readonly BookingListDocument[], timezone: string, timelineDate: string) {
  return (timelineEvent: Pick<DayTimelineEvent, "id">, _period: string, timelineEventElement?: HTMLElement | null) => {
    const event = events.find(({ id }) => String(id) === timelineEvent.id);
    return event ? (
      <BookingActions
        event={event}
        timezone={timezone}
        timelineDate={timelineDate}
        timelineEventElement={timelineEventElement}
      />
    ) : null;
  };
}
