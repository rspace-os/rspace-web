import { Temporal } from "@js-temporal/polyfill";
import type { DayTimelineEvent } from "@/modules/booking/components/DayTimelineEvent";
import { toTimelineEvent } from "@/modules/booking/components/toTimelineEvent";
import { layoutVerticalTimelineEvents } from "@/modules/booking/components/verticalTimelineLayout";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { parsePlainDate } from "@/modules/booking/domain/bookingTime";
import { eventsOn } from "./calendarLayoutUtils";

export const WEEK_GRID_DAY_MINUTES = 24 * 60;
export const WEEK_GRID_PIXELS_PER_HOUR = 56;
/** Short bookings still get a card tall enough to read and hit; lanes are assigned with this extent. */
export const WEEK_GRID_MINIMUM_VISUAL_MINUTES = 30;

export type WeekGridEvent = {
  booking: BookingListDocument;
  /** The day's timeline event; its elapsed minutes keep the card's labels identical to the other layouts. */
  event: DayTimelineEvent;
  /** Wall-clock minutes on the shared hour axis, clipped to the displayed day. */
  startMinute: number;
  endMinute: number;
  lane: number;
  laneCount: number;
  continuesBefore: boolean;
  continuesAfter: boolean;
};

/** Minutes after `day`'s wall-clock midnight, so every column shares one hour axis across DST changes. */
export function wallClockMinute(instant: string, day: string, timezone: string): number {
  const local = Temporal.Instant.from(instant).toZonedDateTimeISO(timezone).toPlainDateTime();
  return local.since(parsePlainDate(day).toPlainDateTime(), { largestUnit: "hours" }).total("minutes");
}

/** Clip each booking to `day` on the wall-clock axis and lay overlapping bookings out side by side. */
export function layoutWeekGridDay(
  events: readonly BookingListDocument[],
  day: string,
  timezone: string,
): WeekGridEvent[] {
  const segments = new Map(
    eventsOn(events, day, timezone).map((booking) => {
      const event = toTimelineEvent(booking, day, timezone);
      const startMinute = wallClockMinute(booking.start, day, timezone);
      const wallEndMinute = wallClockMinute(booking.end, day, timezone);
      // A booking that ends in the repeated hour of a DST fall-back can end before it starts on the wall clock.
      const endMinute =
        wallEndMinute > startMinute
          ? wallEndMinute
          : startMinute + (Date.parse(booking.end) - Date.parse(booking.start)) / 60_000;
      return [event.id, { booking, event, startMinute, endMinute }] as const;
    }),
  );
  return layoutVerticalTimelineEvents(
    Array.from(segments.values(), ({ event, startMinute, endMinute }) => ({
      ...event,
      startMinute,
      endMinute: Math.max(endMinute, startMinute + WEEK_GRID_MINIMUM_VISUAL_MINUTES),
    })),
    WEEK_GRID_DAY_MINUTES,
  ).flatMap((positioned) => {
    const segment = segments.get(positioned.event.id);
    if (!segment) return [];
    return [
      {
        ...positioned,
        booking: segment.booking,
        event: segment.event,
        continuesAfter: segment.endMinute > WEEK_GRID_DAY_MINUTES,
      },
    ];
  });
}

/**
 * Lanes per overlap group before the rest collapse into one "+N more" slot. A day column is about 200px
 * at 1440px, so a third lane leaves cards too narrow for a readable title next to the expand control.
 */
export const WEEK_GRID_MAX_LANES = 2;

export type WeekGridOverflow = {
  startMinute: number;
  endMinute: number;
  lane: number;
  laneCount: number;
  hiddenCount: number;
  /** The collapsed bookings, in start order, for the slot to list. */
  hidden: readonly WeekGridEvent[];
};

/**
 * Keep a crowded overlap group readable: its first lanes stay and the others collapse into one slot
 * spanning their times, so eight simultaneous bookings do not become eight unreadable slivers.
 */
export function collapseWeekGridLanes(
  events: readonly WeekGridEvent[],
  maxLanes = WEEK_GRID_MAX_LANES,
): { visible: WeekGridEvent[]; overflows: WeekGridOverflow[] } {
  const visible: WeekGridEvent[] = [];
  const overflows: WeekGridOverflow[] = [];
  let group: WeekGridEvent[] = [];
  let groupEnd = Number.NEGATIVE_INFINITY;
  const finishGroup = () => {
    if (group.length > 0 && group[0].laneCount > maxLanes) {
      const hidden = group.filter(({ lane }) => lane >= maxLanes - 1);
      visible.push(
        ...group.filter(({ lane }) => lane < maxLanes - 1).map((event) => ({ ...event, laneCount: maxLanes })),
      );
      overflows.push({
        startMinute: Math.min(...hidden.map(({ startMinute }) => startMinute)),
        endMinute: Math.max(...hidden.map(({ endMinute }) => endMinute)),
        lane: maxLanes - 1,
        laneCount: maxLanes,
        hiddenCount: hidden.length,
        hidden,
      });
    } else {
      visible.push(...group);
    }
    group = [];
    groupEnd = Number.NEGATIVE_INFINITY;
  };
  // Positioned events arrive sorted by start, and each overlap group is contiguous, as the lane layout builds them.
  for (const event of events) {
    if (event.startMinute >= groupEnd) finishGroup();
    group.push(event);
    groupEnd = Math.max(groupEnd, event.endMinute);
  }
  finishGroup();
  return { visible, overflows };
}

export function weekGridEventBox({
  startMinute,
  endMinute,
  lane,
  laneCount,
}: Pick<WeekGridEvent, "startMinute" | "endMinute" | "lane" | "laneCount">) {
  const pixelsPerMinute = WEEK_GRID_PIXELS_PER_HOUR / 60;
  return {
    top: startMinute * pixelsPerMinute,
    height: (endMinute - startMinute) * pixelsPerMinute,
    left: `${(lane / laneCount) * 100}%`,
    width: `${100 / laneCount}%`,
  };
}
