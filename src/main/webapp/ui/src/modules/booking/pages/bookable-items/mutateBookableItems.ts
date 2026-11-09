import type { InvalidateOptions, QueryClient } from "@tanstack/react-query";
import * as v from "valibot";
import { invalidateBookingListOperation } from "@/modules/booking/domain/bookingInvalidation";
import { requireBookingValue } from "@/modules/booking/domain/bookingMapping";
import {
  deleteManyBookingConfigurations,
  invalidateCountBookingConfigurations,
  invalidateGetBookingCalendarSubscription,
  invalidateGetBookingConfiguration,
  invalidateGetBookingConfigurationAccess,
  invalidateGetMyBookingNotificationSubscription,
  invalidateListBookingConfigurationAuditEvents,
  invalidateListBookingConfigurations,
  updateManyBookingConfigurations,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { invalidateCountBookings, invalidateListBookings } from "@/modules/common/api/v2/generated/bookings/bookings";
import {
  invalidateGetUserBookingCalendarSubscription,
  invalidateListMyBookableItemCalendarSubscriptions,
} from "@/modules/common/api/v2/generated/users/users";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import type { BookableItemsBulkAction } from "./bookableItemLifecycleHelpers";
import type { BookingConfigurationRow } from "./bookingConfiguration";

const maximumBookableItemsSelection = 100;
export async function mutateBookableItems(
  action: BookableItemsBulkAction,
  selectedRowIds: readonly string[],
): Promise<readonly { id: number }[] | null> {
  if (selectedRowIds.length === 0) throw new Error("A bulk booking action requires at least one row ID");
  if (selectedRowIds.length > maximumBookableItemsSelection)
    throw new Error(`A bulk booking action cannot contain more than ${maximumBookableItemsSelection} row IDs`);
  const where = serializeRsqlExpression<BookingConfigurationRow>({
    kind: "comparison",
    field: "id",
    operator: "in",
    value: selectedRowIds,
  });
  try {
    const response =
      action === "archive"
        ? await deleteManyBookingConfigurations({ where })
        : await updateManyBookingConfigurations({ enabled: action === "enable" }, { where });
    const docs = response?.docs;
    if (!docs || docs.some(({ id }) => id == null || !Number.isSafeInteger(id) || id < 1)) return null;
    return docs.map((document) => ({ id: requireBookingValue(document.id, "id") }));
  } catch (error) {
    // A successful response with an unreadable receipt may already have committed.
    if (error instanceof v.ValiError || error instanceof SyntaxError) return null;
    throw error;
  }
}

export async function invalidateBookableItemRecords(
  queryClient: QueryClient,
  action: BookableItemsBulkAction,
  docs: readonly { id: number }[],
  options?: InvalidateOptions,
): Promise<void> {
  const refreshes: Promise<unknown>[] = [
    invalidateListBookingConfigurations(queryClient, undefined, options),
    invalidateCountBookingConfigurations(queryClient, undefined, options),
    invalidateBookingListOperation(queryClient, "targets", options),
    invalidateBookingListOperation(queryClient, "catalogue", options),
    invalidateBookingListOperation(queryClient, "locations", options),
    invalidateBookingListOperation(queryClient, "calendarCatalogue", options),
    invalidateBookingListOperation(queryClient, "availabilityCounts", options),
    ...docs.flatMap(({ id }) => [
      invalidateGetBookingConfiguration(queryClient, id, undefined, options),
      invalidateGetBookingConfigurationAccess(queryClient, id, options),
      invalidateBookingListOperation(queryClient, { accessGrantees: id }, options),
      invalidateListBookingConfigurationAuditEvents(queryClient, id, undefined, options),
      invalidateGetBookingCalendarSubscription(queryClient, id, options),
      invalidateGetMyBookingNotificationSubscription(queryClient, id, options),
    ]),
  ];
  if (action === "archive")
    refreshes.push(
      invalidateListBookings(queryClient, undefined, options),
      invalidateCountBookings(queryClient, undefined, options),
      invalidateBookingListOperation(queryClient, "calendarEvents", options),
      invalidateListMyBookableItemCalendarSubscriptions(queryClient, options),
      invalidateGetUserBookingCalendarSubscription(queryClient, options),
    );
  await Promise.all(refreshes);
}
