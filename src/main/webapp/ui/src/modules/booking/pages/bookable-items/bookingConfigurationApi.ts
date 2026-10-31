import { apiV2Headers } from "@/modules/common/api/apiV2Headers";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2PageEnvelope } from "@/modules/common/queries/v2Pagination";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import { type BookingConfiguration, BookingConfigurationSchema } from "./bookingConfigurationSchema";

export const BOOKING_CONFIGURATION_READ_FIELDS =
  "id,configurationVersion,target,enabled,state,timezone,slotGranularityMinutes,openingHours,bufferBeforeMinutes,bufferAfterMinutes,maxBookingDurationMinutes,allowDoubleBooking,createdAt,createdByName,updatedAt,effectiveRole,roleSources,capabilities,ownerHealth";

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

/** No readable booking configuration exists for a target. */
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
