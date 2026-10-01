// PROTOTYPE ONLY. Explore the recurring booking workflow (plan 004) on top of the production booking form,
// day timeline, item card and lists. Network reads are served by story-local MSW handlers; nothing is written.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */

import { Temporal } from "@js-temporal/polyfill";
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { Link } from "@tanstack/react-router";
import { AlertTriangleIcon, CheckIcon, InfoIcon, Repeat2Icon, XIcon } from "lucide-react";
import { HttpResponse, http } from "msw";
import { setupWorker } from "msw/browser";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { OAUTH_TOKEN, oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { BookingSummaryCard } from "@/modules/booking/components/BookingSummaryCard";
import { BookingDayTimelineAside } from "@/modules/booking/creation/BookingDayTimelineAside";
import {
  BookingForm,
  type BookingFormState,
  type BookingFormSubmission,
  type EditableBooking,
} from "@/modules/booking/creation/BookingForm";
import { BookingItemInformationCard } from "@/modules/booking/creation/BookingItemInformation";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { useBookingTimelineDraft } from "@/modules/booking/creation/useBookingTimelineDraft";
import type { Booking, BookingListDocument } from "@/modules/booking/domain/booking";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import { type BookingWindowDraft, bookingHourCycle, isPlainDate } from "@/modules/booking/domain/bookingTime";
import { formatBookingEventDateTime, Panel } from "@/modules/booking/pages/bookings/BookingEventContext";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import { useTableList } from "@/modules/common/table-list/useTableList";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/modules/common/ui/alert-dialog";
import { Badge } from "@/modules/common/ui/badge";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { FieldError, FieldLegend, FieldSet } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { Label } from "@/modules/common/ui/label";
import { RadioGroup, RadioGroupItem } from "@/modules/common/ui/radio-group";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/modules/common/ui/table";
import { Textarea } from "@/modules/common/ui/textarea";
import { Heading } from "@/modules/common/ui/typography";
import { UserBadge } from "@/modules/common/ui/user-badge";

// ---------------------------------------------------------------------------
// Fixtures. The item's timezone differs from the viewer's display timezone: the rule expands in the
// display timezone (plan 004, contract 2) while opening hours are checked per occurrence in the item's.
// ---------------------------------------------------------------------------

const DATE = "2026-10-05"; // Monday
const CONFIGURATION_TIMEZONE = "Europe/London";
const DISPLAY_TIMEZONE = "Europe/Berlin";
const MAX_OCCURRENCES = 52;
const CANCELLATION_REASON_MAX_LENGTH = 500;

const item: BookableItemOption = {
  configurationId: 9001,
  targetId: 123,
  globalId: "IN123",
  name: "Confocal microscope",
  timezone: CONFIGURATION_TIMEZONE,
  slotGranularityMinutes: 15,
  openingStart: "08:00",
  openingEnd: "18:00",
  openDays: [1, 2, 3, 4, 5],
  openingExceptions: [],
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 240,
  allowDoubleBooking: false,
};

const target: Booking["target"] = {
  relationTo: "booking-instruments",
  value: {
    id: item.targetId,
    name: item.name,
    deleted: false,
    parentContainerName: "Imaging lab",
    parentContainerGlobalId: "IC456",
  },
  globalId: item.globalId,
};

/** Display-timezone wall clock, as the form reports it. */
const INITIAL_WINDOW: BookingWindowDraft = {
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

function toListDocument(booking: Booking): BookingListDocument {
  return { ...booking, canViewConfiguration: true, requesterId: booking.privacy === "full" ? 84 : null };
}

/** Existing events on the item. Served to the day timeline by MSW and used by the simulated dry run. */
const storedBookings: readonly Booking[] = [
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

// ---------------------------------------------------------------------------
// Story-local MSW. Reads answer from the fixtures above; writes are refused so nothing can reach a server.
// ---------------------------------------------------------------------------

function listEnvelope(docs: readonly Booking[]) {
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

const refused = () =>
  HttpResponse.json({ status: 501, detail: "Prototype: bookings are never written." }, { status: 501 });

const handlers = [
  oauthTokenHandler(true),
  http.get("/api/v2/bookings", ({ request }) => {
    const where = new URL(request.url).searchParams.get("where") ?? "";
    const before = /start=lt=([^;]+)/.exec(where)?.[1];
    const after = /end=gt=([^;]+)/.exec(where)?.[1];
    const docs = storedBookings.filter(
      (booking) =>
        (!before || Temporal.Instant.compare(booking.start, before) < 0) &&
        (!after || Temporal.Instant.compare(booking.end, after) > 0),
    );
    return HttpResponse.json(listEnvelope(docs));
  }),
  http.post("/api/v2/bookings", refused),
  http.patch("/api/v2/bookings/:id", refused),
  http.put("/api/v2/bookings/:id", refused),
  http.delete("/api/v2/bookings/:id", refused),
];

const prototypeWorker = setupWorker(...handlers);

// ---------------------------------------------------------------------------
// Recurrence grammar (plan 004, contract 1): DAILY | WEEKLY, interval 1..4, weekday set for WEEKLY,
// count 2..52 or an inclusive until date, both within one year of occurrence 1.
// ---------------------------------------------------------------------------

type Weekday = 1 | 2 | 3 | 4 | 5 | 6 | 7;
const WEEKDAYS: ReadonlyArray<{ value: Weekday; label: string; short: string }> = [
  { value: 1, label: "Monday", short: "Mon" },
  { value: 2, label: "Tuesday", short: "Tue" },
  { value: 3, label: "Wednesday", short: "Wed" },
  { value: 4, label: "Thursday", short: "Thu" },
  { value: 5, label: "Friday", short: "Fri" },
  { value: 6, label: "Saturday", short: "Sat" },
  { value: 7, label: "Sunday", short: "Sun" },
];

type RecurrenceRule = {
  frequency: "NONE" | "DAILY" | "WEEKLY";
  interval: number;
  weekdays: Weekday[];
  ends: { kind: "count"; count: number } | { kind: "until"; until: string };
};

const DEFAULT_RULE: RecurrenceRule = {
  frequency: "NONE",
  interval: 1,
  weekdays: [1, 3],
  ends: { kind: "count", count: 12 },
};

const CONFLICTING_RULE: RecurrenceRule = {
  frequency: "WEEKLY",
  interval: 1,
  weekdays: [1, 3, 6],
  ends: { kind: "count", count: 12 },
};

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));
const isTime = (value: string) => /^\d{2}:\d{2}$/.test(value);

function firstWeekday(draft: BookingWindowDraft): Weekday | undefined {
  return isPlainDate(draft.startDate) ? (Temporal.PlainDate.from(draft.startDate).dayOfWeek as Weekday) : undefined;
}

function sameRule(left: RecurrenceRule, right: RecurrenceRule): boolean {
  const key = (rule: RecurrenceRule) => JSON.stringify({ ...rule, weekdays: [...rule.weekdays].sort((a, b) => a - b) });
  return key(left) === key(right);
}

function ruleProblem(rule: RecurrenceRule, draft: BookingWindowDraft): string | undefined {
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
type Occurrence = OccurrenceWindow & { status: OccurrenceStatus; conflictWith?: string };

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
  const start = Temporal.Instant.from(window.start).toZonedDateTimeISO(CONFIGURATION_TIMEZONE);
  const end = Temporal.Instant.from(window.end).toZonedDateTimeISO(CONFIGURATION_TIMEZONE);
  if (!item.openDays.includes(start.dayOfWeek)) return false;
  const sameDay = start.toPlainDate().equals(end.toPlainDate());
  const endsAtMidnight = wallTime(end) === "00:00" && start.toPlainDate().add({ days: 1 }).equals(end.toPlainDate());
  if (!sameDay && !endsAtMidnight) return false;
  const endTime = sameDay ? wallTime(end) : "24:00";
  return wallTime(start) >= item.openingStart && endTime <= item.openingEnd;
}

/**
 * Stand-in for the server dry run (`POST /bookings` with `dryRun: true`). Only the fixtures above are
 * checked; it exists so the preview has something to show and is not how production will work.
 */
function dryRun(rule: RecurrenceRule, draft: BookingWindowDraft): Occurrence[] {
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

function useOccurrenceFormat() {
  const { i18n } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const language = i18n.resolvedLanguage ?? i18n.language;
  return React.useMemo(() => {
    const date = new Intl.DateTimeFormat(language, {
      weekday: "short",
      day: "numeric",
      month: "short",
      year: "numeric",
      timeZone: DISPLAY_TIMEZONE,
    });
    const time = new Intl.DateTimeFormat(language, {
      timeStyle: "short",
      hourCycle: bookingHourCycle(timeFormat),
      timeZone: DISPLAY_TIMEZONE,
    });
    return {
      date: (instant: string) => date.format(new Date(instant)),
      period: (start: string, end: string) => time.formatRange(new Date(start), new Date(end)),
    };
  }, [language, timeFormat]);
}

function describeRule(rule: RecurrenceRule, weekday?: Weekday): string {
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
// Repeat control, rendered inside BookingForm through its `afterWindowFields` slot.
// ---------------------------------------------------------------------------

function RepeatPanel({
  rule,
  draft,
  problem,
  onChange,
}: {
  rule: RecurrenceRule;
  draft: BookingWindowDraft;
  problem?: string;
  onChange: (next: RecurrenceRule) => void;
}) {
  const id = React.useId();
  const weekly = rule.frequency === "WEEKLY";
  const repeating = rule.frequency !== "NONE";
  const anchor = firstWeekday(draft);
  const first = isPlainDate(draft.startDate) ? Temporal.PlainDate.from(draft.startDate) : undefined;
  const defaultUntil = (first ?? Temporal.PlainDate.from(DATE)).add({ weeks: 10 }).toString();
  return (
    <FieldSet className="gap-4">
      <FieldLegend variant="label">Repeat</FieldLegend>
      <RadioGroup
        aria-label="Repeat"
        value={rule.frequency}
        onValueChange={(value) => onChange({ ...rule, frequency: String(value) as RecurrenceRule["frequency"] })}
        className="flex flex-wrap gap-4"
      >
        {(["NONE", "DAILY", "WEEKLY"] as const).map((frequency) => (
          <div key={frequency} className="flex items-center gap-2">
            <RadioGroupItem value={frequency} id={`${id}-${frequency}`} />
            <Label htmlFor={`${id}-${frequency}`}>
              {frequency === "NONE" ? "Does not repeat" : frequency === "DAILY" ? "Daily" : "Weekly"}
            </Label>
          </div>
        ))}
      </RadioGroup>
      {repeating ? (
        <>
          <div className="flex items-center gap-2 text-sm">
            <Label htmlFor={`${id}-interval`}>Every</Label>
            <Input
              id={`${id}-interval`}
              type="number"
              min={1}
              max={4}
              className="w-16"
              value={rule.interval}
              onChange={(event) => onChange({ ...rule, interval: clamp(Number(event.currentTarget.value) || 1, 1, 4) })}
            />
            <span>{weekly ? (rule.interval === 1 ? "week" : "weeks") : rule.interval === 1 ? "day" : "days"}</span>
          </div>
          {weekly ? (
            <FieldSet className="gap-2">
              <FieldLegend variant="label" className="mb-1">
                On
              </FieldLegend>
              <div className="flex flex-wrap gap-3">
                {WEEKDAYS.map((day) => {
                  const anchored = day.value === anchor;
                  const checked = anchored || rule.weekdays.includes(day.value);
                  return (
                    <label
                      key={day.value}
                      htmlFor={`${id}-day-${day.value}`}
                      className="flex items-center gap-1.5 text-sm"
                    >
                      <Checkbox
                        id={`${id}-day-${day.value}`}
                        checked={checked}
                        disabled={anchored}
                        aria-label={anchored ? `${day.label} (first occurrence)` : day.label}
                        onCheckedChange={(next) =>
                          onChange({
                            ...rule,
                            weekdays: next
                              ? [...rule.weekdays, day.value].sort((a, b) => a - b)
                              : rule.weekdays.filter((value) => value !== day.value),
                          })
                        }
                      />
                      <span aria-hidden="true">{day.short}</span>
                    </label>
                  );
                })}
              </div>
            </FieldSet>
          ) : null}
          <FieldSet className="gap-2">
            <FieldLegend variant="label" className="mb-1">
              Ends
            </FieldLegend>
            <RadioGroup
              aria-label="Ends"
              value={rule.ends.kind}
              onValueChange={(value) =>
                onChange({
                  ...rule,
                  ends: value === "until" ? { kind: "until", until: defaultUntil } : { kind: "count", count: 12 },
                })
              }
              className="flex flex-col gap-2"
            >
              <div className="flex items-center gap-2 text-sm">
                <RadioGroupItem value="count" id={`${id}-ends-count`} />
                <Label htmlFor={`${id}-ends-count`}>After</Label>
                <Input
                  type="number"
                  min={2}
                  max={MAX_OCCURRENCES}
                  className="w-20"
                  aria-label="Number of occurrences"
                  disabled={rule.ends.kind !== "count"}
                  value={rule.ends.kind === "count" ? rule.ends.count : 12}
                  onChange={(event) =>
                    onChange({ ...rule, ends: { kind: "count", count: Number(event.currentTarget.value) || 0 } })
                  }
                />
                <span>occurrences</span>
              </div>
              <div className="flex items-center gap-2 text-sm">
                <RadioGroupItem value="until" id={`${id}-ends-until`} />
                <Label htmlFor={`${id}-ends-until`}>On</Label>
                <Input
                  type="date"
                  className="w-44"
                  aria-label="Last occurrence date"
                  disabled={rule.ends.kind !== "until"}
                  min={first?.toString()}
                  max={first?.add({ years: 1 }).toString()}
                  value={rule.ends.kind === "until" ? rule.ends.until : ""}
                  onChange={(event) => onChange({ ...rule, ends: { kind: "until", until: event.currentTarget.value } })}
                />
              </div>
            </RadioGroup>
            {problem ? <FieldError>{problem}</FieldError> : null}
          </FieldSet>
          <p className="text-xs text-muted-foreground">
            Repeats at the same time in {DISPLAY_TIMEZONE}. Up to {MAX_OCCURRENCES} occurrences within one year.
          </p>
        </>
      ) : null}
    </FieldSet>
  );
}

// ---------------------------------------------------------------------------
// Occurrence preview, rendered from the (simulated) dry-run response.
// ---------------------------------------------------------------------------

function OccurrenceStatusBadge({ occurrence }: { occurrence: Occurrence }) {
  return (
    <span className="flex flex-wrap items-center gap-1">
      {occurrence.status === "ok" ? (
        <Badge variant="secondary">Available</Badge>
      ) : occurrence.status === "overlap" ? (
        <Badge variant="destructive">Overlap</Badge>
      ) : (
        <Badge variant="destructive">Closed</Badge>
      )}
      {occurrence.dstShifted ? <Badge variant="outline">Time shifted</Badge> : null}
      {occurrence.conflictWith ? (
        <span className="block w-full text-xs text-muted-foreground">{occurrence.conflictWith}</span>
      ) : null}
    </span>
  );
}

function OccurrencePreview({
  rule,
  draft,
  occurrences,
  problem,
}: {
  rule: RecurrenceRule;
  draft: BookingWindowDraft;
  occurrences: readonly Occurrence[];
  problem?: string;
}) {
  const format = useOccurrenceFormat();
  if (rule.frequency === "NONE") return null;
  const conflicts = occurrences.filter((occurrence) => occurrence.status !== "ok");
  const last = occurrences[occurrences.length - 1];
  return (
    <Card size="sm" className="gap-0 py-0">
      <CardHeader className="border-b py-4">
        <CardTitle className="text-base">Occurrences</CardTitle>
        <p className="text-xs text-muted-foreground">
          {describeRule(rule, firstWeekday(draft))}
          {last ? ` · ${occurrences.length} occurrences · until ${format.date(last.start)}` : ""}
          {` · times in ${DISPLAY_TIMEZONE}`}
        </p>
      </CardHeader>
      <CardContent className="p-0">
        {problem ? (
          <Alert variant="destructive" className="rounded-none border-x-0 border-t-0">
            <AlertTriangleIcon />
            <AlertTitle>{problem}</AlertTitle>
          </Alert>
        ) : occurrences.length === 0 ? (
          <Alert className="rounded-none border-x-0 border-t-0">
            <InfoIcon />
            <AlertTitle>Enter a valid date and time to preview the series.</AlertTitle>
          </Alert>
        ) : conflicts.length > 0 ? (
          <Alert variant="destructive" className="rounded-none border-x-0 border-t-0">
            <AlertTriangleIcon />
            <AlertTitle>
              {conflicts.length} of {occurrences.length} occurrences cannot be booked
            </AlertTitle>
            <AlertDescription>Every occurrence must be free before the series can be saved.</AlertDescription>
          </Alert>
        ) : (
          <Alert className="rounded-none border-x-0 border-t-0">
            <InfoIcon />
            <AlertTitle>All {occurrences.length} occurrences are available</AlertTitle>
            <AlertDescription>Each occurrence is checked again when you save.</AlertDescription>
          </Alert>
        )}
        {occurrences.length > 0 && !problem ? (
          // Cells wrap so the table never scrolls: axe requires scrollable regions to be focusable.
          <div className="[&_td]:whitespace-normal [&_th]:whitespace-normal">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-8">#</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead className="w-28">Time</TableHead>
                  <TableHead className="w-32">Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {occurrences.map((occurrence) => (
                  <TableRow
                    key={occurrence.position}
                    className={occurrence.status !== "ok" ? "bg-destructive/5" : undefined}
                  >
                    <TableCell className="text-muted-foreground">{occurrence.position}</TableCell>
                    <TableCell>{format.date(occurrence.start)}</TableCell>
                    <TableCell>
                      <BookingInstrumentTimeTooltip
                        start={occurrence.start}
                        end={occurrence.end}
                        displayTimeZone={DISPLAY_TIMEZONE}
                        instrumentTimeZone={CONFIGURATION_TIMEZONE}
                      >
                        <time dateTime={occurrence.start}>{format.period(occurrence.start, occurrence.end)}</time>
                      </BookingInstrumentTimeTooltip>
                    </TableCell>
                    <TableCell>
                      <OccurrenceStatusBadge occurrence={occurrence} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Create story: the production Add Booking page (form, item card, day timeline) plus Repeat and preview.
// ---------------------------------------------------------------------------

function CreateRecurringPrototype({ initialRule }: { initialRule: RecurrenceRule }) {
  const { t } = useTranslation("booking");
  const format = useOccurrenceFormat();
  const [rule, setRule] = React.useState(initialRule);
  const [saved, setSaved] = React.useState<string>();
  const bridge = useBookingTimelineDraft();
  const formContainerRef = React.useRef<HTMLDivElement>(null);
  const formState = bridge.formState;
  const draft = bridge.draft ?? INITIAL_WINDOW;
  // Only a window that passes the item's rules is expanded, as only a valid window reaches the server.
  const occurrences = React.useMemo(() => (formState?.window ? dryRun(rule, formState.draft) : []), [rule, formState]);
  const problem = ruleProblem(rule, draft);
  const conflicts = occurrences.filter((occurrence) => occurrence.status !== "ok");
  const repeating = rule.frequency !== "NONE";
  const blocked = problem !== undefined || conflicts.length > 0;
  const onStateChange = React.useCallback(
    (state: BookingFormState) => {
      bridge.onStateChange(state);
      setSaved(undefined);
    },
    [bridge.onStateChange],
  );
  const submit = async (_submission: BookingFormSubmission) => {
    await new Promise((resolve) => setTimeout(resolve, 300));
    setSaved(
      repeating
        ? `Series created: ${occurrences.length} bookings, first on ${format.date(occurrences[0].start)}.`
        : "Booking created.",
    );
  };

  return (
    <main className="space-y-6 p-4 sm:p-8">
      <Heading level={3} as="h1">
        {t("bookings.addTitle")}
      </Heading>
      <div className="@container">
        <div className="grid gap-6 @4xl:grid-cols-[minmax(0,1fr)_30rem]">
          <div ref={formContainerRef} className="min-w-0 space-y-6">
            <BookingForm
              mode="add"
              displayTimezone={DISPLAY_TIMEZONE}
              eventKind="BOOKING"
              initialTarget={item}
              initialDate={DATE}
              initialWindow={INITIAL_WINDOW}
              token={OAUTH_TOKEN}
              pending={false}
              // Locked so the item picker never queries the API; production keeps the picker.
              lockTarget
              error={
                bridge.interactionActive
                  ? undefined
                  : (problem ??
                    (conflicts.length === 0
                      ? undefined
                      : repeating
                        ? `${conflicts.length} of ${occurrences.length} occurrences cannot be booked.`
                        : t("bookings.errors.overlap")))
              }
              submissionBlocked={bridge.adjustmentPending || bridge.interactionActive || blocked}
              showMobileItemInformation
              showRulesSummary={false}
              onStateChange={onStateChange}
              onDraftChange={bridge.onDraftChange}
              onTargetChange={bridge.onTargetChange}
              windowAdjustment={bridge.windowAdjustment}
              windowAdjustmentTarget={bridge.windowAdjustmentTarget}
              afterWindowFields={
                <RepeatPanel
                  rule={rule}
                  draft={draft}
                  problem={problem}
                  onChange={(next) => {
                    setRule(next);
                    setSaved(undefined);
                  }}
                />
              }
              onSubmit={submit}
            />
            <OccurrencePreview rule={rule} draft={draft} occurrences={occurrences} problem={problem} />
            {saved ? (
              <p className="text-sm" role="status">
                {saved}
              </p>
            ) : null}
          </div>
          <div className="min-w-0 space-y-6">
            <div className="hidden @4xl:block">
              <BookingItemInformationCard item={item} displayTimezone={DISPLAY_TIMEZONE} date={draft.startDate} />
            </div>
            <BookingDayTimelineAside
              formContainerRef={formContainerRef}
              target={item}
              draft={draft}
              timezone={DISPLAY_TIMEZONE}
              token={OAUTH_TOKEN}
              onInteractionChange={bridge.onInteractionChange}
              onChange={(next) => bridge.onTimelineChange(next, item.globalId)}
            />
          </div>
        </div>
      </div>
    </main>
  );
}

// ---------------------------------------------------------------------------
// Series fixtures shared by the details, scope and list stories.
// ---------------------------------------------------------------------------

const STORED_RULE: RecurrenceRule = {
  frequency: "WEEKLY",
  interval: 1,
  weekdays: [1, 3],
  ends: { kind: "count", count: 12 },
};
const SERIES_ID_BASE = 9100;
const SERIES_POSITION = 3;

const seriesBookings: readonly Booking[] = expandRule(STORED_RULE, INITIAL_WINDOW).map((window) =>
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

type SeriesMembership = { position: number; count: number };

/** The optional `series` field of the booking document; busy events never carry it. */
function seriesOf(booking: Booking): SeriesMembership | undefined {
  if (booking.privacy === "busy") return undefined;
  const position = booking.id - SERIES_ID_BASE;
  return position >= 1 && position <= SERIES_COUNT ? { position, count: SERIES_COUNT } : undefined;
}

function editableBooking(booking: Booking): EditableBooking {
  if (booking.privacy !== "full") throw new Error("Prototype fixture must be a full booking");
  return { ...booking, canEdit: true, state: "CONFIRMED" };
}

const currentBooking = seriesBookings[SERIES_POSITION - 1];
const lastBooking = seriesBookings[SERIES_COUNT - 1];

function RepeatBadge({ rule = STORED_RULE }: { rule?: RecurrenceRule }) {
  return (
    <Badge variant="outline">
      <Repeat2Icon aria-hidden="true" />
      {describeRule(rule)}
    </Badge>
  );
}

function SeriesFacts({ booking, action }: { booking: Booking; action?: React.ReactNode }) {
  const { t, i18n } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const format = useOccurrenceFormat();
  const headingId = React.useId();
  const series = seriesOf(booking);
  const minutes = Math.max(0, Math.round((Date.parse(booking.end) - Date.parse(booking.start)) / 60000));
  const facts: Array<[string, React.ReactNode]> = [
    [
      t("bookings.details.when"),
      <span key="when">
        <BookingInstrumentTimeTooltip
          start={booking.start}
          end={booking.end}
          displayTimeZone={DISPLAY_TIMEZONE}
          instrumentTimeZone={booking.timezone}
        >
          <span>
            <time dateTime={booking.start}>
              {formatBookingEventDateTime(booking.start, DISPLAY_TIMEZONE, i18n.language, timeFormat)}
            </time>
            {" – "}
            <time dateTime={booking.end}>
              {formatBookingEventDateTime(booking.end, DISPLAY_TIMEZONE, i18n.language, timeFormat)}
            </time>
          </span>
        </BookingInstrumentTimeTooltip>
        <span className="text-muted-foreground">{` · ${t("bookableItemDetails.minutes", { count: minutes })}`}</span>
      </span>,
    ],
    ...(series
      ? ([
          [
            "Repeats",
            <span key="repeats" className="flex flex-wrap items-center gap-2">
              <RepeatBadge />
              <span>
                {series.position} of {series.count} · until {format.date(lastBooking.start)}
              </span>
            </span>,
          ],
        ] as Array<[string, React.ReactNode]>)
      : []),
    ...(booking.privacy === "full" && booking.bookedBy
      ? ([[t("bookings.details.bookedBy"), <UserBadge key="booked-by" name={booking.bookedBy} />]] as Array<
          [string, React.ReactNode]
        >)
      : []),
    [
      t("bookings.form.purpose"),
      booking.purpose ? (
        <span key="purpose" className="whitespace-pre-line">
          {booking.purpose}
        </span>
      ) : (
        <span key="purpose" className="text-muted-foreground">
          {t("bookings.details.noneProvided")}
        </span>
      ),
    ],
  ];
  return (
    <Panel heading={t("bookings.details.title")} headingId={headingId} action={action}>
      <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
        <dl className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4`}>
          {facts.map(([label, value]) => (
            <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME} key={label}>
              <dt className="font-medium">{label}</dt>
              <dd className="min-w-0">{value}</dd>
            </div>
          ))}
        </dl>
      </div>
    </Panel>
  );
}

// ---------------------------------------------------------------------------
// Scope choice for cancel and edit (THIS | FOLLOWING | SERIES), following DeleteBookingDialog's structure.
// ---------------------------------------------------------------------------

type Scope = "THIS" | "FOLLOWING" | "SERIES";

function affectedCount(scope: Scope, series: SeriesMembership): number {
  return scope === "THIS" ? 1 : series.count - series.position + 1;
}

function ScopeOptions({
  action,
  series,
  scope,
  ruleChanged = false,
  onChange,
}: {
  action: "cancel" | "edit";
  series: SeriesMembership;
  scope: Scope;
  ruleChanged?: boolean;
  onChange: (scope: Scope) => void;
}) {
  const id = React.useId();
  const format = useOccurrenceFormat();
  const remaining = affectedCount("FOLLOWING", series);
  const regenerated =
    action === "edit" ? " Bookings are re-created, and any occurrence moved earlier is replaced." : "";
  const options: ReadonlyArray<{ value: Scope; label: string; detail: string; disabled?: boolean }> = [
    {
      value: "THIS",
      label: "This occurrence",
      detail: ruleChanged
        ? "Not available while the repeat rule is changed."
        : `Only ${format.date(currentBooking.start)} (${series.position} of ${series.count}).`,
      disabled: ruleChanged,
    },
    {
      value: "FOLLOWING",
      label: "This and following occurrences",
      detail: `${remaining} bookings from ${format.date(currentBooking.start)} to ${format.date(lastBooking.start)}.${regenerated}`,
    },
    {
      value: "SERIES",
      label: "Entire series",
      detail: `${remaining} remaining bookings. Past occurrences stay in history.${regenerated}`,
    },
  ];
  return (
    <RadioGroup
      aria-label="Apply to"
      value={scope}
      onValueChange={(value) => onChange(String(value) as Scope)}
      className="flex flex-col gap-3"
    >
      {options.map((option) => (
        <div key={option.value} className="flex items-start gap-2">
          <RadioGroupItem
            value={option.value}
            id={`${id}-${option.value}`}
            disabled={option.disabled}
            className="mt-0.5"
          />
          <div className="grid gap-0.5">
            <Label htmlFor={`${id}-${option.value}`}>{option.label}</Label>
            <p className="text-xs text-muted-foreground">{option.detail}</p>
          </div>
        </div>
      ))}
    </RadioGroup>
  );
}

function CancelSeriesDialog({ booking, series }: { booking: Booking; series: SeriesMembership }) {
  const { t } = useTranslation("booking");
  const format = useOccurrenceFormat();
  const [open, setOpen] = React.useState(false);
  const [scope, setScope] = React.useState<Scope>("THIS");
  const [reason, setReason] = React.useState("");
  const [outcome, setOutcome] = React.useState<{ scope: Scope; count: number; reason: string }>();
  const reasonId = React.useId();
  const reasonHintId = `${reasonId}-hint`;
  const reasonCountId = `${reasonId}-count`;
  const count = affectedCount(scope, series);
  const period = `${format.date(booking.start)}, ${format.period(booking.start, booking.end)}`;
  const openChange = (next: boolean) => {
    if (next) {
      setScope("THIS");
      setReason("");
    }
    setOpen(next);
  };
  return (
    <div className="space-y-3">
      <AlertDialog open={open} onOpenChange={openChange}>
        <AlertDialogTrigger render={<Button type="button" size="sm" variant="destructive" />}>
          {t("bookings.actions.cancel")}
        </AlertDialogTrigger>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("bookings.cancelDialog.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("bookings.cancelDialog.description", { itemName: item.name, period })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <ScopeOptions action="cancel" series={series} scope={scope} onChange={setScope} />
          <div className="space-y-2">
            <label className="text-sm font-medium" htmlFor={reasonId}>
              {t("bookings.cancelDialog.reasonLabel")}
            </label>
            <Textarea
              id={reasonId}
              value={reason}
              maxLength={CANCELLATION_REASON_MAX_LENGTH}
              rows={3}
              aria-describedby={`${reasonHintId} ${reasonCountId}`}
              onChange={(event) => setReason(event.currentTarget.value)}
            />
            <div className="flex items-start justify-between gap-3 text-xs text-muted-foreground">
              <p id={reasonHintId}>{t("bookings.cancelDialog.reasonHint")}</p>
              <span id={reasonCountId} className="shrink-0" aria-live="polite">
                {t("bookings.cancelDialog.reasonCount", { count: reason.length })}
              </span>
            </div>
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel>{t("bookings.cancelDialog.keep")}</AlertDialogCancel>
            <AlertDialogAction
              type="button"
              variant="destructive"
              onClick={() => {
                setOutcome({ scope, count, reason: reason.trim() });
                setOpen(false);
              }}
            >
              {count === 1 ? t("bookings.actions.cancel") : `Cancel ${count} bookings`}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <p role="status" aria-live="polite" className="text-sm">
        {outcome ? (
          <>
            {outcome.count === 1 ? t("bookings.details.bookingCancelled") : `${outcome.count} bookings cancelled.`}
            {outcome.reason ? ` Reason: ${outcome.reason}` : ""}{" "}
            {outcome.scope === "THIS" ? (
              <Button type="button" size="xs" variant="link" onClick={() => setOutcome(undefined)}>
                Undo
              </Button>
            ) : null}
          </>
        ) : null}
      </p>
    </div>
  );
}

function EditSeriesPrototype({ booking, series }: { booking: Booking; series: SeriesMembership }) {
  const { t } = useTranslation("booking");
  const formId = React.useId();
  const headingId = React.useId();
  const saveRef = React.useRef<HTMLButtonElement>(null);
  const [formKey, setFormKey] = React.useState(0);
  const [rule, setRule] = React.useState(STORED_RULE);
  const [formState, setFormState] = React.useState<BookingFormState>();
  const [pending, setPending] = React.useState<BookingFormSubmission>();
  const [scope, setScope] = React.useState<Scope>("FOLLOWING");
  const [outcome, setOutcome] = React.useState<string>();
  const ruleChanged = !sameRule(rule, STORED_RULE);
  const draft = formState?.draft ?? INITIAL_WINDOW;
  const changed = ruleChanged || formState?.dirty === true;
  // A changed window or rule previews the FOLLOWING result: the stored rule continues for the remaining count.
  const previewRule = React.useMemo<RecurrenceRule>(() => {
    if (ruleChanged || rule.ends.kind !== "count") return rule;
    return { ...rule, ends: { kind: "count", count: affectedCount("FOLLOWING", series) } };
  }, [rule, ruleChanged, series]);
  const problem = ruleProblem(previewRule, draft);
  const occurrences = React.useMemo(
    () => (changed && formState?.window ? dryRun(previewRule, formState.draft) : []),
    [changed, formState, previewRule],
  );
  const conflicts = occurrences.filter((occurrence) => occurrence.status !== "ok");
  // The form opens scope selection; validate only that scope before confirming the edit.
  const singleConflicts = formState?.window
    ? dryRun({ ...previewRule, frequency: "NONE" }, formState.draft).filter((occurrence) => occurrence.status !== "ok")
    : [];
  const scopedConflicts = scope === "THIS" ? singleConflicts : conflicts;
  const blocked = problem !== undefined;
  const reset = () => {
    setFormKey((key) => key + 1);
    setRule(STORED_RULE);
    setFormState(undefined);
    setOutcome(undefined);
  };
  return (
    <div className="space-y-6">
      <Panel
        heading={t("bookings.details.edit.title")}
        headingId={headingId}
        action={
          <span className="flex gap-3">
            <Button ref={saveRef} type="submit" size="xs" form={formId} disabled={blocked}>
              <CheckIcon aria-hidden="true" />
              {t("bookings.form.save")}
            </Button>
            <Button type="button" size="xs" variant="ghost" onClick={reset}>
              <XIcon aria-hidden="true" />
              {t("bookings.details.edit.discard")}
            </Button>
          </span>
        }
      >
        <BookingForm
          key={formKey}
          mode="edit"
          layout="inline"
          formId={formId}
          displayTimezone={DISPLAY_TIMEZONE}
          token={OAUTH_TOKEN}
          pending={false}
          booking={editableBooking(booking)}
          configuration={item}
          error={problem}
          submissionBlocked={blocked}
          onStateChange={setFormState}
          afterWindowFields={<RepeatPanel rule={rule} draft={draft} problem={problem} onChange={setRule} />}
          onSubmit={async (submission) => {
            setPending(submission);
            setScope(ruleChanged ? "FOLLOWING" : "THIS");
          }}
        />
      </Panel>
      {changed ? (
        <OccurrencePreview rule={previewRule} draft={draft} occurrences={occurrences} problem={problem} />
      ) : null}
      <AlertDialog open={pending !== undefined} onOpenChange={(open) => (open ? undefined : setPending(undefined))}>
        <AlertDialogContent finalFocus={saveRef}>
          <AlertDialogHeader>
            <AlertDialogTitle>Apply changes to which bookings?</AlertDialogTitle>
            <AlertDialogDescription>
              This booking is part of a series: {describeRule(STORED_RULE)}, {series.count} occurrences.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <ScopeOptions action="edit" series={series} scope={scope} ruleChanged={ruleChanged} onChange={setScope} />
          {scopedConflicts.length > 0 ? (
            <p role="alert" className="text-sm text-destructive">
              {scopedConflicts.length} occurrences cannot be booked. Choose another scope or change the booking.
            </p>
          ) : null}
          <AlertDialogFooter>
            <AlertDialogCancel>Back</AlertDialogCancel>
            <AlertDialogAction
              type="button"
              disabled={blocked || scopedConflicts.length > 0}
              onClick={() => {
                const count = affectedCount(scope, series);
                setOutcome(`Updated ${count} ${count === 1 ? "booking" : "bookings"}.`);
                setPending(undefined);
              }}
            >
              {`Update ${affectedCount(scope, series)} ${affectedCount(scope, series) === 1 ? "booking" : "bookings"}`}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      {outcome ? (
        <p role="status" className="text-sm">
          {outcome}
        </p>
      ) : null}
    </div>
  );
}

function ScopePrototype() {
  const series = seriesOf(currentBooking);
  if (!series) throw new Error("Prototype fixture must be a series booking");
  return (
    <main className="space-y-8 p-4 sm:p-8">
      <Heading level={3} as="h1">
        {item.name}
      </Heading>
      <div className="max-w-3xl space-y-8">
        <EditSeriesPrototype booking={currentBooking} series={series} />
        <SeriesFacts booking={currentBooking} />
        <CancelSeriesDialog booking={currentBooking} series={series} />
      </div>
    </main>
  );
}

// ---------------------------------------------------------------------------
// Series representation in lists: the production TableList and booking summary card.
// ---------------------------------------------------------------------------

type EventRow = Booking & { seriesLabel: string | null };

const listRows: readonly EventRow[] = [
  seriesBookings[SERIES_POSITION - 1],
  seriesBookings[SERIES_POSITION],
  storedBookings[2],
  storedBookings[0],
].map((booking) => {
  const series = seriesOf(booking);
  return { ...booking, seriesLabel: series ? `${series.position} of ${series.count}` : null };
});

function SeriesEventTable() {
  const { t, i18n } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const formatter = React.useMemo(
    () =>
      new Intl.DateTimeFormat(i18n.language, {
        dateStyle: "medium",
        timeStyle: "short",
        hourCycle: bookingHourCycle(timeFormat),
        timeZone: DISPLAY_TIMEZONE,
      }),
    [i18n.language, timeFormat],
  );
  const config = React.useMemo(
    () =>
      resolveCollectionConfig({
        slug: "recurring-prototype-events",
        idField: "id",
        useAsTitle: "start",
        defaultColumns: ["start", "seriesLabel", "bookedBy", "purpose"],
        pagination: { defaultLimit: 10, limits: [10] },
        labels: { singularKey: "booking:calendar.event", pluralKey: "booking:bookableItemDetails.upcoming" },
        fields: [
          { name: "id", type: "number", labelKey: "booking:calendar.fields.id", list: false },
          {
            name: "start",
            type: "dateTime",
            labelKey: "booking:bookings.form.time",
            list: {
              width: 280,
              minWidth: 220,
              dependencies: ["end"],
              renderCell: ({ row }) => (
                <BookingInstrumentTimeTooltip
                  start={row.start}
                  end={row.end}
                  displayTimeZone={DISPLAY_TIMEZONE}
                  instrumentTimeZone={row.timezone}
                >
                  <time dateTime={row.start}>{formatter.formatRange(new Date(row.start), new Date(row.end))}</time>
                </BookingInstrumentTimeTooltip>
              ),
            },
          },
          { name: "end", type: "dateTime", labelKey: "booking:myBookings.fields.end", list: false },
          {
            name: "seriesLabel",
            type: "text",
            nullable: true,
            labelKey: "booking:calendar.event",
            label: "Repeats",
            list: {
              width: 220,
              renderCell: ({ row }) =>
                row.seriesLabel ? (
                  <span className="flex flex-wrap items-center gap-2">
                    <RepeatBadge />
                    <span className="text-muted-foreground">{row.seriesLabel}</span>
                  </span>
                ) : null,
            },
          },
          {
            name: "bookedBy",
            type: "text",
            nullable: true,
            labelKey: "booking:bookableItemDetails.events.actor",
            label: t("bookableItemDetails.events.actor"),
            list: {
              width: 220,
              renderCell: ({ row }) =>
                row.privacy === "busy" || row.bookedBy === null ? (
                  t("bookableItemDetails.events.busy")
                ) : (
                  <UserBadge name={row.bookedBy} />
                ),
            },
          },
          {
            name: "purpose",
            type: "text",
            nullable: true,
            labelKey: "booking:bookableItemDetails.events.purpose",
            list: { renderCell: ({ row }) => (row.privacy === "full" ? row.purpose : null) },
          },
        ],
      } satisfies CollectionConfig<EventRow>),
    [formatter, t],
  );
  const table = useTableList<EventRow>({
    config,
    dataSource: { type: "client", rows: listRows },
    features: { filtering: false, sorting: false, columns: false },
    queryString: false,
    reserveEmptyRows: false,
  });
  const rowActions = React.useMemo<TableListRowActions<EventRow>>(
    () => ({
      id: "actions",
      label: t("calendar.actions.label"),
      width: 160,
      minWidth: 140,
      renderCell: ({ row }) =>
        row.privacy === "full" ? (
          <Link
            className={buttonVariants({ size: "sm", variant: "outline" })}
            to="/booking/calendar/bookings/$id"
            params={{ id: String(row.id) }}
          >
            {t("calendar.actions.viewDetails")}
          </Link>
        ) : null,
      renderInteraction: () => null,
    }),
    [t],
  );
  return (
    <TableList
      {...table.tableProps}
      hideHeader
      emptyDescription={t("bookableItemDetails.events.empty")}
      presentations={{ table: "wide", cards: "narrow" }}
      rowActions={rowActions}
    />
  );
}

function SeriesInListsPrototype() {
  const series = seriesOf(currentBooking);
  return (
    <main className="space-y-8 p-4 sm:p-8">
      <Heading level={3} as="h1">
        {item.name}
      </Heading>
      <div className="max-w-4xl space-y-8">
        <SeriesFacts booking={currentBooking} />
        <section className="space-y-3">
          <Heading level={5} as="h2">
            Upcoming events
          </Heading>
          <SeriesEventTable />
        </section>
        <section className="space-y-3">
          <Heading level={5} as="h2">
            Summary card
          </Heading>
          <div className="max-w-md space-y-2">
            {series ? (
              <p className="flex flex-wrap items-center gap-2 text-sm">
                <RepeatBadge />
                <span className="text-muted-foreground">
                  {series.position} of {series.count}
                </span>
              </p>
            ) : null}
            <BookingSummaryCard booking={toListDocument(currentBooking)} timeZone={DISPLAY_TIMEZONE} />
          </div>
        </section>
      </div>
    </main>
  );
}

// ---------------------------------------------------------------------------
// Stories
// ---------------------------------------------------------------------------

function RecurringBookingPrototype({ story }: { story: "create" | "createConflicts" | "scopes" | "lists" }) {
  if (story === "scopes") return <ScopePrototype />;
  if (story === "lists") return <SeriesInListsPrototype />;
  return <CreateRecurringPrototype initialRule={story === "createConflicts" ? CONFLICTING_RULE : DEFAULT_RULE} />;
}

const meta = {
  title: "Booking/Prototypes/Recurring Bookings (Fable 5.1)",
  component: RecurringBookingPrototype,
  parameters: { layout: "fullscreen" },
  beforeEach: async () => {
    await prototypeWorker.start({ onUnhandledRequest: "bypass", quiet: true });
    return () => prototypeWorker.stop();
  },
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof RecurringBookingPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Create: Story = { args: { story: "create" } };
export const CreateWithConflicts: Story = { args: { story: "createConflicts" } };
export const CancelAndEditScopes: Story = { args: { story: "scopes" } };
export const SeriesInLists: Story = { args: { story: "lists" } };
