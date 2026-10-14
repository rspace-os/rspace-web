import type { QueryClient } from "@tanstack/react-query";
import { queryOptions, skipToken } from "@tanstack/react-query";
import * as v from "valibot";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";
import {
  bookingCatalogueKeys,
  bookingConfigurationKeys,
  bookingKeys,
  bookingTargetKeys,
} from "@/modules/booking/domain/bookingKeys";
import { apiV2Headers, apiV2JsonHeaders } from "@/modules/common/api/apiV2Headers";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { retryUnlessClientError } from "@/modules/common/queries/queryRetry";
import { v2PageEnvelope } from "@/modules/common/queries/v2Pagination";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import { type BookingConfiguration, BookingConfigurationSchema } from "./bookingConfigurationSchema";

/** Refreshes both catalogue query families after a configuration changes. */
export async function invalidateBookingCatalogueQueries(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: bookingCatalogueKeys.all }),
    queryClient.invalidateQueries({ queryKey: bookingKeys.catalogue() }),
  ]);
}

export async function archiveBookingConfiguration(id: number, version: number, token: string): Promise<void> {
  const response = await fetchWithUiToken(`/api/v2/booking-configurations/${id}`, {
    method: "DELETE",
    headers: apiV2Headers(token, { "If-Match": `"${version}"` }),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
}

export async function restoreBookingConfiguration(id: number, version: number, token: string): Promise<void> {
  const response = await fetchWithUiToken(`/api/v2/booking-configurations/${id}`, {
    method: "PATCH",
    headers: apiV2JsonHeaders(token, { "If-Match": `"${version}"` }),
    body: JSON.stringify({ state: "ACTIVE" }),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
}

export async function permanentlyDeleteBookingConfiguration(id: number, version: number, token: string): Promise<void> {
  const response = await fetchWithUiToken(`/api/v2/booking-configurations/${id}?permanent=true`, {
    method: "DELETE",
    headers: apiV2Headers(token, { "If-Match": `"${version}"` }),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
}

export const BOOKING_CONFIGURATION_READ_FIELDS =
  "id,configurationVersion,target,enabled,state,timezone,slotGranularityMinutes,incrementUnit,alignToIncrement,openingHours,bufferBeforeMinutes,bufferAfterMinutes,maxBookingDurationMinutes,allowDoubleBooking,createdAt,createdByName,updatedAt,effectiveRole,roleSources,capabilities,ownerHealth";

export const bookingConfigurationTargetQueryKey = (globalId: string) =>
  bookingConfigurationTargetQuery(globalId, "").queryKey;

const bookingConfigurationReadParameters = {
  depth: "2",
  "fields[instruments]": "id,globalId,name,deleted,parentContainer",
  "fields[containers]": "id,globalId,name",
  "fields[booking-configurations]": BOOKING_CONFIGURATION_READ_FIELDS,
};

export class BookingConfigurationRequestError extends Error {
  constructor(readonly status: number) {
    super(`Booking configuration request failed with status ${status}`);
  }
}

/** No readable booking configuration exists for a target: it is unconfigured, missing, or not visible. */
class BookingConfigurationNotFoundError extends Error {
  readonly status = 404;
}

export async function fetchBookingConfigurationByTarget(
  globalId: string,
  token: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration> {
  const configurations = await readBookingConfigurationsByTarget(globalId, token, signal);
  if (configurations.length > 1) throw new Error(`Expected exactly one booking configuration for ${globalId}`);
  if (configurations.length === 0 || configurations[0].target?.globalId !== globalId) {
    throw new BookingConfigurationNotFoundError(`Expected exactly one booking configuration for ${globalId}`);
  }
  return configurations[0];
}

export async function fetchBookingConfigurationDetailsByTarget(
  globalId: string,
  token: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration> {
  return fetchBookingConfigurationByTarget(globalId, token, signal);
}

/** Returns a readable configuration for one Inventory target, or null when none is visible. */
export async function findBookingConfigurationByTarget(
  globalId: string,
  token: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration | null> {
  const configurations = await readBookingConfigurationsByTarget(globalId, token, signal);
  if (configurations.length > 1) throw new Error(`Expected at most one booking configuration for ${globalId}`);
  return configurations[0] ?? null;
}

async function readBookingConfigurationsByTarget(
  globalId: string,
  token: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration[]> {
  const where = serializeRsqlExpression<BookingConfiguration>({
    kind: "comparison",
    field: "target",
    operator: "equals",
    value: globalId,
  });
  const parameters = new URLSearchParams({
    ...bookingConfigurationReadParameters,
    limit: "2",
    where,
  });
  const response = await fetchWithUiToken(`/api/v2/booking-configurations?${parameters}`, {
    headers: apiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new BookingConfigurationRequestError(response.status);
  const configurations = parseOrThrow(
    v2PageEnvelope(BookingConfigurationSchema),
    (await response.json()) as unknown,
  ).docs;
  return configurations;
}

/** Returns the readable Booking configurations for a bounded set of Instrument global IDs. */
export async function fetchBookingOwnershipCandidates(
  globalIds: readonly string[],
  token: string,
  signal?: AbortSignal,
): Promise<readonly BookingConfiguration[]> {
  if (globalIds.length === 0) return [];
  const where = serializeRsqlExpression<BookingConfiguration>({
    kind: "comparison",
    field: "target",
    operator: "in",
    value: globalIds,
  });
  const parameters = new URLSearchParams({
    ...bookingConfigurationReadParameters,
    limit: String(Math.min(globalIds.length, 100)),
    where,
  });
  const response = await fetchWithUiToken(`/api/v2/booking-configurations?${parameters}`, {
    headers: apiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking configuration request failed with status ${response.status}`);
  return parseOrThrow(v2PageEnvelope(BookingConfigurationSchema), (await response.json()) as unknown).docs;
}

export const bookingConfigurationTargetQuery = (globalId: string, token: string) =>
  queryOptions({
    queryKey: bookingConfigurationKeys.target(globalId),
    queryFn: ({ signal }) => fetchBookingConfigurationDetailsByTarget(globalId, token, signal),
    retry: retryUnlessClientError,
  });

export const inventoryBookingConfigurationQuery = (globalId: string, token: string) =>
  queryOptions({
    queryKey: [...bookingConfigurationKeys.all, "inventory-action", globalId] as const,
    queryFn: ({ signal }) => findBookingConfigurationByTarget(globalId, token, signal),
  });

export const bookingConfigurationPickerTargetQuery = (globalId: string | undefined, token: string) =>
  queryOptions({
    queryKey: bookingConfigurationKeys.pickerTarget(globalId),
    queryFn: !globalId ? skipToken : ({ signal }) => fetchBookingConfigurationByTarget(globalId, token, signal),
    retry: false,
  });

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
  if (query.trim().length >= MINIMUM_TARGET_QUERY_LENGTH) parameters.set("q", query.trim());
  const response = await fetchWithUiToken(`/api/v2/booking-configuration-targets?${parameters}`, {
    headers: { "X-Requested-With": "XMLHttpRequest", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    signal,
  });
  if (!response.ok) throw await parseApiV2Problem(response);
  return parseOrThrow(v.object({ docs: v.array(BookingTargetSchema) }), (await response.json()) as unknown).docs;
}

export const bookingRouteTargetQuery = (globalId: string | undefined, token: string) =>
  queryOptions({
    queryKey: [...bookingTargetKeys.all, "route-target", globalId] as const,
    queryFn: globalId === undefined ? skipToken : ({ signal }) => searchBookingTargets(globalId, token, signal),
  });
