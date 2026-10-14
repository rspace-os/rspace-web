import * as v from "valibot";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";
import { formatIsoWeekday, OpeningHoursSchema } from "@/modules/booking/domain/bookingOpeningHours";
import { apiV2Headers } from "@/modules/common/api/apiV2Headers";
import { formatList } from "@/modules/common/i18n/listFormat";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const SNAPSHOT_FINGERPRINT = /^[0-9a-f]{64}$/;
const DAY_MILLISECONDS = 86_400_000;
const MAX_AUDIT_DAYS = 183;

const AuditEventSchema = v.object({
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

type AuditEvent = v.InferOutput<typeof AuditEventSchema>;
export type AuditSnapshot = { snapshotDate: string; snapshotFingerprint: string };
export type AuditRow = AuditEvent & { rowId: string };
export type AuditDateRange = { from: string; to: string };
export type AuditDateField = "from" | "to";
export type AuditDateError = "required" | "invalid" | "inverted" | "tooWide";
type AuditDateValidation =
  | { valid: true; range: AuditDateRange; dates: { from: Date; to: Date } }
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
] as const;
export type AuditAction = (typeof AUDIT_ACTIONS)[number];

const AuditPageSchema = v.intersect([
  v2ListEnvelope(AuditEventSchema),
  v.object({
    snapshotDate: v.pipe(v.string(), v.regex(ISO_DATE)),
    snapshotFingerprint: v.pipe(v.string(), v.regex(SNAPSHOT_FINGERPRINT)),
  }),
]);

const AUDIT_PAGE_SIZE = 20;

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

export async function fetchBookingConfigurationAudit(input: {
  configurationId: number;
  q?: string;
  start?: string;
  end?: string;
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
  if (input.q !== undefined) parameters.set("q", input.q);
  if (input.start !== undefined) parameters.set("start", input.start);
  if (input.end !== undefined) parameters.set("end", input.end);
  if (input.search !== undefined && input.search.trim() !== "") parameters.set("search", input.search.trim());
  for (const action of input.actions ?? []) parameters.append("actions", action);
  if (input.snapshot !== undefined) {
    parameters.set("snapshotDate", input.snapshot.snapshotDate);
    parameters.set("snapshotFingerprint", input.snapshot.snapshotFingerprint);
  }

  const response = await fetchWithUiToken(
    `/api/v2/booking-configurations/${input.configurationId}/audit?${parameters}`,
    {
      headers: apiV2Headers(input.token),
      signal: input.signal,
    },
  );
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

/** Recorded values for one event. Unknown nested values retain their JSON representation. */
export function recordedValues(payload: AuditEvent["payload"], locale: string): Array<[string, string]> {
  const dateFormat = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "long",
    timeZone: "UTC",
  });
  return Object.entries(payload).map(([key, value]) => {
    if (key === "openingHours") {
      const hours = v.safeParse(OpeningHoursSchema, parsedArray(value));
      if (hours.success) {
        return [
          key,
          formatList(
            hours.output.map(
              (entry) => `${formatIsoWeekday(entry.dayOfWeek, "long", locale)} ${entry.start}–${entry.end}`,
            ),
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
