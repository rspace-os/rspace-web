// PROTOTYPE ONLY. Explore the recurring booking workflow (plan 004) on top of the production booking form,
// day timeline, item card and lists. Network reads are served by story-local MSW handlers; nothing is written.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */

import { Temporal } from "@js-temporal/polyfill";
import type { EditableBooking } from "@/modules/booking/creation/BookingForm";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import type { Booking, BookingListDocument } from "@/modules/booking/domain/booking";
import { coversInterval } from "@/modules/booking/domain/bookingOpeningHours";
import { type BookingWindowDraft, isPlainDate } from "@/modules/booking/domain/bookingTime";

// ---------------------------------------------------------------------------
// Fixtures. The item's timezone differs from the viewer's display timezone: the rule expands in the
// display timezone (plan 004, contract 2) while opening hours are checked per occurrence in the item's.
// ---------------------------------------------------------------------------

export const DATE = "2026-10-05"; // Monday
export const CONFIGURATION_TIMEZONE = "Europe/London";
export const DISPLAY_TIMEZONE = "Europe/Berlin";
export const MAX_OCCURRENCES = 52;
export const CANCELLATION_REASON_MAX_LENGTH = 500;

export const item: BookableItemOption = {
  configurationId: 9001,
  targetId: 123,
  globalId: "IN123",
  name: "Confocal microscope",
  timezone: CONFIGURATION_TIMEZONE,
  slotGranularityMinutes: 15,
  incrementUnit: "MINUTES",
  openingHours: [1, 2, 3, 4, 5].map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "18:00" })),
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 240,
  allowDoubleBooking: false,
};

const target: Booking["target"] = {
  relationTo: "instruments",
  value: {
    id: item.targetId,
    name: item.name,
    deleted: false,
    parentContainer: {
      relationTo: "containers",
      globalId: "IC456",
      value: { id: 456, name: "Imaging lab", globalId: "IC456" },
    },
  },
  globalId: item.globalId,
};

/** Display-timezone wall clock, as the form reports it. */
export const INITIAL_WINDOW: BookingWindowDraft = {
  startDate: DATE,
  startTime: "10:00",
  endDate: DATE,
  endTime: "11:30",
};

function instantAt(date: string, time: string): string {
  return Temporal.PlainDateTime.from(`${date}T${time}`).toZonedDateTime(DISPLAY_TIMEZONE).toInstant().toString();
}

function fixtureBooking(input: {
  id: number;
  start: string;
  end: string;
  kind?: Booking["kind"];
  privacy?: "full" | "busy";
  bookedBy?: string;
  createdBy?: string;
  purpose?: string;
  own?: boolean;
}): Booking {
  const base = {
    id: input.id,
    version: 0,
    target,
    timezone: CONFIGURATION_TIMEZONE,
    start: input.start,
    end: input.end,
    state: "CONFIRMED" as const,
    kind: input.kind ?? "BOOKING",
    createdAt: "2026-09-01T09:00:00Z",
    updatedAt: "2026-09-01T09:00:00Z",
  };
  if (input.privacy === "busy") {
    return {
      ...base,
      privacy: "busy",
      purpose: null,
      cancellationReason: null,
      bookedBy: null,
      canEdit: false,
      canCancel: false,
    };
  }
  return {
    ...base,
    privacy: "full",
    purpose: input.purpose ?? null,
    cancellationReason: null,
    bookedBy: input.bookedBy ?? null,
    createdBy: input.createdBy ?? input.bookedBy ?? null,
    canEdit: input.own === true,
    canCancel: input.own === true,
  };
}

export function toListDocument(booking: Booking): BookingListDocument {
  return { ...booking, canViewConfiguration: true, requesterId: booking.privacy === "full" ? 84 : null };
}

