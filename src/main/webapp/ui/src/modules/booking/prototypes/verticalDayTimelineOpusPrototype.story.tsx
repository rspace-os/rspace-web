// PROTOTYPE ONLY (Opus 5.5). Throwaway shared code for the vertical day timeline stories; do not import from production.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import { Temporal } from "@js-temporal/polyfill";
import {
  CheckIcon,
  ChevronDownIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  ChevronUpIcon,
  CrosshairIcon,
  InfoIcon,
  TriangleAlertIcon,
} from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { type DayTimelineEvent, DayTimelineEventCard } from "@/modules/booking/components/DayTimeline";
import { formatMinuteWithDayOffset } from "@/modules/booking/components/DayTimelineEvent";
import type { EditableBooking } from "@/modules/booking/creation/BookingForm";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { resolveBookingWindow } from "@/modules/booking/creation/ZonedBookingWindowFields";
import type { BookingConflict } from "@/modules/booking/domain/availability";
import {
  addCalendarDays,
  type BookingWindowDraft,
  dayMinuteToZonedTime,
  displayInterval,
  isPlainDate,
  wallClockDraftFromInstants,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import { Alert, AlertAction, AlertDescription } from "@/modules/common/ui/alert";
import { Button } from "@/modules/common/ui/button";
import { ButtonGroup, ButtonGroupText } from "@/modules/common/ui/button-group";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { cn } from "@/modules/common/utils/cn";

// ---------------------------------------------------------------------------
// Fixtures (in memory; no API calls)
// ---------------------------------------------------------------------------

export type FixtureEvent = { id: number; kind: "BOOKING" | "MAINTENANCE"; start: string; end: string } & (
  | { privacy: "busy" }
  | { privacy: "full"; bookedBy: string; purpose?: string }
);

export type Scenario = {
  label: string;
  date: string;
  displayTimezone: string;
  item: BookableItemOption;
  events: readonly FixtureEvent[];
  draftStart: string;
  draftEnd: string;
  initialWindow: BookingWindowDraft;
};

function instrument(overrides: Partial<BookableItemOption>): BookableItemOption {
  return {
    configurationId: 9001,
    targetId: 123,
    globalId: "IN123",
    name: "Confocal microscope",
    timezone: "Europe/Berlin",
    slotGranularityMinutes: 15,
    openingStart: "07:00",
    openingEnd: "19:00",
    openDays: [1, 2, 3, 4, 5, 6, 7],
    openingExceptions: [],
    bufferBeforeMinutes: 0,
    bufferAfterMinutes: 0,
    maxBookingDurationMinutes: 240,
    allowDoubleBooking: false,
    ...overrides,
  };
}

function scenario(value: Omit<Scenario, "initialWindow">): Scenario {
  return {
    ...value,
    initialWindow: wallClockDraftFromInstants(value.draftStart, value.draftEnd, value.displayTimezone),
  };
}

const full = (id: number, start: string, end: string, bookedBy: string, purpose?: string): FixtureEvent => ({
  id,
  kind: "BOOKING",
  privacy: "full",
  start,
  end,
  bookedBy,
  purpose,
});
const busy = (id: number, start: string, end: string): FixtureEvent => ({
  id,
  kind: "BOOKING",
  privacy: "busy",
  start,
  end,
});
const maintenance = (id: number, start: string, end: string, purpose: string): FixtureEvent => ({
  id,
  kind: "MAINTENANCE",
  privacy: "full",
  start,
  end,
  bookedBy: "Facilities",
  purpose,
});

export const SCENARIOS = {
  standard: scenario({
    label: "Ordinary day (Berlin, 15 min slots, 15 min buffer after)",
    date: "2026-11-16",
    displayTimezone: "Europe/Berlin",
    item: instrument({ bufferAfterMinutes: 15 }),
    events: [
      full(101, "2026-11-16T07:00:00Z", "2026-11-16T08:15:00Z", "Ada Lovelace", "Cell imaging"),
      maintenance(102, "2026-11-16T10:00:00Z", "2026-11-16T11:00:00Z", "Lens alignment"),
      full(103, "2026-11-16T12:00:00Z", "2026-11-16T13:30:00Z", "Grace Hopper", "Cryo-grid screening"),
      busy(104, "2026-11-16T12:30:00Z", "2026-11-16T13:00:00Z"),
      busy(105, "2026-11-16T15:00:00Z", "2026-11-16T16:30:00Z"),
    ],
    draftStart: "2026-11-16T08:30:00Z",
    draftEnd: "2026-11-16T09:30:00Z",
  }),
  dense: scenario({
    label: "Dense and 5-minute bookings (double booking allowed)",
    date: "2026-11-17",
    displayTimezone: "Europe/Berlin",
    item: instrument({ slotGranularityMinutes: 5, allowDoubleBooking: true }),
    events: [
      full(201, "2026-11-17T08:00:00Z", "2026-11-17T08:05:00Z", "Ada Lovelace", "Calibration"),
      busy(202, "2026-11-17T08:05:00Z", "2026-11-17T08:10:00Z"),
      full(203, "2026-11-17T09:00:00Z", "2026-11-17T10:00:00Z", "Grace Hopper", "Screening"),
      busy(204, "2026-11-17T09:10:00Z", "2026-11-17T09:40:00Z"),
      full(205, "2026-11-17T09:20:00Z", "2026-11-17T10:30:00Z", "Katherine Johnson", "Tomography"),
      busy(206, "2026-11-17T09:30:00Z", "2026-11-17T09:50:00Z"),
      maintenance(207, "2026-11-17T11:00:00Z", "2026-11-17T11:05:00Z", "Laser check"),
      full(208, "2026-11-17T13:00:00Z", "2026-11-17T14:00:00Z", "Ada Lovelace", "Live imaging"),
    ],
    draftStart: "2026-11-17T09:15:00Z",
    draftEnd: "2026-11-17T09:45:00Z",
  }),
  springForward: scenario({
    label: "Spring forward: 23-hour day (Berlin, 28 Mar 2027)",
    date: "2027-03-28",
    displayTimezone: "Europe/Berlin",
    item: instrument({
      slotGranularityMinutes: 30,
      openingStart: "00:00",
      openingEnd: "24:00",
      openDays: [1, 2, 3, 4, 5, 6, 7],
      openingExceptions: [],
      maxBookingDurationMinutes: 0,
    }),
    events: [
      full(301, "2027-03-27T23:30:00Z", "2027-03-28T00:30:00Z", "Ada Lovelace", "Night acquisition"),
      busy(302, "2027-03-28T00:30:00Z", "2027-03-28T01:30:00Z"),
      maintenance(303, "2027-03-28T04:00:00Z", "2027-03-28T05:00:00Z", "Chiller service"),
    ],
    draftStart: "2027-03-28T01:30:00Z",
    draftEnd: "2027-03-28T02:30:00Z",
  }),
  fallBack: scenario({
    label: "Fall back: 25-hour day, repeated 02:00 (Berlin, 25 Oct 2026)",
    date: "2026-10-25",
    displayTimezone: "Europe/Berlin",
    item: instrument({ openingStart: "00:00", openingEnd: "24:00", maxBookingDurationMinutes: 0 }),
    events: [
      full(401, "2026-10-25T00:15:00Z", "2026-10-25T00:45:00Z", "Grace Hopper", "First pass"),
      busy(402, "2026-10-25T01:15:00Z", "2026-10-25T01:45:00Z"),
      full(403, "2026-10-25T04:00:00Z", "2026-10-25T05:00:00Z", "Ada Lovelace", "Second pass"),
    ],
    draftStart: "2026-10-25T01:45:00Z",
    draftEnd: "2026-10-25T02:30:00Z",
  }),
  overnight: scenario({
    label: "Overnight and cross-day (Berlin)",
    date: "2026-11-19",
    displayTimezone: "Europe/Berlin",
    item: instrument({
      slotGranularityMinutes: 30,
      openingStart: "00:00",
      openingEnd: "24:00",
      openDays: [1, 2, 3, 4, 5, 6, 7],
      openingExceptions: [],
      maxBookingDurationMinutes: 0,
    }),
    events: [
      full(501, "2026-11-18T20:00:00Z", "2026-11-18T23:00:00Z", "Grace Hopper", "Ends at next-day midnight"),
      maintenance(502, "2026-11-18T22:00:00Z", "2026-11-19T02:00:00Z", "Overnight chiller service"),
      full(503, "2026-11-19T07:00:00Z", "2026-11-19T11:00:00Z", "Ada Lovelace", "Long acquisition"),
      busy(504, "2026-11-20T03:00:00Z", "2026-11-20T05:00:00Z"),
    ],
    draftStart: "2026-11-19T19:00:00Z",
    draftEnd: "2026-11-20T01:00:00Z",
  }),
  displayDiffers: scenario({
    label: "Display Asia/Kolkata (+05:30), instrument Berlin, hourly slots",
    date: "2026-11-16",
    displayTimezone: "Asia/Kolkata",
    item: instrument({ slotGranularityMinutes: 60, openingStart: "08:00", openingEnd: "18:00" }),
    events: [
      full(601, "2026-11-16T11:00:00Z", "2026-11-16T12:00:00Z", "Grace Hopper", "Live imaging"),
      busy(602, "2026-11-16T13:00:00Z", "2026-11-16T15:00:00Z"),
    ],
    draftStart: "2026-11-16T08:00:00Z",
    draftEnd: "2026-11-16T10:00:00Z",
  }),
} satisfies Record<string, Scenario>;

export type ScenarioId = keyof typeof SCENARIOS;
export const SCENARIO_IDS = Object.keys(SCENARIOS) as ScenarioId[];

export const ORIGINAL_BOOKING_ID = 41;
const ORIGINAL_PURPOSE = "My imaging session";

/** The edited booking as the day query would return it, so exclusion is exercised rather than assumed. */
export function originalFixtureFor(value: Scenario): FixtureEvent {
  return full(ORIGINAL_BOOKING_ID, value.draftStart, value.draftEnd, "You", ORIGINAL_PURPOSE);
}

export function editableBookingFor(value: Scenario): EditableBooking {
  return {
    id: ORIGINAL_BOOKING_ID,
    version: 0,
    target: {
      relationTo: "booking-instruments",
      globalId: value.item.globalId,
      value: { id: value.item.targetId, name: value.item.name, deleted: false },
    },
    timezone: value.item.timezone,
    start: value.draftStart,
    end: value.draftEnd,
    state: "CONFIRMED",
    kind: "BOOKING",
    privacy: "full",
    cancellationReason: null,
    purpose: ORIGINAL_PURPOSE,
    bookedBy: "You",
    createdBy: "You",
    canEdit: true,
    canCancel: true,
    createdAt: "2026-08-01T09:00:00Z",
    updatedAt: "2026-08-01T09:00:00Z",
  };
}

// ---------------------------------------------------------------------------
// Pure range logic (prototype stand-in for creation/verticalTimelineRange.ts)
// ---------------------------------------------------------------------------

const MINUTE_MS = 60_000;
/** Epoch minutes. Absolute, so repeated wall-clock hours never collide. */
export type EpochRange = { start: number; end: number };
export type AdjustMode = "move" | "start" | "end";
type SlotGrid = { dayStart: number; dayEnd: number; slots: number[]; valid: Set<number> };

export const instantMinute = (instant: string) => Temporal.Instant.from(instant).epochMilliseconds / MINUTE_MS;
const minuteInstant = (minute: number) =>
  Temporal.Instant.fromEpochMilliseconds(Math.round(minute * MINUTE_MS)).toString();
const sameRange = (left: EpochRange | undefined, right: EpochRange | undefined) =>
  left?.start === right?.start && left?.end === right?.end;

/**
 * Every instant in the displayed zoned day (end inclusive) that sits on a slot in the
 * scheduling timezone's wall clock, the same rule validateBookingWindow applies.
 * ponytail: assumes whole-minute UTC offsets; historic LMT offsets would need second precision.
 */
function slotGrid(date: string, displayTimezone: string, schedulingTimezone: string, granularity: number): SlotGrid {
  const bounds = zonedDayBounds(date, displayTimezone);
  const dayStart = instantMinute(bounds.start);
  const dayEnd = instantMinute(bounds.end);
  const slots: number[] = [];
  let cursor = dayStart;
  let zoned = Temporal.Instant.from(bounds.start).toZonedDateTimeISO(schedulingTimezone);
  while (cursor <= dayEnd) {
    const offset = zoned.offsetNanoseconds / (MINUTE_MS * 1_000_000);
    const transition = zoned.getTimeZoneTransition("next");
    const segmentEnd = transition ? Math.min(dayEnd + 1, transition.epochMilliseconds / MINUTE_MS) : dayEnd + 1;
    for (let minute = Math.ceil(cursor); minute < segmentEnd; minute++) {
      const wallMinute = (((minute + offset) % 1440) + 1440) % 1440;
      if (wallMinute % granularity === 0) slots.push(minute);
    }
    if (!transition || segmentEnd > dayEnd) break;
    cursor = segmentEnd;
    zoned = transition;
  }
  return { dayStart, dayEnd, slots, valid: new Set(slots) };
}

/**
 * One calculation for keyboard and pointer. Move keeps the duration and only accepts starts
 * whose end is also a valid slot; resize never crosses the other endpoint. No valid
 * candidate leaves the range unchanged. Pointer ties go to the earlier instant.
 */
function adjustRange(
  range: EpochRange,
  mode: AdjustMode,
  grid: SlotGrid,
  request: { kind: "step"; direction: -1 | 1 } | { kind: "pointer"; target: number },
): EpochRange {
  const duration = range.end - range.start;
  const accepts = (slot: number) =>
    mode === "start" ? slot < range.end : mode === "end" ? slot > range.start : grid.valid.has(slot + duration);
  const current = mode === "end" ? range.end : range.start;
  let chosen: number | undefined;
  if (request.kind === "step" && request.direction < 0) {
    for (const slot of grid.slots) {
      if (slot >= current) break;
      if (accepts(slot)) chosen = slot;
    }
  } else if (request.kind === "step") {
    chosen = grid.slots.find((slot) => slot > current && accepts(slot));
  } else {
    let best = Number.POSITIVE_INFINITY;
    for (const slot of grid.slots) {
      const distance = Math.abs(slot - request.target);
      if (accepts(slot) && distance < best) {
        best = distance;
        chosen = slot;
      }
    }
  }
  if (chosen === undefined) return range;
  if (mode === "start") return { start: chosen, end: range.end };
  if (mode === "end") return { start: range.start, end: chosen };
  return { start: chosen, end: chosen + duration };
}

type Placed<T> = { item: T; start: number; end: number; lane: number; lanes: number; cluster: number };

/** Deterministic half-open lanes: sort by start, end, id; first free lane inside each overlap group. */
function placeInLanes<T extends { id: string }>(entries: readonly { item: T; start: number; end: number }[]) {
  const sorted = [...entries].sort((a, b) => a.start - b.start || a.end - b.end || a.item.id.localeCompare(b.item.id));
  const placed: Placed<T>[] = [];
  let group: Placed<T>[] = [];
  let laneEnds: number[] = [];
  let groupEnd = Number.NEGATIVE_INFINITY;
  let cluster = 0;
  const flush = () => {
    for (const entry of group) entry.lanes = laneEnds.length;
    group = [];
    laneEnds = [];
    cluster++;
  };
  for (const entry of sorted) {
    if (group.length > 0 && entry.start >= groupEnd) flush();
    let lane = laneEnds.findIndex((end) => end <= entry.start);
    if (lane < 0) {
      lane = laneEnds.length;
      laneEnds.push(entry.end);
    } else laneEnds[lane] = entry.end;
    const next = { ...entry, lane, lanes: 1, cluster };
    group.push(next);
    placed.push(next);
    groupEnd = Math.max(groupEnd, entry.end);
  }
  flush();
  return placed;
}

function openingRanges(grid: SlotGrid, item: BookableItemOption): EpochRange[] {
  if (item.openingStart === "00:00" && item.openingEnd === "24:00") return [{ start: grid.dayStart, end: grid.dayEnd }];
  const schedulingDate = (minute: number) =>
    Temporal.Instant.fromEpochMilliseconds(minute * MINUTE_MS)
      .toZonedDateTimeISO(item.timezone)
      .toPlainDate();
  const ranges: EpochRange[] = [];
  const last = schedulingDate(grid.dayEnd);
  for (
    let date = schedulingDate(grid.dayStart);
    Temporal.PlainDate.compare(date, last) <= 0;
    date = date.add({ days: 1 })
  ) {
    try {
      const interval = displayInterval(date.toString(), item.timezone, item.openingStart, item.openingEnd);
      ranges.push({ start: instantMinute(interval.start), end: instantMinute(interval.end) });
    } catch {
      // ponytail: an invalid opening configuration leaves the whole day shaded as closed.
    }
  }
  return ranges;
}

export function sameDraft(left: BookingWindowDraft, right: BookingWindowDraft) {
  return (
    left.startDate === right.startDate &&
    left.startTime === right.startTime &&
    left.startOccurrence === right.startOccurrence &&
    left.endDate === right.endDate &&
    left.endTime === right.endTime &&
    left.endOccurrence === right.endOccurrence
  );
}

export function formatInterval(start: string, end: string, timezone: string, locale: string) {
  return new Intl.DateTimeFormat(locale, {
    timeZone: timezone,
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    timeZoneName: "shortOffset",
  }).formatRange(new Date(start), new Date(end));
}

// ---------------------------------------------------------------------------
// Simulated availability policy (prototype stand-in for the shared draft-availability hook)
// ---------------------------------------------------------------------------

export type AvailabilityFixture = "ok" | "pending" | "failed";
export type DraftStatus = { tone: "neutral" | "success" | "warning" | "error"; text: string };

/** Buffers pad existing bookings; maintenance always blocks; allowed double booking is a warning. */
export function draftConflicts(
  window: { start: string; end: string } | undefined,
  events: readonly FixtureEvent[],
  item: BookableItemOption,
  displayTimezone: string,
  excludedBookingId?: number,
) {
  if (!window) return { conflicts: [] as BookingConflict[], blocking: false, severity: "warning" as const };
  const start = instantMinute(window.start);
  const end = instantMinute(window.end);
  const conflicts: BookingConflict[] = [];
  let blocking = false;
  for (const event of events) {
    if (event.id === excludedBookingId) continue;
    const before = event.kind === "BOOKING" ? item.bufferBeforeMinutes : 0;
    const after = event.kind === "BOOKING" ? item.bufferAfterMinutes : 0;
    if (!(start < instantMinute(event.end) + after && end > instantMinute(event.start) - before)) continue;
    if (event.kind === "MAINTENANCE" || !item.allowDoubleBooking) blocking = true;
    conflicts.push({
      id: event.id,
      kind: event.kind,
      privacy: event.privacy,
      purpose: event.privacy === "full" ? (event.purpose ?? null) : null,
      bookedBy: event.privacy === "full" ? event.bookedBy : null,
      start: event.start,
      end: event.end,
      timezone: displayTimezone,
      instrumentTimeZone: item.timezone,
    });
  }
  return { conflicts, blocking, severity: blocking ? ("error" as const) : ("warning" as const) };
}

export function draftStatus({
  resolved,
  availability,
  conflictCount,
  blocking,
}: {
  resolved: boolean;
  availability: AvailabilityFixture;
  conflictCount: number;
  blocking: boolean;
}): DraftStatus {
  if (!resolved) return { tone: "neutral", text: "Not checked · see the form fields" };
  if (availability === "pending") return { tone: "neutral", text: "Checking availability…" };
  if (availability === "failed") return { tone: "warning", text: "Availability not verified" };
  if (conflictCount === 0) return { tone: "success", text: "No conflicts found" };
  return blocking
    ? { tone: "error", text: `Conflicts with ${conflictCount} ${conflictCount === 1 ? "event" : "events"}` }
    : { tone: "warning", text: `Overlaps ${conflictCount} · warning only` };
}

export type DayQueryFixture = "ready" | "loading" | "empty" | "error" | "truncated";

/** Simulates the browsed-day query. Retry resolves to "ready" after a short delay. */
export function useSimulatedDayQuery(initial: DayQueryFixture) {
  const [state, setState] = React.useState(initial);
  const timer = React.useRef<number | undefined>(undefined);
  React.useEffect(() => () => window.clearTimeout(timer.current), []);
  const retry = React.useCallback(() => {
    setState("loading");
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setState("ready"), 800);
  }, []);
  return [state, setState, retry] as const;
}

