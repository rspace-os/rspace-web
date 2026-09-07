import * as v from "valibot";
import { bookingApiV2Headers } from "@/modules/booking/domain/apiV2";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";
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

/** Recorded values for one event. Nested values are JSON-encoded, never dropped. */
export function recordedValues(payload: AuditEvent["payload"]): Array<[string, string]> {
  return Object.entries(payload).map(([key, value]) => [
    key,
    value === null || value === undefined
      ? "—"
      : typeof value === "object"
        ? JSON.stringify(value)
        : String(value as string | number | boolean),
  ]);
}