/** Existing events on the item. Served to the day timeline by MSW and used by the simulated dry run. */
export const storedBookings: readonly Booking[] = [
  fixtureBooking({ id: 501, start: instantAt(DATE, "13:00"), end: instantAt(DATE, "14:00"), privacy: "busy" }),
  fixtureBooking({
    id: 502,
    start: instantAt(DATE, "16:00"),
    end: instantAt(DATE, "17:00"),
    kind: "MAINTENANCE",
    createdBy: "Grace Hopper (grace)",
    purpose: "Laser alignment",
  }),
  fixtureBooking({
    id: 503,
    start: instantAt("2026-10-14", "09:30"),
    end: instantAt("2026-10-14", "11:00"),
    bookedBy: "Grace Hopper (grace)",
    purpose: "Calibration",
  }),
  fixtureBooking({
    id: 504,
    start: instantAt("2026-11-02", "10:00"),
    end: instantAt("2026-11-02", "12:00"),
    bookedBy: "Katherine Johnson (katherine)",
    purpose: "Sample screening",
  }),
];

export function listEnvelope(docs: readonly Booking[]) {
  return {
    docs,
    totalDocs: docs.length,
    limit: 100,
    page: 1,
    pagingCounter: 1,
    totalPages: 1,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  };
}

// ---------------------------------------------------------------------------
// Recurrence grammar (plan 004, contract 1): DAILY | WEEKLY, interval 1..4, weekday set for WEEKLY,
// count 2..52 or an inclusive until date, both within one year of occurrence 1.
// ---------------------------------------------------------------------------

type Weekday = 1 | 2 | 3 | 4 | 5 | 6 | 7;
export const WEEKDAYS: ReadonlyArray<{ value: Weekday; label: string; short: string }> = [
  { value: 1, label: "Monday", short: "Mon" },
  { value: 2, label: "Tuesday", short: "Tue" },
  { value: 3, label: "Wednesday", short: "Wed" },
  { value: 4, label: "Thursday", short: "Thu" },
  { value: 5, label: "Friday", short: "Fri" },
  { value: 6, label: "Saturday", short: "Sat" },
  { value: 7, label: "Sunday", short: "Sun" },
];

export type RecurrenceRule = {
  frequency: "NONE" | "DAILY" | "WEEKLY";
  interval: number;
  weekdays: Weekday[];
  ends: { kind: "count"; count: number } | { kind: "until"; until: string };
};

export const DEFAULT_RULE: RecurrenceRule = {
  frequency: "NONE",
  interval: 1,
  weekdays: [1, 3],
  ends: { kind: "count", count: 12 },
};

export const CONFLICTING_RULE: RecurrenceRule = {
  frequency: "WEEKLY",
  interval: 1,
  weekdays: [1, 3, 6],
  ends: { kind: "count", count: 12 },
};

export const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));
const isTime = (value: string) => /^\d{2}:\d{2}$/.test(value);

export function firstWeekday(draft: BookingWindowDraft): Weekday | undefined {
  return isPlainDate(draft.startDate) ? (Temporal.PlainDate.from(draft.startDate).dayOfWeek as Weekday) : undefined;
}

export function sameRule(left: RecurrenceRule, right: RecurrenceRule): boolean {
  const key = (rule: RecurrenceRule) => JSON.stringify({ ...rule, weekdays: [...rule.weekdays].sort((a, b) => a - b) });
  return key(left) === key(right);
}

export function ruleProblem(rule: RecurrenceRule, draft: BookingWindowDraft): string | undefined {
  if (rule.frequency === "NONE" || !isPlainDate(draft.startDate)) return undefined;
  if (rule.ends.kind === "count") {
    if (!Number.isInteger(rule.ends.count) || rule.ends.count < 2 || rule.ends.count > MAX_OCCURRENCES)
      return `Enter between 2 and ${MAX_OCCURRENCES} occurrences.`;
  } else {
    if (!isPlainDate(rule.ends.until)) return "Enter the last occurrence date.";
    const first = Temporal.PlainDate.from(draft.startDate);
    const until = Temporal.PlainDate.from(rule.ends.until);
    if (Temporal.PlainDate.compare(until, first) <= 0) return "The last occurrence must be after the first.";
    if (Temporal.PlainDate.compare(until, first.add({ years: 1 })) > 0) return "A series can run for at most one year.";
  }
  if (isPlainDate(draft.endDate) && isTime(draft.startTime) && isTime(draft.endTime)) {
    const rows = expandRule(rule, draft);
    if (rule.ends.kind === "count" && rows.length < rule.ends.count) return "All occurrences must fit within one year.";
    if (rows.length > MAX_OCCURRENCES) return `A series can contain at most ${MAX_OCCURRENCES} occurrences.`;
    if (rows.length < 2) return "Choose at least two occurrences.";
  }
  return undefined;
}

