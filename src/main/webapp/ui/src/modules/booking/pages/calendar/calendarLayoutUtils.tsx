import * as React from "react";
import type { DayTimelineEvent } from "@/modules/booking/components/DayTimeline";
import type { ExpandedEventEditController } from "@/modules/booking/components/ExpandedEventCard";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import {
  addCalendarDays,
  sliceAcrossZonedDay,
  wallClockToDayMinute,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import { BookingActions } from "./BookingEventActions";

export const calendarLayouts = ["time-grid", "resources", "agenda"] as const;
export type CalendarLayout = (typeof calendarLayouts)[number];
export const calendarViews = ["day", "week", "month"] as const;
export type CalendarView = (typeof calendarViews)[number];
export type BookingCalendarResource = NonNullable<BookingListDocument["target"]>;

export function utcDate(date: string): Date {
  return new Date(`${date}T12:00:00Z`);
}

export function firstOfMonth(date: string): string {
  return `${date.slice(0, 8)}01`;
}

export function startOfWeek(date: string): string {
  return addCalendarDays(date, -((utcDate(date).getUTCDay() + 6) % 7));
}

export function monthGridStart(date: string): string {
  return startOfWeek(firstOfMonth(date));
}

export function monthDayCount(date: string): number {
  const value = utcDate(firstOfMonth(date));
  return new Date(Date.UTC(value.getUTCFullYear(), value.getUTCMonth() + 1, 0)).getUTCDate();
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

export function nextFreeRange(
  events: readonly BookingListDocument[],
  date: string,
  timezone: string,
  startWindow: number,
  endWindow: number,
  increment: number,
  // Day-minute ranges, such as closed hours, that a proposal must also avoid.
  unavailable: readonly { startMinute: number; endMinute: number }[] = [],
): { startMinute: number; endMinute: number } | undefined {
  startWindow = wallClockToDayMinute(date, timezone, startWindow);
  endWindow = wallClockToDayMinute(date, timezone, endWindow);
  if (endWindow <= startWindow) return undefined;
  const duration = Math.min(60, endWindow - startWindow);
  const firstStart = Math.ceil(startWindow / increment) * increment;
  const occupied = [
    ...events.map((event) => sliceAcrossZonedDay(event.start, event.end, date, timezone)),
    ...unavailable,
  ];
  for (let startMinute = firstStart; startMinute + duration <= endWindow; startMinute += increment) {
    const endMinute = startMinute + duration;
    if (!occupied.some((event) => event.startMinute < endMinute && event.endMinute > startMinute)) {
      return { startMinute, endMinute };
    }
  }
  return undefined;
}

export function shiftDate(date: string, view: CalendarView, delta: number): string {
  if (view === "day") return addCalendarDays(date, delta);
  if (view === "week") return addCalendarDays(date, delta * 7);
  const value = utcDate(date);
  return new Date(Date.UTC(value.getUTCFullYear(), value.getUTCMonth() + delta, 1)).toISOString().slice(0, 10);
}

export function occursOn(event: BookingListDocument, date: string, timezone: string): boolean {
  const bounds = zonedDayBounds(date, timezone);
  return Date.parse(event.start) < Date.parse(bounds.end) && Date.parse(event.end) > Date.parse(bounds.start);
}

export function eventsOn(events: readonly BookingListDocument[], date: string, timezone: string) {
  return events
    .filter((event) => occursOn(event, date, timezone))
    .toSorted((left, right) => left.start.localeCompare(right.start));
}

export function formatDate(date: string, options: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(undefined, { ...options, timeZone: "UTC" }).format(utcDate(date));
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
  return (
    timelineEvent: Extract<DayTimelineEvent, { kind: "booking" }>,
    _period: string,
    timelineEventElement?: HTMLElement | null,
    editController?: ExpandedEventEditController,
  ) => {
    const event = events.find(({ id }) => String(id) === timelineEvent.id);
    return event ? (
      <BookingActions
        event={event}
        timezone={timezone}
        timelineDate={timelineDate}
        timelineEventElement={timelineEventElement}
        editController={editController}
      />
    ) : null;
  };
}

export function blockoutActionsFor(events: readonly BookingListDocument[], timezone: string, timelineDate: string) {
  return (
    timelineEvent: Extract<DayTimelineEvent, { kind: "blockout" }>,
    _period: string,
    timelineEventElement?: HTMLElement | null,
    editController?: ExpandedEventEditController,
  ) => {
    const event = events.find(({ id }) => String(id) === timelineEvent.id);
    return event ? (
      <BookingActions
        event={event}
        timezone={timezone}
        timelineDate={timelineDate}
        timelineEventElement={timelineEventElement}
        editController={editController}
      />
    ) : null;
  };
}
