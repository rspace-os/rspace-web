import {
  getListBookingConfigurationsSuspenseQueryOptions,
  listBookingConfigurations,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { retryUnlessClientError } from "@/modules/common/queries/queryRetry";
import { type BookingConfiguration, mapBookingConfiguration } from "./bookingConfigurationSchema";

export const BOOKING_CONFIGURATION_READ_FIELDS =
  "id,configurationVersion,target,enabled,state,timezone,slotGranularityMinutes,openingHours,bufferBeforeMinutes,bufferAfterMinutes,maxBookingDurationMinutes,allowDoubleBooking,createdAt,createdByName,updatedAt,effectiveRole,roleSources,capabilities,ownerHealth";
class BookingConfigurationNotFoundError extends Error {
  readonly status = 404;
}

function parameters(globalId: string) {
  return {
    depth: 2,
    limit: 2,
    fields: {
      instruments: "id,globalId,name,deleted,parentContainer",
      containers: "id,globalId,name",
      "booking-configurations": BOOKING_CONFIGURATION_READ_FIELDS,
    },
    where: { target: globalId },
  };
}
function oneConfiguration(globalId: string, documents: BookingConfiguration[]): BookingConfiguration {
  if (documents.length > 1) throw new Error(`Expected exactly one booking configuration for ${globalId}`);
  if (documents.length === 0 || documents[0].target?.globalId !== globalId)
    throw new BookingConfigurationNotFoundError(`Expected exactly one booking configuration for ${globalId}`);
  return documents[0];
}
export async function fetchBookingConfigurationByTarget(
  globalId: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration> {
  return oneConfiguration(
    globalId,
    (await listBookingConfigurations(parameters(globalId), { signal })).docs.map(mapBookingConfiguration),
  );
}
export function fetchBookingConfigurationDetailsByTarget(
  globalId: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration> {
  return fetchBookingConfigurationByTarget(globalId, signal);
}
export async function findBookingConfigurationByTarget(
  globalId: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration | null> {
  const documents = (await listBookingConfigurations(parameters(globalId), { signal })).docs.map(
    mapBookingConfiguration,
  );
  if (documents.length > 1) throw new Error(`Expected at most one booking configuration for ${globalId}`);
  return documents[0] ?? null;
}
function matchingConfiguration(globalId: string, documents: BookingConfiguration[]): BookingConfiguration | null {
  if (documents.length > 1) throw new Error(`Expected exactly one booking configuration for ${globalId}`);
  return documents[0]?.target?.globalId === globalId ? documents[0] : null;
}
export function bookingConfigurationTargetQuery(globalId: string) {
  return getListBookingConfigurationsSuspenseQueryOptions<BookingConfiguration | null>(parameters(globalId), {
    query: {
      retry: retryUnlessClientError,
      // No single match reads as null, so the page shows "not found" itself instead of the error boundary.
      select: (result) => matchingConfiguration(globalId, result.docs.map(mapBookingConfiguration)),
    },
  });
}
