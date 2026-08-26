import * as v from "valibot";
import { bookingApiV2Headers } from "@/modules/booking/domain/apiV2";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";

/** Mirrors ApiV2AuditEvent. The audit trail has no stable per-event identifier. */
export const AuditEventSchema = v.object({
  timestamp: v.pipe(v.string(), v.isoTimestamp()),
  username: v.string(),
  fullName: v.nullish(v.string()),
  domain: v.string(),
  action: v.string(),
  description: v.nullish(v.string()),
  payload: v.record(v.string(), v.unknown()),
});

export type AuditEvent = v.InferOutput<typeof AuditEventSchema>;

/**
 * An audit event with a synthetic row key. The API returns no identifier, so
 * the key is derived from the event's position in the ordered result set;
 * it is stable for one response and must not be persisted or sent back.
 */
export type AuditRow = AuditEvent & { rowId: string };

const AuditEventsSchema = v2ListEnvelope(AuditEventSchema);

export const AUDIT_PAGE_SIZE = 20;

export async function fetchBookingConfigurationAudit(input: {
  configurationId: number;
  dateFrom?: string;
  dateTo?: string;
  page: number;
  token: string;
  signal?: AbortSignal;
}): Promise<{ rows: AuditRow[]; totalDocs: number; totalPages: number }> {
  const parameters = new URLSearchParams({
    page: String(input.page + 1),
    limit: String(AUDIT_PAGE_SIZE),
  });
  if (input.dateFrom !== undefined) parameters.set("dateFrom", input.dateFrom);
  if (input.dateTo !== undefined) parameters.set("dateTo", input.dateTo);

  const response = await fetch(`/api/v2/booking-configurations/${input.configurationId}/audit?${parameters}`, {
    headers: bookingApiV2Headers(input.token),
    signal: input.signal,
  });
  if (!response.ok) throw new Error(`Booking configuration audit request failed with status ${response.status}`);

  const result = parseOrThrow(AuditEventsSchema, (await response.json()) as unknown);
  return {
    rows: result.docs.map((event, index) => ({ ...event, rowId: `${result.pagingCounter + index}` })),
    totalDocs: result.totalDocs,
    totalPages: result.totalPages,
  };
}

/**
 * Recorded values for one event, as label/value pairs. Nested payload values
 * are JSON-encoded rather than dropped, so a change is never shown as empty.
 */
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
