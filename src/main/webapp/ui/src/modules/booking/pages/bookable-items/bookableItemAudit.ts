import * as v from "valibot";
import { bookingApiV2Headers } from "@/modules/booking/domain/apiV2";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";
import {
  formatIsoWeekday,
  formatOpeningRange,
  OpenDaysSchema,
  OpeningExceptionsSchema,
} from "@/modules/booking/domain/bookingOpeningHours";
import { type BookingTimeFormat, bookingHourCycle } from "@/modules/booking/domain/bookingTime";
import { formatList } from "@/modules/common/i18n/listFormat";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const SNAPSHOT_FINGERPRINT = /^[0-9a-f]{64}$/;
const DAY_MILLISECONDS = 86_400_000;
export const MAX_AUDIT_DAYS = 183;

export const AuditEventSchema = v.object({
  eventId: v.pipe(v.string(), v.regex(SNAPSHOT_FINGERPRINT)),
  timestamp: v.pipe(v.string(), v.isoTimestamp()),
  username: v.string(),
  fullName: v.nullish(v.string()),
  domain: v.string(),
  action: v.string(),
  description: v.nullish(v.string()),
  payload: v.record(v.string(), v.unknown()),
  target: v.nullish(v.string()),
});

export type AuditEvent = v.InferOutput<typeof AuditEventSchema>;
export type AuditSnapshot = { snapshotDate: string; snapshotFingerprint: string };
export type AuditRow = AuditEvent & { rowId: string };
export type AuditDateRange = { from: string; to: string };
export type AuditDateField = "from" | "to";
export type AuditDateError = "required" | "invalid" | "inverted" | "tooWide";
export type AuditDateValidation =
  | { valid: true; range: AuditDateRange }
  | { valid: false; fields: Partial<Record<AuditDateField, AuditDateError>> };

/**
 * The audit actions Booking records: bookings are created (CREATE) and edited or cancelled (WRITE);
 * configurations are also deleted (DELETE) and restored (RESTORE).
 */
export const AUDIT_ACTIONS = ["CREATE", "WRITE", "DELETE", "RESTORE"] as const;
export type AuditAction = (typeof AUDIT_ACTIONS)[number];

/** A Booking-level reading of an audit event. A WRITE that sets the state to CANCELLED is a cancellation. */
export type AuditEventKind = "created" | "changed" | "cancelled" | "deleted" | "restored";

export function auditEventKind(event: Pick<AuditEvent, "action" | "payload">): AuditEventKind | null {
  if (event.action === "CREATE") return "created";
  if (event.action === "WRITE") return event.payload.state === "CANCELLED" ? "cancelled" : "changed";
  if (event.action === "DELETE") return "deleted";
  if (event.action === "RESTORE") return "restored";
  return null;
}

const AuditPageSchema = v.object({
  docs: v.array(AuditEventSchema),
  totalDocs: v.pipe(v.number(), v.integer(), v.minValue(0)),
  limit: v.pipe(v.number(), v.integer(), v.minValue(1)),
  page: v.pipe(v.number(), v.integer(), v.minValue(1)),
  pagingCounter: v.pipe(v.number(), v.integer(), v.minValue(1)),
  totalPages: v.pipe(v.number(), v.integer(), v.minValue(0)),
  hasPrevPage: v.boolean(),
  hasNextPage: v.boolean(),
  prevPage: v.nullable(v.pipe(v.number(), v.integer(), v.minValue(1))),
  nextPage: v.nullable(v.pipe(v.number(), v.integer(), v.minValue(1))),
  snapshotDate: v.pipe(v.string(), v.regex(ISO_DATE)),
  snapshotFingerprint: v.pipe(v.string(), v.regex(SNAPSHOT_FINGERPRINT)),
});

export const AUDIT_PAGE_SIZE = 20;

function utcDate(value: string): Date | null {
  if (!ISO_DATE.test(value)) return null;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day ? date : null;
}

function plainUtcDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

export function auditPresetRange(days: 7 | 30 | 90, today = new Date()): AuditDateRange {
  const to = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate()));
  const from = new Date(to);
  from.setUTCDate(from.getUTCDate() - (days - 1));
  return { from: plainUtcDate(from), to: plainUtcDate(to) };
}

export function validateAuditDateRange(range: AuditDateRange): AuditDateValidation {
  const fields: Partial<Record<AuditDateField, AuditDateError>> = {};
  const from = range.from === "" ? null : utcDate(range.from);
  const to = range.to === "" ? null : utcDate(range.to);
  if (range.from === "") fields.from = "required";
  else if (from === null) fields.from = "invalid";
  if (range.to === "") fields.to = "required";
  else if (to === null) fields.to = "invalid";
  if (from === null || to === null) return { valid: false, fields };

  if (from.getTime() > to.getTime()) {
    return { valid: false, fields: { from: "inverted", to: "inverted" } };
  }
  const inclusiveDays = (to.getTime() - from.getTime()) / DAY_MILLISECONDS + 1;
  if (inclusiveDays > MAX_AUDIT_DAYS) {
    return { valid: false, fields: { from: "tooWide", to: "tooWide" } };
  }
  return { valid: true, range };
}

export function auditRangeToQuery(range: AuditDateRange): { dateFrom: string; dateTo: string } {
  const validation = validateAuditDateRange(range);
  if (!validation.valid) throw new RangeError("Invalid audit date range");
  const from = utcDate(range.from);
  const to = utcDate(range.to);
  if (from === null || to === null) throw new RangeError("Invalid audit date range");
  return {
    dateFrom: from.toISOString(),
    dateTo: new Date(to.getTime() + DAY_MILLISECONDS - 1).toISOString(),
  };
}