// ---------------------------------------------------------------------------
// Prototype-only fixture controls (visually distinct from proposed production UI)
// ---------------------------------------------------------------------------

export function PrototypeControls({ children }: { children: React.ReactNode }) {
  return (
    <fieldset className="min-w-0 rounded-sm border-2 border-dashed border-border bg-muted/25 p-3 text-xs text-foreground">
      <legend className="px-1 font-semibold">Prototype fixtures · simulated, not proposed UI</legend>
      <div className="flex flex-wrap items-end gap-3">{children}</div>
    </fieldset>
  );
}

export function FixtureSelect<T extends string | number>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: readonly { value: T; label: string }[];
  onChange: (value: T) => void;
}) {
  const id = React.useId();
  return (
    <div className="flex min-w-0 max-w-full flex-col gap-1">
      <label htmlFor={id} className="font-medium">
        {label}
      </label>
      <select
        id={id}
        className="h-8 max-w-full rounded-sm border bg-background px-2 text-sm"
        value={String(value)}
        onChange={(event) => {
          const next = options.find((option) => String(option.value) === event.currentTarget.value);
          if (next) onChange(next.value);
        }}
      >
        {options.map((option) => (
          <option key={String(option.value)} value={String(option.value)}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
}

export const SCENARIO_OPTIONS = SCENARIO_IDS.map((id) => ({ value: id, label: SCENARIOS[id].label }));
export const QUERY_OPTIONS: readonly { value: DayQueryFixture; label: string }[] = [
  { value: "ready", label: "Loaded" },
  { value: "loading", label: "Loading" },
  { value: "empty", label: "Empty" },
  { value: "error", label: "Failed" },
  { value: "truncated", label: "Over 1,000 results" },
];
export const HOUR_HEIGHT_OPTIONS: readonly { value: number; label: string }[] = [
  { value: 48, label: "48 px/hour" },
  { value: 72, label: "72 px/hour" },
  { value: 120, label: "120 px/hour" },
];

export function DraftReadout({ draft }: { draft: BookingWindowDraft }) {
  const endpoint = (date: string, time: string, occurrence?: string) =>
    `${date || "—"} ${time || "—"}${occurrence ? ` (${occurrence})` : ""}`;
  return (
    <span className="font-mono tabular-nums">
      {endpoint(draft.startDate, draft.startTime, draft.startOccurrence)} →{" "}
      {endpoint(draft.endDate, draft.endTime, draft.endOccurrence)}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Vertical day timeline (proposed production UI)
// ---------------------------------------------------------------------------

const GUTTER_PX = 56;
const MIN_LANE_PX = 96; // 6rem
const MIN_TARGET_PX = 24;
const MODE_LABEL: Record<AdjustMode, string> = { move: "Move", start: "Start", end: "End" };

function toDayEvent(event: FixtureEvent, dayStart: number, item: BookableItemOption): DayTimelineEvent {
  const base = {
    id: String(event.id),
    startMinute: instantMinute(event.start) - dayStart,
    endMinute: instantMinute(event.end) - dayStart,
    startInstant: event.start,
    endInstant: event.end,
    instrumentTimeZone: item.timezone,
  };
  const reference = { name: item.name, globalId: item.globalId };
  // Busy-only rows never receive private fields; the fixture type cannot carry them.
  if (event.privacy === "busy") return { ...base, kind: "booking", privacy: "busy" };
  if (event.kind === "MAINTENANCE") {
    return {
      ...base,
      kind: "blockout",
      title: "Maintenance",
      item: reference,
      createdBy: event.bookedBy,
      notes: event.purpose,
    };
  }
  return {
    ...base,
    kind: "booking",
    privacy: "full",
    title: item.name,
    bookedBy: event.bookedBy,
    item: reference,
    canEdit: false,
    notes: event.purpose,
  };
}

function markClass(event: FixtureEvent) {
  if (event.kind === "MAINTENANCE") return "bg-amber-600";
  return event.privacy === "busy" ? "bg-slate-500" : "bg-blue-600";
}

function StatusLine({ status }: { status: DraftStatus }) {
  const Icon = status.tone === "success" ? CheckIcon : status.tone === "neutral" ? InfoIcon : TriangleAlertIcon;
  return (
    <p
      className={cn(
        "flex items-center gap-1.5 text-xs font-medium",
        status.tone === "neutral" && "text-muted-foreground",
        status.tone === "success" && "text-emerald-700 dark:text-emerald-300",
        status.tone === "warning" && "text-amber-800 dark:text-amber-300",
        status.tone === "error" && "text-destructive",
      )}
    >
      <Icon aria-hidden="true" className="size-3.5 shrink-0" />
      {status.text}
    </p>
  );
}

type Gesture = { mode: AdjustMode; pointerId: number; grabOffset: number; base: EpochRange; element: HTMLElement };

export function VerticalDayTimeline({
  item,
  displayTimezone,
  events,
  fallbackDate,
  originalBookingId,
  draft,
  awaitingAcknowledgement = false,
  disabled = false,
  query,
  onRetry,
  status,
  hourHeight,
  layout,
  onCommit,
}: {
  item: BookableItemOption;
  displayTimezone: string;
  events: readonly FixtureEvent[];
  fallbackDate: string;
  originalBookingId?: number;
  /** The committed draft: a pending adjustment if one awaits acknowledgement, else the form's draft. */
  draft: BookingWindowDraft;
  awaitingAcknowledgement?: boolean;
  disabled?: boolean;
  query: DayQueryFixture;
  onRetry: () => void;
  status: DraftStatus;
  hourHeight: number;
  layout: "sticky" | "inline";
  onCommit: (draft: BookingWindowDraft) => void;
}) {
  const { i18n } = useTranslation("booking");
  const locale = i18n.language;
  const headingId = React.useId();
  const hintId = React.useId();
  const timezone = displayTimezone;
  const draftDay = isPlainDate(draft.startDate) ? draft.startDate : undefined;
  const [view, setView] = React.useState<"day" | "list">("day");
  const [browseDate, setBrowseDate] = React.useState(draftDay ?? fallbackDate);
  // An explicit form date change follows the new draft start day; browsing never alters the draft.
  const [followedDay, setFollowedDay] = React.useState(draftDay);
  if (draftDay !== followedDay) {
    setFollowedDay(draftDay);
    if (draftDay) setBrowseDate(draftDay);
  }
  const [announcement, setAnnouncement] = React.useState("");
  const [canvas, setCanvas] = React.useState<HTMLDivElement | null>(null);
  const [canvasWidth, setCanvasWidth] = React.useState(0);
  const scrollRef = React.useRef<HTMLElement>(null);
  const [preview, setPreview] = React.useState<EpochRange>();
  const previewRef = React.useRef<EpochRange | undefined>(undefined);
  const gesture = React.useRef<Gesture | null>(null);

  const grid = React.useMemo(
    () => slotGrid(browseDate, timezone, item.timezone, item.slotGranularityMinutes),
    [browseDate, timezone, item.timezone, item.slotGranularityMinutes],
  );
  const elapsed = grid.dayEnd - grid.dayStart;
  const pxPerMinute = hourHeight / 60;
  // Resolve instants separately from policy so an out-of-hours draft stays visible.
  const resolved = React.useMemo(() => {
    const window = resolveBookingWindow(draft, timezone).window;
    return window ? { start: instantMinute(window.start), end: instantMinute(window.end) } : undefined;
  }, [draft, timezone]);

  const blocker = disabled
    ? "Saving…"
    : !resolved
      ? "Complete the start and end in the form to place the draft."
      : browseDate !== draftDay
        ? "Viewing another day."
        : resolved.end > grid.dayEnd
          ? "Spans midnight. Adjust it in the date and time fields."
          : !grid.valid.has(resolved.start) || !grid.valid.has(resolved.end)
            ? `Off the ${item.slotGranularityMinutes}-minute grid. Adjust it in the time fields.`
            : undefined;
  const adjustable = blocker === undefined && resolved !== undefined;

  const cancelGesture = React.useCallback(() => {
    const active = gesture.current;
    if (!active) return;
    gesture.current = null;
    if (active.element.hasPointerCapture(active.pointerId)) active.element.releasePointerCapture(active.pointerId);
    previewRef.current = undefined;
    setPreview(undefined);
  }, []);

  // Day, timezone, view, submission or an external draft change invalidates any preview.
  const contextKey = `${browseDate}|${timezone}|${view}|${disabled}|${JSON.stringify(draft)}`;
  const lastContextKey = React.useRef(contextKey);
  React.useEffect(() => {
    if (lastContextKey.current === contextKey) return;
    lastContextKey.current = contextKey;
    cancelGesture();
  }, [contextKey, cancelGesture]);

  React.useEffect(() => {
    if (!preview) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      cancelGesture();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [preview, cancelGesture]);

  // Measure lanes, and cancel rather than commit against an obsolete geometry.
  React.useEffect(() => {
    if (!canvas) return;
    let last = { width: canvas.clientWidth, height: canvas.clientHeight };
    setCanvasWidth(last.width);
    const observer = new ResizeObserver(() => {
      const next = { width: canvas.clientWidth, height: canvas.clientHeight };
      if (next.width === last.width && next.height === last.height) return;
      last = next;
      setCanvasWidth(next.width);
      cancelGesture();
    });
    observer.observe(canvas);
    return () => observer.disconnect();
  }, [canvas, cancelGesture]);

  const opening = React.useMemo(() => openingRanges(grid, item), [grid, item]);
  const scrollAnchor =
    resolved && resolved.end > grid.dayStart && resolved.start < grid.dayEnd
      ? Math.max(resolved.start, grid.dayStart)
      : (opening.find((range) => range.end > grid.dayStart)?.start ?? grid.dayStart);
  // Scroll once per browsed day and view, never on refresh or draft changes.
  const scrolledFor = React.useRef("");
  React.useLayoutEffect(() => {
    const key = `${browseDate}|${timezone}|${view}`;
    if (!scrollRef.current || scrolledFor.current === key) return;
    scrolledFor.current = key;
    scrollRef.current.scrollTop = Math.max(0, (scrollAnchor - grid.dayStart) * pxPerMinute - 32);
  });

  const commit = (range: EpochRange) => {
    if (!resolved || sameRange(range, resolved)) return;
    const startInstant = minuteInstant(range.start);
    const endInstant = minuteInstant(range.end);
    onCommit(wallClockDraftFromInstants(startInstant, endInstant, timezone));
    setAnnouncement(`Draft ${formatInterval(startInstant, endInstant, timezone, locale)}`);
  };
  const step = (mode: AdjustMode, direction: -1 | 1) => {
    if (!adjustable || !resolved) return;
    commit(adjustRange(resolved, mode, grid, { kind: "step", direction }));
  };
  const canStep = (mode: AdjustMode, direction: -1 | 1) =>
    adjustable &&
    resolved !== undefined &&
    !sameRange(adjustRange(resolved, mode, grid, { kind: "step", direction }), resolved);

  const pointerMinute = (clientY: number) =>
    grid.dayStart + (clientY - (canvas?.getBoundingClientRect().top ?? 0)) / pxPerMinute;
  const beginGesture = (mode: AdjustMode) => (event: React.PointerEvent<HTMLButtonElement>) => {
    // Only the active primary pointer and the left mouse button start a gesture.
    if (!adjustable || !resolved || gesture.current || !event.isPrimary || event.button !== 0 || !canvas) return;
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    const anchor = mode === "end" ? resolved.end : resolved.start;
    gesture.current = {
      mode,
      pointerId: event.pointerId,
      grabOffset: pointerMinute(event.clientY) - anchor,
      base: resolved,
      element: event.currentTarget,
    };
    previewRef.current = resolved;
    setPreview(resolved);
  };
  const trackGesture = (event: React.PointerEvent<HTMLButtonElement>) => {
    const active = gesture.current;
    if (!active || active.pointerId !== event.pointerId) return;
    const target = pointerMinute(event.clientY) - active.grabOffset;
    const next = adjustRange(active.base, active.mode, grid, { kind: "pointer", target });
    if (sameRange(next, previewRef.current)) return;
    previewRef.current = next;
    setPreview(next);
  };
  const finishGesture = (event: React.PointerEvent<HTMLButtonElement>) => {
    const active = gesture.current;
    if (!active || active.pointerId !== event.pointerId) return;
    const final = previewRef.current;
    gesture.current = null;
    previewRef.current = undefined;
    setPreview(undefined);
    if (active.element.hasPointerCapture(event.pointerId)) active.element.releasePointerCapture(event.pointerId);
    if (final) commit(final); // Commit once per gesture; no-ops are ignored.
  };
  const abortGesture = (event: React.PointerEvent<HTMLButtonElement>) => {
    if (gesture.current?.pointerId === event.pointerId) cancelGesture();
  };
  const pointerHandlers = (mode: AdjustMode) => ({
    onPointerDown: beginGesture(mode),
    onPointerMove: trackGesture,
    onPointerUp: finishGesture,
    onPointerCancel: abortGesture,
    onLostPointerCapture: abortGesture,
  });

  const segment = (range: EpochRange) => {
    const top = Math.max(range.start, grid.dayStart) - grid.dayStart;
    const bottom = Math.min(range.end, grid.dayEnd) - grid.dayStart;
    return bottom > top ? { top: top * pxPerMinute, height: (bottom - top) * pxPerMinute } : undefined;
  };

  const loaded = query === "ready";
  const original = events.find((event) => event.id === originalBookingId);
  const dayEvents = loaded
    ? events.filter(
        (event) =>
          event.id !== originalBookingId &&
          instantMinute(event.start) < grid.dayEnd &&
          instantMinute(event.end) > grid.dayStart,
      )
    : [];
  const placed = placeInLanes(
    dayEvents.map((event) => ({
      item: { id: String(event.id), fixture: event },
      start: Math.max(instantMinute(event.start), grid.dayStart),
      end: Math.min(instantMinute(event.end), grid.dayEnd),
    })),
  );
  const areaWidth = canvasWidth - GUTTER_PX;
  const denseClusters = new Set(
    placed.filter((entry) => canvasWidth > 0 && areaWidth / entry.lanes < MIN_LANE_PX).map((entry) => entry.cluster),
  );
  const isMark = (entry: (typeof placed)[number]) =>
    denseClusters.has(entry.cluster) || (entry.end - entry.start) * pxPerMinute < MIN_TARGET_PX;
  const markedCount = placed.filter(isMark).length;
  const laneStyle = (lane: number, lanes: number): React.CSSProperties => ({
    left: `calc(${GUTTER_PX}px + (100% - ${GUTTER_PX}px) * ${lane / lanes})`,
    width: `calc((100% - ${GUTTER_PX}px) / ${lanes} - 4px)`,
  });

  const shown = preview ?? resolved;
  const draftSegment = shown ? segment(shown) : undefined;
  const originalSegment = original
    ? segment({ start: instantMinute(original.start), end: instantMinute(original.end) })
    : undefined;
  const committedHeight = resolved ? (resolved.end - resolved.start) * pxPerMinute : 0;
  const handles = !adjustable
    ? "none"
    : committedHeight >= 3 * MIN_TARGET_PX
      ? "full"
      : committedHeight >= MIN_TARGET_PX
        ? "move"
        : "none";
  const shownStatus: DraftStatus = preview
    ? { tone: "neutral", text: "Adjusting · release to check" }
    : awaitingAcknowledgement
      ? { tone: "neutral", text: "Updating form…" }
      : status;
  const minuteLabel = (minute: number) => formatMinuteWithDayOffset(browseDate, timezone, minute - grid.dayStart);

  const bounds = zonedDayBounds(browseDate, timezone);
  const startOffset = Temporal.Instant.from(bounds.start).toZonedDateTimeISO(timezone).offset;
  const endOffset = Temporal.Instant.from(bounds.end).toZonedDateTimeISO(timezone).offset;
  const clockChange = elapsed !== 1440;
  const hours = Array.from({ length: Math.floor(elapsed / 60) + 1 }, (_, index) => {
    const zoned = dayMinuteToZonedTime(browseDate, timezone, index * 60);
    return {
      minute: index * 60,
      time: zoned.toPlainTime().toString({ smallestUnit: "minute" }),
      offset: clockChange ? zoned.offset : undefined,
      nextDay: zoned.toPlainDate().toString() !== browseDate,
    };
  });
  const dayHeading = new Intl.DateTimeFormat(locale, {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${browseDate}T12:00:00Z`));
  const listEvents = [...dayEvents].sort((a, b) => a.start.localeCompare(b.start) || a.id - b.id);

  const queryNotice =
    query === "loading" ? (
      <p role="status" className="text-xs text-muted-foreground">
        Loading bookings…
      </p>
    ) : query === "empty" ? (
      <p role="status" className="text-xs text-muted-foreground">
        No bookings on this day.
      </p>
    ) : query === "error" || query === "truncated" ? (
      <Alert variant="destructive" className="py-2 pr-20">
        <TriangleAlertIcon aria-hidden="true" />
        <AlertDescription className="text-xs">
          {query === "error"
            ? "Couldn't load this day's bookings. The form is unchanged."
            : "Over 1,000 bookings. This schedule would be incomplete, so none are shown."}
        </AlertDescription>
        <AlertAction>
          <Button type="button" size="xs" variant="outline" onClick={onRetry}>
            Retry
          </Button>
        </AlertAction>
      </Alert>
    ) : null;

  const canvasBody = (
    <div ref={setCanvas} className="relative select-none" style={{ height: elapsed * pxPerMinute }}>
      <div aria-hidden="true" className="absolute inset-y-0 right-0 bg-muted" style={{ left: GUTTER_PX }} />
      {opening.map((range) => {
        const block = segment(range);
        return block ? (
          <div
            key={range.start}
            aria-hidden="true"
            className="absolute right-0 bg-card"
            style={{ left: GUTTER_PX, top: block.top, height: block.height }}
          />
        ) : null;
      })}
      {hours.map((hour) => (
        <div
          key={hour.minute}
          aria-hidden="true"
          className="absolute inset-x-0 border-t border-border"
          style={{ top: hour.minute * pxPerMinute }}
        >
          <span className="absolute top-0.5 left-1 bg-card text-[10px] leading-tight text-muted-foreground tabular-nums">
            {hour.time}
            {hour.nextDay ? " +1" : ""}
            {hour.offset ? <span className="block">{hour.offset}</span> : null}
          </span>
        </div>
      ))}
      {query === "loading" ? (
        <div aria-hidden="true">
          {[0.2, 0.45, 0.7].map((fraction) => (
            <Skeleton
              key={fraction}
              className="absolute h-16 bg-muted-foreground/20"
              style={{ top: fraction * elapsed * pxPerMinute, ...laneStyle(0, 1) }}
            />
          ))}
        </div>
      ) : null}
      {/* Occupancy marks are decorative; the list view carries their details. */}
      <div aria-hidden="true">
        {placed.filter(isMark).map((entry) => {
          const block = segment(entry);
          if (!block) return null;
          const dense = denseClusters.has(entry.cluster);
          const style = dense
            ? {
                left: `calc(${GUTTER_PX}px + (100% - ${GUTTER_PX}px) * ${entry.lane / entry.lanes})`,
                width: 6,
              }
            : laneStyle(entry.lane, entry.lanes);
          return (
            <div
              key={entry.item.id}
              className={cn("absolute rounded-full", markClass(entry.item.fixture))}
              style={{ top: block.top, height: Math.max(block.height, 3), ...style }}
            />
          );
        })}
      </div>
      <ol aria-label="Bookings on this day" className="m-0 list-none p-0">
        {placed
          .filter((entry) => !isMark(entry))
          .map((entry) => {
            const block = segment(entry);
            if (!block) return null;
            const event = entry.item.fixture;
            return (
              <li
                key={entry.item.id}
                data-event-id={entry.item.id}
                className="absolute [&>article]:max-w-full"
                style={{ top: block.top, height: block.height, ...laneStyle(entry.lane, entry.lanes) }}
              >
                <DayTimelineEventCard
                  event={toDayEvent(event, grid.dayStart, item)}
                  date={browseDate}
                  timezone={timezone}
                  variant="timeline"
                  compactCards={false}
                />
                {instantMinute(event.start) < grid.dayStart ? (
                  <ChevronUpIcon
                    aria-hidden="true"
                    className="pointer-events-none absolute -top-1 left-1/2 z-20 size-4 -translate-x-1/2 rounded-full bg-background"
                  />
                ) : null}
                {instantMinute(event.end) > grid.dayEnd ? (
                  <ChevronDownIcon
                    aria-hidden="true"
                    className="pointer-events-none absolute -bottom-1 left-1/2 z-20 size-4 -translate-x-1/2 rounded-full bg-background"
                  />
                ) : null}
              </li>
            );
          })}
      </ol>
      {/* Drafts span the booking area; the transparent wrapper lets other cards receive clicks. */}
      <div aria-hidden="true" className="pointer-events-none absolute inset-y-0 right-1" style={{ left: GUTTER_PX }}>
        {originalSegment ? (
          <div
            className="absolute inset-x-0 rounded-sm border-2 border-dashed border-muted-foreground"
            style={{ top: originalSegment.top, height: originalSegment.height }}
          >
            {originalSegment.height >= 16 ? <span className="px-1 text-[10px] font-medium">Original</span> : null}
          </div>
        ) : null}
        {draftSegment && shown ? (
          <div
            className="pointer-events-auto absolute inset-x-0 overflow-hidden rounded-sm border-2 border-primary bg-[color-mix(in_srgb,var(--primary)_25%,var(--background))] text-[11px] leading-tight text-foreground shadow-sm ring-3 ring-ring/40"
            style={{ top: draftSegment.top, height: Math.max(draftSegment.height, 3) }}
          >
            {handles === "full" ? (
              <>
                <button
                  type="button"
                  tabIndex={-1}
                  aria-label="Drag draft start"
                  className="absolute inset-x-0 top-0 h-6 cursor-ns-resize touch-none border-b border-current/20"
                  {...pointerHandlers("start")}
                />
                <button
                  type="button"
                  tabIndex={-1}
                  aria-label="Drag draft"
                  className="absolute inset-x-0 top-6 bottom-6 cursor-grab touch-none px-1 text-left font-medium"
                  {...pointerHandlers("move")}
                >
                  {minuteLabel(shown.start)}–{minuteLabel(shown.end)}
                </button>
                <button
                  type="button"
                  tabIndex={-1}
                  aria-label="Drag draft end"
                  className="absolute inset-x-0 bottom-0 h-6 cursor-ns-resize touch-none border-t border-current/20"
                  {...pointerHandlers("end")}
                />
              </>
            ) : handles === "move" ? (
              <button
                type="button"
                tabIndex={-1}
                aria-label="Drag draft"
                className="absolute inset-0 cursor-grab touch-none px-1 text-left font-medium"
                {...pointerHandlers("move")}
              >
                {minuteLabel(shown.start)}
              </button>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  );

  const listBody =
    query === "loading" ? (
      <div aria-hidden="true" className="space-y-2 p-3">
        {[0, 1, 2].map((row) => (
          <Skeleton key={row} className="h-12 w-full" />
        ))}
      </div>
    ) : (
      <ol aria-label="Bookings on this day" className="m-0 list-none space-y-2 p-3">
        {original ? (
          <li className="rounded-sm border-2 border-dashed border-muted-foreground p-2 text-xs">
            <span className="font-medium">Original booking</span> ·{" "}
            {formatInterval(original.start, original.end, timezone, locale)}
            <span className="block text-muted-foreground">Not counted as a conflict</span>
          </li>
        ) : null}
        {listEvents.map((event) => (
          <li key={event.id} data-event-id={String(event.id)}>
            <DayTimelineEventCard
              event={toDayEvent(event, grid.dayStart, item)}
              date={browseDate}
              timezone={timezone}
              variant="flow"
              compactCards={false}
            />
          </li>
        ))}
        {loaded && listEvents.length === 0 ? <li className="text-xs text-muted-foreground">No bookings.</li> : null}
      </ol>
    );

  const draftText = resolved
    ? formatInterval(
        minuteInstant(preview?.start ?? resolved.start),
        minuteInstant(preview?.end ?? resolved.end),
        timezone,
        locale,
      )
    : "Not set";
  const moveBlocked = adjustable && !canStep("move", -1) && !canStep("move", 1);

  return (
    <section
      aria-labelledby={headingId}
      className={cn(
        "flex min-w-0 flex-col rounded-sm border bg-card text-card-foreground",
        layout === "sticky" && "sticky top-4 max-h-[calc(100dvh-2rem)]",
      )}
    >
      <header className="space-y-2 border-b p-3">
        <div className="flex items-center justify-between gap-2">
          <h2 id={headingId} className="text-sm font-semibold">
            Day schedule
          </h2>
          <ButtonGroup aria-label="Schedule view">
            {(["day", "list"] as const).map((option) => (
              <Button
                key={option}
                type="button"
                size="xs"
                variant={view === option ? "secondary" : "outline"}
                aria-pressed={view === option}
                onClick={() => setView(option)}
              >
                {option === "day" ? "Day" : "List"}
              </Button>
            ))}
          </ButtonGroup>
        </div>
        <div className="flex items-center gap-1">
          <Button
            type="button"
            size="icon-sm"
            variant="ghost"
            aria-label="Previous day"
            onClick={() => setBrowseDate(addCalendarDays(browseDate, -1))}
          >
            <ChevronLeftIcon />
          </Button>
          <p className="min-w-0 flex-1 text-center text-sm font-medium" aria-live="polite">
            {dayHeading}
          </p>
          <Button
            type="button"
            size="icon-sm"
            variant="ghost"
            aria-label="Next day"
            onClick={() => setBrowseDate(addCalendarDays(browseDate, 1))}
          >
            <ChevronRightIcon />
          </Button>
        </div>
        <p className="text-center text-xs text-muted-foreground">
          {timezone} · UTC{startOffset}
          {startOffset !== endOffset ? ` → ${endOffset}` : ""}
          {clockChange ? ` · ${elapsed / 60}-hour day` : ""}
          {item.timezone !== timezone ? ` · slots follow ${item.timezone}` : ""}
        </p>
        {draftDay && browseDate !== draftDay ? (
          <Button type="button" size="xs" variant="outline" className="w-full" onClick={() => setBrowseDate(draftDay)}>
            <CrosshairIcon aria-hidden="true" />
            Back to draft day
          </Button>
        ) : null}
        {queryNotice}
        {view === "day" && markedCount > 0 ? (
          <p className="flex flex-wrap items-center gap-1 text-xs">
            <InfoIcon aria-hidden="true" className="size-3.5" />
            {markedCount} short or overlapping {markedCount === 1 ? "booking" : "bookings"} shown as marks.
            <Button type="button" variant="link" size="xs" className="h-auto p-0" onClick={() => setView("list")}>
              View list
            </Button>
          </p>
        ) : null}
      </header>
      <section
        ref={scrollRef}
        aria-label={view === "day" ? "Day timeline" : "Day list"}
        // biome-ignore lint/a11y/noNoninteractiveTabindex: Keyboard users need to focus and scroll the day schedule.
        tabIndex={0}
        className={cn(
          "overflow-y-auto overscroll-contain outline-none focus-visible:ring-3 focus-visible:ring-ring/30",
          layout === "sticky" ? "min-h-48 flex-1" : "max-h-[28rem]",
        )}
      >
        {view === "day" ? canvasBody : listBody}
      </section>
      <div className="space-y-2 border-t p-3">
        <div className="flex flex-wrap items-baseline justify-between gap-x-2 text-sm">
          <span className="font-medium">Draft</span>
          <span className="tabular-nums">{draftText}</span>
        </div>
        <StatusLine status={shownStatus} />
        {adjustable ? (
          <div className="flex flex-wrap gap-2">
            {(["move", "start", "end"] as const).map((mode) => (
              <ButtonGroup key={mode} aria-label={MODE_LABEL[mode]}>
                <ButtonGroupText className="px-2 text-xs">{MODE_LABEL[mode]}</ButtonGroupText>
                {([-1, 1] as const).map((direction) => {
                  const available = canStep(mode, direction);
                  return (
                    <Button
                      key={direction}
                      type="button"
                      size="icon-sm"
                      variant="outline"
                      aria-label={`${MODE_LABEL[mode]} ${direction < 0 ? "earlier" : "later"}`}
                      aria-describedby={hintId}
                      aria-disabled={available ? undefined : true}
                      className="aria-disabled:opacity-50"
                      onClick={() => {
                        if (available) step(mode, direction);
                      }}
                      onKeyDown={(event) => {
                        if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
                        event.preventDefault();
                        step(mode, event.key === "ArrowUp" ? -1 : 1);
                      }}
                    >
                      {direction < 0 ? <ChevronUpIcon /> : <ChevronDownIcon />}
                    </Button>
                  );
                })}
              </ButtonGroup>
            ))}
          </div>
        ) : (
          <p className="text-xs text-muted-foreground">{blocker}</p>
        )}
        {moveBlocked ? (
          <p className="text-xs text-muted-foreground">No slot keeps this duration. Use the time fields.</p>
        ) : null}
        <p id={hintId} className="text-xs text-muted-foreground">
          ↑/↓ adjusts by one slot · Esc cancels a drag
        </p>
        <p aria-live="polite" className="sr-only">
          {announcement}
        </p>
      </div>
    </section>
  );
}
