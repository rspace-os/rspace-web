import { Temporal } from "@js-temporal/polyfill";
import type { AuditAction } from "@/modules/common/api/v2/generated/models/auditAction";
import type { ListBookingConfigurationAuditEventsParams } from "@/modules/common/api/v2/generated/models/listBookingConfigurationAuditEventsParams";

const AUDIT_EVENT_ID = /^[0-9a-f]{64}$/;

import * as v from "valibot";
import { formatIsoWeekday, mapOpeningHours } from "@/modules/booking/domain/bookingOpeningHours";
import { listBookingConfigurationAuditEvents } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { ApiV2AuditEvent } from "@/modules/common/api/v2/generated/models/apiV2AuditEvent";
import { BookingOpeningHoursEntrySchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { formatList } from "@/modules/common/i18n/listFormat";

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const MAX_AUDIT_DAYS = 183;

// The mapped form only lets the generated interface satisfy useTableList's Record<string, unknown> row constraint.
export type AuditRow = { [K in keyof ApiV2AuditEvent]: ApiV2AuditEvent[K] } & { rowId: string };
export type AuditDateRange = { from: string; to: string };
export type AuditDateField = "from" | "to";
export type AuditDateError = "required" | "invalid" | "inverted" | "tooWide";
type AuditDateValidation =
  | { valid: true; range: AuditDateRange; dates: { from: Temporal.PlainDate; to: Temporal.PlainDate } }
  | { valid: false; fields: Partial<Record<AuditDateField, AuditDateError>> };

export const AUDIT_ACTIONS = [
  "CREATE",
  "DELETE",
  "DOWNLOAD",
  "DUPLICATE",
  "EXPORT",
  "MOVE",
  "READ",
  "RENAME",
  "RESTORE",
  "SEARCH",
  "SHARE",
  "SIGN",
  "TRANSFER",
  "UNSHARE",
  "VIEW",
  "WITNESSED",
  "WRITE",
] as const satisfies readonly AuditAction[];
export type BookingAuditAction = Extract<AuditAction, (typeof AUDIT_ACTIONS)[number]>;

function plainDate(value: string): Temporal.PlainDate | null {
  if (!ISO_DATE.test(value)) return null;
  try {
    return Temporal.PlainDate.from(value);
  } catch {
    return null;
  }
}

/** The last `days` days up to `today`, a UTC calendar date. */
export function auditPresetRange(days: 7 | 30 | 90, today = Temporal.Now.plainDateISO("UTC")): AuditDateRange {
  return { from: today.subtract({ days: days - 1 }).toString(), to: today.toString() };
}

export function validateAuditDateRange(range: AuditDateRange): AuditDateValidation {
  const fields: Partial<Record<AuditDateField, AuditDateError>> = {};
  const from = range.from === "" ? null : plainDate(range.from);
  const to = range.to === "" ? null : plainDate(range.to);
  if (range.from === "") fields.from = "required";
  else if (from === null) fields.from = "invalid";
  if (range.to === "") fields.to = "required";
  else if (to === null) fields.to = "invalid";
  if (from === null || to === null) return { valid: false, fields };

  if (Temporal.PlainDate.compare(from, to) > 0) {
    return { valid: false, fields: { from: "inverted", to: "inverted" } };
  }
  const inclusiveDays = from.until(to, { largestUnit: "days" }).days + 1;
  if (inclusiveDays > MAX_AUDIT_DAYS) {
    return { valid: false, fields: { from: "tooWide", to: "tooWide" } };
  }
  return { valid: true, range, dates: { from, to } };
}

export function auditRangeToQuery(range: AuditDateRange): { start: Temporal.Instant; end: Temporal.Instant } {
  const validation = validateAuditDateRange(range);
  if (!validation.valid) throw new RangeError("Invalid audit date range");
  const { from, to } = validation.dates;
  return {
    start: from.toZonedDateTime("UTC").toInstant(),
    end: to.add({ days: 1 }).toZonedDateTime("UTC").toInstant(),
  };
}

export async function fetchBookingConfigurationAudit(
  input: Pick<ListBookingConfigurationAuditEventsParams, "q" | "start" | "end"> & {
    configurationId: number;
    actions?: readonly BookingAuditAction[];
    signal?: AbortSignal;
  },
) {
  const result = await listBookingConfigurationAuditEvents(
    input.configurationId,
    {
      q: input.q?.trim() || undefined,
      start: input.start,
      end: input.end,
      actions: input.actions ? [...input.actions] : undefined,
    },
    { signal: input.signal },
  );
  for (const event of result.docs)
    if (!AUDIT_EVENT_ID.test(event.eventId)) throw new Error("Invalid audit event identity");
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

/** Recorded values for one event. Unknown nested values retain their JSON representation. */
export function recordedValues(payload: ApiV2AuditEvent["payload"], locale: string): Array<[string, string]> {
  const dateFormat = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "long",
    timeZone: "UTC",
  });
  return Object.entries(payload).map(([key, value]) => {
    if (key === "openingHours") {
      const hours = parseRecordedOpeningHours(parsedArray(value));
      if (hours !== undefined) {
        return [
          key,
          formatList(
            hours.map((entry) => `${formatIsoWeekday(entry.dayOfWeek, "long", locale)} ${entry.start}–${entry.end}`),
            locale,
            { type: "unit" },
          ),
        ];
      }
    }
    if ((key === "start" || key === "end") && (typeof value === "number" || typeof value === "string")) {
      const date = new Date(value);
      if (Number.isFinite(date.getTime())) return [key, dateFormat.format(date)];
    }
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