export async function fetchBookingConfigurationAudit(input: {
  configurationId: number;
  dateFrom?: string;
  dateTo?: string;
  search?: string;
  actions?: readonly AuditAction[];
  page: number;
  snapshot?: AuditSnapshot;
  token: string;
  signal?: AbortSignal;
}): Promise<{
  rows: AuditRow[];
  totalDocs: number;
  totalPages: number;
  hasPrevPage: boolean;
  hasNextPage: boolean;
  snapshotDate: string;
  snapshotFingerprint: string;
}> {
  const parameters = new URLSearchParams({
    page: String(input.page + 1),
    limit: String(AUDIT_PAGE_SIZE),
  });
  if (input.dateFrom !== undefined) parameters.set("dateFrom", input.dateFrom);
  if (input.dateTo !== undefined) parameters.set("dateTo", input.dateTo);
  if (input.search !== undefined && input.search.trim() !== "") parameters.set("search", input.search.trim());
  for (const action of input.actions ?? []) parameters.append("actions", action);
  if (input.snapshot !== undefined) {
    parameters.set("snapshotDate", input.snapshot.snapshotDate);
    parameters.set("snapshotFingerprint", input.snapshot.snapshotFingerprint);
  }

  const response = await fetch(`/api/v2/booking-configurations/${input.configurationId}/audit?${parameters}`, {
    headers: bookingApiV2Headers(input.token),
    signal: input.signal,
  });
  if (!response.ok) throw await parseApiV2Problem(response);

  const result = parseOrThrow(AuditPageSchema, (await response.json()) as unknown);
  const occurrences = new Map<string, number>();
  return {
    rows: result.docs.map((event) => {
      const occurrence = (occurrences.get(event.eventId) ?? 0) + 1;
      occurrences.set(event.eventId, occurrence);
      return { ...event, rowId: `${event.eventId}:${occurrence}` };
    }),
    totalDocs: result.totalDocs,
    totalPages: result.totalPages,
    hasPrevPage: result.hasPrevPage,
    hasNextPage: result.hasNextPage,
    snapshotDate: result.snapshotDate,
    snapshotFingerprint: result.snapshotFingerprint,
  };
}

/** The snapshot may hold the array itself or its JSON text. */
function parsedArray(value: unknown): unknown {
  if (typeof value !== "string") return value;
  try {
    return JSON.parse(value) as unknown;
  } catch {
    return value;
  }
}

/** Open days as weekday names, and each exception as `Weekday HH:mm–HH:mm`; otherwise undefined. */
function openingValue(key: string, value: unknown, locale: string): string | undefined {
  // A midnight close is stored as 24:00 and shown as 00:00, as everywhere else.
  if (key === "openingEnd" && value === "24:00") return "00:00";
  if (key === "openDays") {
    const days = v.safeParse(OpenDaysSchema, parsedArray(value));
    if (days.success)
      return formatList(
        days.output.toSorted((left, right) => left - right).map((day) => formatIsoWeekday(day, "long", locale)),
        locale,
        { type: "unit" },
      );
  }
  if (key === "openingExceptions") {
    const exceptions = v.safeParse(OpeningExceptionsSchema, parsedArray(value));
    if (exceptions.success) {
      if (exceptions.output.length === 0) return "—";
      return formatList(
        exceptions.output
          .toSorted((left, right) => left.dayOfWeek - right.dayOfWeek)
          .map(
            (exception) => `${formatIsoWeekday(exception.dayOfWeek, "long", locale)} ${formatOpeningRange(exception)}`,
          ),
        locale,
        { type: "unit" },
      );
    }
  }
  return undefined;
}

/** Translated words for recorded booleans and enumerated values, so they read as the rest of the UI does. */
export type RecordedValueWords = { yes: string; no: string; values: Readonly<Record<string, string>> };

const BOOLEAN_KEYS = new Set(["enabled", "allowDoubleBooking"]);

/** Booleans as Yes/No, states and kinds by their labels, and a bookable-item target by its global ID. */
function wordValue(key: string, value: unknown, words: RecordedValueWords): string | undefined {
  if (typeof value === "boolean" || (BOOLEAN_KEYS.has(key) && (value === "true" || value === "false"))) {
    return value === true || value === "true" ? words.yes : words.no;
  }
  if ((key === "state" || key === "kind") && typeof value === "string") return words.values[value];
  if (key === "target") {
    const target = parsedArray(value);
    if (
      typeof target === "object" &&
      target !== null &&
      "type" in target &&
      "id" in target &&
      target.type === "INSTRUMENT" &&
      typeof target.id === "number"
    ) {
      return `IN${target.id}`;
    }
  }
  return undefined;
}

/** Recorded values for one event. Nested values are JSON-encoded, never dropped. */
export function recordedValues(
  payload: AuditEvent["payload"],
  locale: string,
  words?: RecordedValueWords,
  timeFormat: BookingTimeFormat = "AUTOMATIC",
): Array<[string, string]> {
  const dateFormat = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "long",
    hourCycle: bookingHourCycle(timeFormat),
    timeZone: "UTC",
  });
  return Object.entries(payload)
    .filter(([key, value]) => !(key === "cancellationReason" && value === null))
    .map(([key, value]) => {
      if ((key === "start" || key === "end") && (typeof value === "number" || typeof value === "string")) {
        const date = new Date(value);
        if (Number.isFinite(date.getTime())) return [key, dateFormat.format(date)];
      }
      const opening = openingValue(key, value, locale);
      if (opening !== undefined) return [key, opening];
      const word = words && wordValue(key, value, words);
      if (word !== undefined) return [key, word];
      return [
        key,
        value === null || value === undefined
          ? "—"
          : typeof value === "object"
            ? JSON.stringify(value)
            : String(value as string | number | boolean),
      ];
    });
}
