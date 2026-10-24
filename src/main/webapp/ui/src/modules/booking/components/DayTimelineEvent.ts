import type { ReactNode } from "react";
import { dayMinuteToZonedTime, parsePlainDate, zonedDayBounds } from "@/modules/booking/domain/bookingTime";

const DAY_MINUTES = 24 * 60;

type BaseEvent = {
  id: string;
  /** Elapsed minutes from the displayed day's start in its display timezone; see instantToDayMinute. */
  startMinute: number;
  /** Same coordinates as startMinute; preserve off-day bounds and clip only for rendering. */
  endMinute: number;
};

export type DayTimelineItem = {
  name: string;
  globalId: string;
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
  renderEventActions?: (event: Extract<DayTimelineEvent, { kind: "booking" }>, period: string) => ReactNode;
  renderBlockoutActions?: (event: Extract<DayTimelineEvent, { kind: "blockout" }>, period: string) => ReactNode;
};

export function formatMinute(date: string, timezone: string, minute: number) {
  return dayMinuteToZonedTime(date, timezone, minute).toPlainTime().toString({ smallestUnit: "minute" });
}

export function dateForMinute(date: string, timezone: string, minute: number) {
  return dayMinuteToZonedTime(date, timezone, minute).toPlainDate().toString();
}

export function formatDayDate(date: string) {
  const [year, month, day] = date.split("-");
  return `${day}-${month}-${year}`;
}

export function formatMinuteWithDayOffset(date: string, timezone: string, minute: number) {
  const time = dayMinuteToZonedTime(date, timezone, minute);
  const offset = parsePlainDate(date).until(time.toPlainDate()).days;
  const dayLabel = offset === 0 ? "" : ` (${offset > 0 ? "+" : ""}${offset})`;
  // Offsets distinguish both occurrences of the repeated hour on clock-change days.
  const zoneLabel = zonedDayBounds(date, timezone).elapsedMinutes === DAY_MINUTES ? "" : ` ${time.offset}`;
  return `${time.toPlainTime().toString({ smallestUnit: "minute" })}${zoneLabel}${dayLabel}`;
}

export function period(
  eventOrRange: DayTimelineEvent | DayTimelineRange,
  date: string,
  timezone: string,
  separator = "–",
) {
  const start = formatMinuteWithDayOffset(date, timezone, eventOrRange.startMinute);
  const end = formatMinuteWithDayOffset(date, timezone, eventOrRange.endMinute);
  return `${start}${separator}${end}`;
}
