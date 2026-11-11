import * as v from "valibot";
import { formatIsoWeekday, formatOpeningRange, mapOpeningHours } from "@/modules/booking/domain/bookingOpeningHours";
import {
  type BookingTimeFormat,
  bookingDateTimeLocale,
  formatBookingDateTime,
} from "@/modules/booking/domain/bookingTime";
import {
  getListBookingConfigurationAuditEventsQueryOptions,
  listBookingConfigurationAuditEvents,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { ApiV2AuditEvent } from "@/modules/common/api/v2/generated/models/apiV2AuditEvent";
import type { ApiV2AuditEventAction } from "@/modules/common/api/v2/generated/models/apiV2AuditEventAction";
import type { ListBookingConfigurationAuditEventsParams } from "@/modules/common/api/v2/generated/models/listBookingConfigurationAuditEventsParams";
import { BookingOpeningHoursEntrySchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import i18n from "@/modules/common/i18n";
import { formatList } from "@/modules/common/i18n/listFormat";
import { formatNumber } from "@/modules/common/i18n/localeFormat";

const AUDIT_EVENT_ID = /^[0-9a-f]{64}$/;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const DAY_MILLISECONDS = 86_400_000;
const MAX_AUDIT_DAYS = 183;

// The mapped form only lets the generated interface satisfy useTableList's Record<string, unknown> row constraint.
export type AuditRow = { [K in keyof ApiV2AuditEvent]: ApiV2AuditEvent[K] } & { rowId: string };
export type AuditDateRange = { from: string; to: string };
export type AuditDateField = "from" | "to";
export type AuditDateError = "required" | "invalid" | "inverted" | "tooWide";
type AuditDateValidation =
  | { valid: true; range: AuditDateRange; dates: { from: Date; to: Date } }
  | { valid: false; fields: Partial<Record<AuditDateField, AuditDateError>> };

/**
 * The audit actions Booking records: bookings are created (CREATE) and edited or cancelled (WRITE);
 * configurations are also deleted (DELETE) and restored (RESTORE).
 */
export const AUDIT_ACTIONS = [
  "CREATE",
  "WRITE",
  "DELETE",
  "RESTORE",
] as const satisfies readonly ApiV2AuditEventAction[];
export type AuditAction = Extract<ApiV2AuditEventAction, (typeof AUDIT_ACTIONS)[number]>;

/** A Booking-level reading of an audit event. A WRITE that sets the state to CANCELLED is a cancellation. */
export type AuditEventKind = "created" | "changed" | "cancelled" | "deleted" | "permanentlyDeleted" | "restored";

export function auditEventKind(event: Pick<ApiV2AuditEvent, "action" | "payload">): AuditEventKind | null {
  if (event.action === "CREATE") return "created";
  if (event.action === "WRITE") return event.payload.state === "CANCELLED" ? "cancelled" : "changed";
  if (event.action === "DELETE") return typeof event.payload.deletedAt === "string" ? "permanentlyDeleted" : "deleted";
  if (event.action === "RESTORE") return "restored";
  return null;
}

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
  return { valid: true, range, dates: { from, to } };
}

export function auditRangeToQuery(range: AuditDateRange): { start: string; end: string } {
  const validation = validateAuditDateRange(range);
  if (!validation.valid) throw new RangeError("Invalid audit date range");
  const { from, to } = validation.dates;
  return {
    start: from.toISOString(),
    end: new Date(to.getTime() + DAY_MILLISECONDS).toISOString(),
  };
}

export async function fetchBookingConfigurationAudit(
  input: Pick<ListBookingConfigurationAuditEventsParams, "q" | "start" | "end"> & {
    configurationId: number;
    actions?: readonly AuditAction[];
    signal?: AbortSignal;
  },
) {
  const result = await listBookingConfigurationAuditEvents(input.configurationId, auditParameters(input), {
    signal: input.signal,
  });
  return mapBookingConfigurationAudit(result);
}

function mapBookingConfigurationAudit(result: Awaited<ReturnType<typeof listBookingConfigurationAuditEvents>>) {
  for (const event of result.docs) {
    if (!AUDIT_EVENT_ID.test(event.eventId)) throw new Error("Invalid audit event identity");
  }
  const occurrences = new Map<string, number>();
  return {
    rows: result.docs.map((event) => {
      const occurrence = (occurrences.get(event.eventId) ?? 0) + 1;
      occurrences.set(event.eventId, occurrence);
      return { ...event, rowId: `${event.eventId}:${occurrence}` };
    }),
    limit: result.limit,
    truncated: result.truncated,
    start: result.start,
    endExclusive: result.endExclusive,
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

function parseRecordedOpeningHours(value: unknown) {
  const parsed = v.safeParse(v.array(BookingOpeningHoursEntrySchema), value);
  if (!parsed.success) return undefined;
  try {
    return mapOpeningHours(parsed.output);
  } catch {
    return undefined;
  }
}

/** Opening hours as weekday names and scheduling-zone ranges; otherwise undefined. */
function openingValue(key: string, value: unknown, locale: string, timeLocale: string): string | undefined {
  if (key === "openingHours") {
    const exceptions = parseRecordedOpeningHours(parsedArray(value));
    if (exceptions !== undefined) {
      if (exceptions.length === 0) return "—";
      return formatList(
        exceptions
          .toSorted((left, right) => left.dayOfWeek - right.dayOfWeek)
          .map((entry) =>
            i18n.t("booking:bookableItemDetails.audit.weekdayRange", {
              day: formatIsoWeekday(entry.dayOfWeek, "long", locale),
              hours: formatOpeningRange(entry, timeLocale),
              lng: locale,
            }),
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
const ENUMERATED_KEYS = new Set(["state", "kind", "incrementUnit"]);

/** Booleans as Yes/No, states, kinds and increment units by their labels, and a target by its global ID. */
function wordValue(key: string, value: unknown, words: RecordedValueWords): string | undefined {
  if (typeof value === "boolean" || (BOOLEAN_KEYS.has(key) && (value === "true" || value === "false"))) {
    return value === true || value === "true" ? words.yes : words.no;
  }
  if (ENUMERATED_KEYS.has(key) && typeof value === "string") return words.values[value];
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
  payload: ApiV2AuditEvent["payload"],
  locale: string,
  words?: RecordedValueWords,
  timeFormat: BookingTimeFormat = "AUTOMATIC",
): Array<[string, string]> {
  return Object.entries(payload)
    .filter(([key, value]) => !(key === "cancellationReason" && value === null))
    .map(([key, value]) => {
      if ((key === "start" || key === "end") && (typeof value === "number" || typeof value === "string")) {
        const date = new Date(value);
        if (Number.isFinite(date.getTime())) {
          return [
            key,
            formatBookingDateTime(date.toISOString(), "UTC", bookingDateTimeLocale(timeFormat), timeFormat, {
              timeStyle: "long",
            }),
          ];
        }
      }
      const opening = openingValue(key, value, locale, bookingDateTimeLocale(timeFormat));
      if (opening !== undefined) return [key, opening];
      if (
        ["bufferBeforeMinutes", "bufferAfterMinutes", "maxBookingDurationMinutes", "slotGranularityMinutes"].includes(
          key,
        ) &&
        (typeof value === "number" ||
          (typeof value === "string" && value.trim() !== "" && Number.isFinite(Number(value))))
      )
        return [key, formatNumber(Number(value), locale)];
      const word = words && wordValue(key, value, words);
      if (word !== undefined) return [key, word];
      return [
        key,
        value === null || value === undefined ? "—" : typeof value === "object" ? JSON.stringify(value) : String(value),
      ];
    });
}

function auditParameters(input: Parameters<typeof fetchBookingConfigurationAudit>[0]) {
  return {
    q: input.q?.trim() || undefined,
    start: input.start,
    end: input.end,
    actions: input.actions ? [...input.actions] : undefined,
  };
}
export function bookingConfigurationAuditQuery(
  input: Parameters<typeof fetchBookingConfigurationAudit>[0],
  _generation: number,
) {
  return getListBookingConfigurationAuditEventsQueryOptions<Awaited<ReturnType<typeof fetchBookingConfigurationAudit>>>(
    input.configurationId,
    auditParameters(input),
    { query: { select: mapBookingConfigurationAudit, retry: false } },
  );
}
