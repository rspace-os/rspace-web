import * as v from "valibot";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";

export const BookingTargetSchema = v.object({
  id: v.number(),
  globalId: v.string(),
  name: v.string(),
  deleted: v.literal(false),
});

export const MINIMUM_TARGET_QUERY_LENGTH = 2;

/** Searches eligible instruments; a blank query lists the first eligible instruments by name. */
export async function searchBookingTargets(query: string, token: string | undefined, signal?: AbortSignal) {
  const parameters = new URLSearchParams({ limit: "20" });
  // The API rejects a non-blank query shorter than two characters.
  if (query.trim().length >= MINIMUM_TARGET_QUERY_LENGTH) parameters.set("query", query.trim());
  const response = await fetch(`/api/v2/booking-configuration-targets?${parameters}`, {
    headers: { "X-Requested-With": "XMLHttpRequest", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    signal,
  });
  if (!response.ok) throw await parseApiV2Problem(response);
  return parseOrThrow(v.array(BookingTargetSchema), (await response.json()) as unknown);
}