type OccurrenceWindow = { position: number; start: string; end: string; dstShifted: boolean };

function zonedDisplay(date: Temporal.PlainDate, time: string): Temporal.ZonedDateTime {
  // A nonexistent time shifts forward by the gap; an ambiguous one takes the earlier instant (contract 3).
  return date.toPlainDateTime(Temporal.PlainTime.from(time)).toZonedDateTime(DISPLAY_TIMEZONE, {
    disambiguation: "compatible",
  });
}

const wallTime = (value: Temporal.ZonedDateTime) => value.toPlainTime().toString({ smallestUnit: "minute" });

/**
 * Expands a rule in the display timezone. Fixture-only: in production the server is the only place that
 * expands rules, and the frontend renders whatever the dry run returns.
 */
function expandRule(rule: RecurrenceRule, draft: BookingWindowDraft): OccurrenceWindow[] {
  if (!isPlainDate(draft.startDate) || !isPlainDate(draft.endDate)) return [];
  if (!isTime(draft.startTime) || !isTime(draft.endTime)) return [];
  const first = Temporal.PlainDate.from(draft.startDate);
  const span = first.until(Temporal.PlainDate.from(draft.endDate)).days;
  if (span < 0) return [];
  const horizon = first.add({ years: 1 });
  const until =
    rule.ends.kind === "until" &&
    isPlainDate(rule.ends.until) &&
    Temporal.PlainDate.compare(rule.ends.until, horizon) < 0
      ? Temporal.PlainDate.from(rule.ends.until)
      : horizon;
  const limit =
    rule.frequency === "NONE"
      ? 1
      : rule.ends.kind === "count"
        ? clamp(rule.ends.count, 1, MAX_OCCURRENCES)
        : MAX_OCCURRENCES + 1;
  const interval = clamp(rule.interval, 1, 4);
  // Occurrence 1 is the entered window; its weekday is always in the set.
  const weekdays = new Set<number>(rule.frequency === "WEEKLY" ? [...rule.weekdays, first.dayOfWeek] : []);
  const result: OccurrenceWindow[] = [];
  for (let day = 0; day <= 366 && result.length < limit; day += 1) {
    const date = first.add({ days: day });
    if (Temporal.PlainDate.compare(date, until) > 0) break;
    const inCycle =
      rule.frequency === "NONE"
        ? day === 0
        : rule.frequency === "DAILY"
          ? day % interval === 0
          : weekdays.has(date.dayOfWeek) && Math.floor(day / 7) % interval === 0;
    if (!inCycle) continue;
    const start = zonedDisplay(date, draft.startTime);
    const end = zonedDisplay(date.add({ days: span }), draft.endTime);
    if (Temporal.Instant.compare(start.toInstant(), end.toInstant()) >= 0) continue;
    result.push({
      position: result.length + 1,
      start: start.toInstant().toString(),
      end: end.toInstant().toString(),
      dstShifted: wallTime(start) !== draft.startTime || wallTime(end) !== draft.endTime,
    });
    if (rule.frequency === "NONE") break;
  }
  return result;
}

type OccurrenceStatus = "ok" | "overlap" | "closed";
export type Occurrence = OccurrenceWindow & { status: OccurrenceStatus; conflictWith?: string };

