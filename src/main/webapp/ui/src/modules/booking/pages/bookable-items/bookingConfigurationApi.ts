import { getSearchBookingConfigurationTargetsQueryOptions } from "@/modules/common/api/v2/generated/booking-configuration-targets/booking-configuration-targets";
import {
  deleteManyBookingConfigurations,
  getListBookingConfigurationsQueryOptions,
  listBookingConfigurations,
  updateManyBookingConfigurations,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { retryUnlessClientError } from "@/modules/common/queries/queryRetry";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import { bulkParameters } from "../all-bookable-items/AllBookableItemsContent";
import { type BookableItemsBulkAction } from "./bookableItemLifecycleHelpers";
import { type BookingConfiguration, mapBookingConfiguration } from "./bookingConfigurationSchema";
import { mapBookingTargetDirectory } from "./bookingConfigurationTargets";

export async function mutateBookableItems(
  action: BookableItemsBulkAction,
  selectedRowIds: readonly string[],
): Promise<void> {
  const params = bulkParameters(selectedRowIds);
  if (action === "archive") await deleteManyBookingConfigurations(params);
  else await updateManyBookingConfigurations({ enabled: action === "enable" }, params);
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
    where: serializeRsqlExpression<BookingConfiguration>({
      kind: "comparison",
      field: "target",
      operator: "equals",
      value: globalId,
    }),
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

export function bookingConfigurationTargetQuery(globalId: string) {
  return getListBookingConfigurationsQueryOptions<BookingConfiguration>(parameters(globalId), {
    query: {
      retry: retryUnlessClientError,
      select: (result) => oneConfiguration(globalId, result.docs.map(mapBookingConfiguration)),
    },
  });
}

export function findBookingConfigurationQueryOptions(globalId: string) {
  return getListBookingConfigurationsQueryOptions<BookingConfiguration | null>(parameters(globalId), {
    query: {
      select: (result) => {
        if (result.docs.length > 1) throw new Error(`Expected at most one booking configuration for ${globalId}`);
        return result.docs.length === 0 ? null : mapBookingConfiguration(result.docs[0]);
      },
    },
  });
}

export async function fetchBookingOwnershipCandidates(
  globalIds: readonly string[],

  signal?: AbortSignal,
): Promise<readonly BookingConfiguration[]> {
  if (globalIds.length === 0) return [];
  const where = serializeRsqlExpression<BookingConfiguration>({
    kind: "comparison",
    field: "target",
    operator: "in",
    value: globalIds,
  });
  return (
    await listBookingConfigurations(
      { ...parameters(globalIds[0]), where, limit: Math.min(globalIds.length, 100) },
      { signal },
    )
  ).docs.map(mapBookingConfiguration);
}

export function bookingRouteTargetQuery(globalId: string | undefined) {
  return getSearchBookingConfigurationTargetsQueryOptions(
    { q: globalId ?? "", limit: 20 },
    { query: { enabled: globalId !== undefined, select: mapBookingTargetDirectory } },
  );
}
