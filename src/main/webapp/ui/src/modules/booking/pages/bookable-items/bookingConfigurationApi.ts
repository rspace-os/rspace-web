import type { QueryClient } from "@tanstack/react-query";
import { skipToken } from "@tanstack/react-query";
import { invalidateBookingListOperation } from "@/modules/booking/domain/bookingInvalidation";
import { requireBookingValue } from "@/modules/booking/domain/bookingMapping";
import {
  getSearchBookingConfigurationTargetsQueryOptions,
  getSearchBookingConfigurationTargetsSuspenseQueryOptions,
  searchBookingConfigurationTargets,
} from "@/modules/common/api/v2/generated/booking-configuration-targets/booking-configuration-targets";
import {
  deleteBookingConfiguration as generatedDeleteBookingConfiguration,
  updateBookingConfiguration as generatedUpdateBookingConfiguration,
  getListBookingConfigurationsQueryOptions,
  getListBookingConfigurationsSuspenseQueryOptions,
  listBookingConfigurations,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { BookingConfigurationTarget } from "@/modules/common/api/v2/generated/models/bookingConfigurationTarget";
import type { BookingConfigurationTargetControllerTargetDirectoryDocument } from "@/modules/common/api/v2/generated/models/bookingConfigurationTargetControllerTargetDirectoryDocument";
import { retryUnlessClientError } from "@/modules/common/queries/queryRetry";
import { isNonEmpty } from "@/util/ArrayUtils";
import { type BookingConfiguration, mapBookingConfiguration } from "./bookingConfigurationSchema";

export async function archiveBookingConfiguration(id: number, version: number): Promise<void> {
  await generatedDeleteBookingConfiguration(id, { "If-Match": `"${version}"` });
}

export async function restoreBookingConfiguration(id: number, version: number): Promise<void> {
  await generatedUpdateBookingConfiguration(id, { state: "ACTIVE" }, { "If-Match": `"${version}"` });
}

export async function permanentlyDeleteBookingConfiguration(id: number, version: number): Promise<void> {
  await generatedDeleteBookingConfiguration(id, { "If-Match": `"${version}"` }, { permanent: true });
}

export const BOOKING_CONFIGURATION_READ_FIELDS =
  "id,configurationVersion,target,enabled,state,timezone,slotGranularityMinutes,incrementUnit,openingHours,bufferBeforeMinutes,bufferAfterMinutes,maxBookingDurationMinutes,allowDoubleBooking,createdAt,createdByName,updatedAt,effectiveRole,roleSources,capabilities,ownerHealth";
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

export function inventoryBookingConfigurationQuery(globalId: string) {
  return getListBookingConfigurationsQueryOptions<BookingConfiguration | null>(parameters(globalId), {
    query: {
      select: (result) => {
        if (result.docs.length > 1) throw new Error(`Expected at most one booking configuration for ${globalId}`);
        return result.docs.length === 0 ? null : mapBookingConfiguration(result.docs[0]);
      },
    },
  });
}

/** Returns the readable Booking configurations for a bounded set of Instrument global IDs. */
export async function fetchBookingOwnershipCandidates(
  globalIds: readonly string[],

  signal?: AbortSignal,
): Promise<readonly BookingConfiguration[]> {
  if (!isNonEmpty(globalIds)) return [];
  return (
    await listBookingConfigurations(
      { ...parameters(globalIds[0]), where: { target: { in: globalIds } }, limit: Math.min(globalIds.length, 100) },
      { signal },
    )
  ).docs.map(mapBookingConfiguration);
}

export async function invalidateBookingCatalogueQueries(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    invalidateBookingListOperation(queryClient, "catalogue"),
    invalidateBookingListOperation(queryClient, "locations"),
    invalidateBookingListOperation(queryClient, "calendarCatalogue"),
    invalidateBookingListOperation(queryClient, "availabilityCounts"),
  ]);
}

export function bookingRouteTargetQuery(globalId: string | undefined) {
  return getSearchBookingConfigurationTargetsQueryOptions(globalId ? { q: globalId, limit: 20 } : { limit: 20 }, {
    query: {
      ...(globalId === undefined ? { queryFn: skipToken } : {}),
      enabled: globalId !== undefined,
      select: mapBookingTargetDirectory,
    },
  });
}

/** The route-target search for a known target, read with `useSuspenseQuery`; it shares `bookingRouteTargetQuery`'s key. */
export function bookingRouteTargetSuspenseQuery(globalId: string) {
  return getSearchBookingConfigurationTargetsSuspenseQueryOptions(
    { q: globalId, limit: 20 },
    { query: { select: mapBookingTargetDirectory } },
  );
}

export const MINIMUM_TARGET_QUERY_LENGTH = 2;

export function mapBookingTargetDirectory(
  response: BookingConfigurationTargetControllerTargetDirectoryDocument,
): BookingConfigurationTarget[] {
  return response.docs.map((target) => {
    if (target.deleted !== false) throw new Error("An eligible booking target must not be deleted");
    return {
      ...target,
      id: requireBookingValue(target.id, "id"),
      globalId: requireBookingValue(target.globalId, "globalId"),
      name: requireBookingValue(target.name, "name"),
    };
  });
}
export async function searchBookingTargets(query: string, signal?: AbortSignal): Promise<BookingConfigurationTarget[]> {
  const q = query.trim();
  return mapBookingTargetDirectory(
    await searchBookingConfigurationTargets(
      { q: q.length >= MINIMUM_TARGET_QUERY_LENGTH ? q : undefined, limit: 20 },
      { signal },
    ),
  );
}

export function bookingConfigurationPickerTargetQuery(globalId: string | undefined) {
  return getListBookingConfigurationsQueryOptions<BookingConfiguration>(parameters(globalId ?? ""), {
    query: {
      ...(globalId === undefined ? { queryFn: skipToken } : {}),
      enabled: globalId !== undefined,
      retry: false,
      select: (result) => oneConfiguration(globalId ?? "", result.docs.map(mapBookingConfiguration)),
    },
  });
}
