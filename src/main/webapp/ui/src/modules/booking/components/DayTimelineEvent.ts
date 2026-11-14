import { dayMinuteToZonedTime, parsePlainDate, zonedDayBounds } from "@/modules/booking/domain/bookingTime";

export const DAY_MINUTES = 24 * 60;

export type BaseEvent = {
  id: string;
  startMinute: number;
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
  centerMinute: number;
};

export type DayTimelineRange = Readonly<{ startMinute: number; endMinute: number }>;

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

export function period(range: DayTimelineRange, date: string, timezone: string) {
  const start = formatMinuteWithDayOffset(date, timezone, range.startMinute);
  const end = formatMinuteWithDayOffset(date, timezone, range.endMinute);
  return `${start}–${end}`;
}