function overlaps(left: { start: string; end: string }, right: { start: string; end: string }): boolean {
  return Temporal.Instant.compare(left.start, right.end) < 0 && Temporal.Instant.compare(right.start, left.end) < 0;
}

function conflictLabel(booking: Booking): string {
  if (booking.privacy === "busy") return "Busy";
  if (booking.kind === "MAINTENANCE") return "Maintenance";
  return booking.bookedBy ?? "Booking";
}

/** Opening hours are checked in the item's timezone, per occurrence, exactly as the scheduling policy does. */
function withinOpeningHours(window: OccurrenceWindow): boolean {
  return coversInterval(item, CONFIGURATION_TIMEZONE, window);
}

/**
 * Stand-in for the server dry run (`POST /bookings` with `dryRun: true`). Only the fixtures above are
 * checked; it exists so the preview has something to show and is not how production will work.
 */
export function dryRun(rule: RecurrenceRule, draft: BookingWindowDraft): Occurrence[] {
  const accepted: OccurrenceWindow[] = [];
  return expandRule(rule, draft).map((window): Occurrence => {
    const stored = storedBookings.find((booking) => booking.state === "CONFIRMED" && overlaps(window, booking));
    const sibling = accepted.find((other) => overlaps(window, other));
    accepted.push(window);
    if (stored) return { ...window, status: "overlap", conflictWith: conflictLabel(stored) };
    if (sibling) return { ...window, status: "overlap", conflictWith: `Occurrence ${sibling.position}` };
    if (!withinOpeningHours(window)) return { ...window, status: "closed" };
    return { ...window, status: "ok" };
  });
}

export function describeRule(rule: RecurrenceRule, weekday?: Weekday): string {
  if (rule.frequency === "NONE") return "Does not repeat";
  const every = rule.interval === 1 ? "" : ` every ${rule.interval} ${rule.frequency === "DAILY" ? "days" : "weeks"}`;
  const days =
    rule.frequency === "WEEKLY"
      ? ` on ${WEEKDAYS.filter((day) => rule.weekdays.includes(day.value) || day.value === weekday)
          .map((day) => day.short)
          .join(", ")}`
      : "";
  return `${rule.frequency === "DAILY" ? "Daily" : "Weekly"}${every}${days}`;
}

// ---------------------------------------------------------------------------
// Series fixtures shared by the details, scope and list stories.
// ---------------------------------------------------------------------------

export const STORED_RULE: RecurrenceRule = {
  frequency: "WEEKLY",
  interval: 1,
  weekdays: [1, 3],
  ends: { kind: "count", count: 12 },
};
const SERIES_ID_BASE = 9100;
export const SERIES_POSITION = 3;

export const seriesBookings: readonly Booking[] = expandRule(STORED_RULE, INITIAL_WINDOW).map((window) =>
  fixtureBooking({
    id: SERIES_ID_BASE + window.position,
    start: window.start,
    end: window.end,
    bookedBy: "Ada Lovelace (ada)",
    purpose: "Weekly imaging session",
    own: true,
  }),
);
const SERIES_COUNT = seriesBookings.length;

export type SeriesMembership = { position: number; count: number };

/** The optional `series` field of the booking document; busy events never carry it. */
export function seriesOf(booking: Booking): SeriesMembership | undefined {
  if (booking.privacy === "busy") return undefined;
  const position = booking.id - SERIES_ID_BASE;
  return position >= 1 && position <= SERIES_COUNT ? { position, count: SERIES_COUNT } : undefined;
}

export function editableBooking(booking: Booking): EditableBooking {
  if (booking.privacy !== "full") throw new Error("Prototype fixture must be a full booking");
  return { ...booking, canEdit: true, state: "CONFIRMED" };
}

export const currentBooking = seriesBookings[SERIES_POSITION - 1];
export const lastBooking = seriesBookings[SERIES_COUNT - 1];

export type Scope = "THIS" | "FOLLOWING" | "SERIES";

export function affectedCount(scope: Scope, series: SeriesMembership): number {
  return scope === "THIS" ? 1 : series.count - series.position + 1;
}
