import type { ListBookingConfigurationAuditEventsParams } from "@/modules/common/api/v2/generated/models/listBookingConfigurationAuditEventsParams";

const AUDIT_EVENT_ID = /^[0-9a-f]{64}$/;

import { listBookingConfigurationAuditEvents } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { ApiV2AuditEvent } from "@/modules/common/api/v2/generated/models/apiV2AuditEvent";

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
    signal?: AbortSignal;
  },
) {
  const result = await listBookingConfigurationAuditEvents(
    input.configurationId,
    {
      q: input.q,
      start: input.start,
      end: input.end,
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

/** Recorded values for one event. Nested values are JSON-encoded, never dropped. */
export function recordedValues(payload: ApiV2AuditEvent["payload"]): Array<[string, string]> {
  return Object.entries(payload).map(([key, value]) => [
    key,
    value === null || value === undefined
      ? "—"
      : typeof value === "object"
        ? JSON.stringify(value)
        : String(value as string | number | boolean),
  ]);
}
