import type { QueryClient } from "@tanstack/react-query";
import * as v from "valibot";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";
import { apiV2Headers, apiV2JsonHeaders } from "@/modules/common/api/apiV2Headers";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql/rsqlCodec";
import { type BookingConfiguration, BookingConfigurationSchema } from "./bookingConfigurationSchema";

const InstrumentLocationSchema = v.object({
  parentContainerName: v.nullable(v.string()),
  parentContainerGlobalId: v.nullable(v.string()),
});

/** Refreshes both catalogue query families after a configuration changes. */
export async function invalidateBookingCatalogueQueries(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-catalogue"] }),
    queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings", "booking-catalogue"] }),
  ]);
}

export async function archiveBookingConfiguration(id: number, version: number, token: string): Promise<void> {
  const response = await fetch(`/api/v2/booking-configurations/${id}`, {
    method: "DELETE",
    headers: apiV2Headers(token, { "If-Match": `"${version}"` }),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
}

export async function restoreBookingConfiguration(id: number, version: number, token: string): Promise<void> {
  const response = await fetch(`/api/v2/booking-configurations/${id}`, {
    method: "PATCH",
    headers: apiV2JsonHeaders(token, { "If-Match": `"${version}"` }),
    body: JSON.stringify({ state: "ACTIVE" }),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
}

export async function permanentlyDeleteBookingConfiguration(id: number, version: number, token: string): Promise<void> {
  const response = await fetch(`/api/v2/booking-configurations/${id}?permanent=true`, {
    method: "DELETE",
    headers: apiV2Headers(token, { "If-Match": `"${version}"` }),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
}

export const BOOKING_CONFIGURATION_READ_FIELDS =
  "id,configurationVersion,target,enabled,state,timezone,slotGranularityMinutes,openingHours,bufferBeforeMinutes,bufferAfterMinutes,maxBookingDurationMinutes,allowDoubleBooking,createdAt,createdByName,updatedAt,effectiveRole,roleSources,capabilities,ownerHealth";

export const bookingConfigurationTargetQueryKey = (globalId: string) =>
  ["api-v2", "booking-configurations", "target", globalId] as const;

const bookingConfigurationReadParameters = {
  depth: "1",
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

async function withLocation(
  configuration: BookingConfiguration,
  token: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration> {
  if (!configuration.target) return configuration;
  const target = configuration.target.value;
  if (target.parentContainerName && target.parentContainerGlobalId) return configuration;
  const parameters = new URLSearchParams({
    "fields[instruments]": "parentContainerName,parentContainerGlobalId",
  });
  let response: Response;
  try {
    response = await fetch(`/api/v2/instruments/${target.id}?${parameters}`, {
      headers: apiV2Headers(token),
      signal,
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    return configuration;
  }
  if (!response.ok) return configuration;
  const location = parseOrThrow(InstrumentLocationSchema, (await response.json()) as unknown);
  return {
    ...configuration,
    target: {
      ...configuration.target,
      value: { ...target, ...location },
    },
  };
}

export async function fetchBookingConfigurationByTarget(
  globalId: string,
  token: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration> {
  const configuration = await findBookingConfigurationByTarget(globalId, token, signal);
  if (!configuration || configuration.target?.globalId !== globalId) {
    throw new BookingConfigurationNotFoundError(`Expected exactly one booking configuration for ${globalId}`);
  }
  return configuration;
}

export async function fetchBookingConfigurationDetailsByTarget(
  globalId: string,
  token: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration> {
  return withLocation(await fetchBookingConfigurationByTarget(globalId, token, signal), token, signal);
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
  const response = await fetch(`/api/v2/booking-configurations?${parameters}`, {
    headers: apiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new BookingConfigurationRequestError(response.status);
  const configurations = parseOrThrow(
    v2ListEnvelope(BookingConfigurationSchema),
    (await response.json()) as unknown,
  ).docs;
  return configurations;
}
