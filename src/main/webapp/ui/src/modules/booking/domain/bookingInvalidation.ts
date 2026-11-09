import type { InvalidateOptions, QueryClient } from "@tanstack/react-query";
import { getSearchCalendarEventsQueryKey } from "@/modules/common/api/v2/generated/booking-calendar/booking-calendar";
import {
  getCountBookingCatalogueAvailabilityQueryKey,
  getSearchBookingCatalogueLocationsQueryKey,
  getSearchBookingCatalogueQueryKey,
  getSearchCalendarCatalogueQueryKey,
} from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";
import { getSearchBookingConfigurationTargetsQueryKey } from "@/modules/common/api/v2/generated/booking-configuration-targets/booking-configuration-targets";

const window = { start: "1970-01-01T00:00:00Z", end: "1970-01-02T00:00:00Z", mine: false, page: 1, limit: 1 };
const operationPaths = {
  targets: getSearchBookingConfigurationTargetsQueryKey({})[0],
  catalogue: getSearchBookingCatalogueQueryKey({ mine: false, page: 1, limit: 1 })[0],
  locations: getSearchBookingCatalogueLocationsQueryKey({ page: 1, limit: 1 })[0],
  calendarCatalogue: getSearchCalendarCatalogueQueryKey(window)[0],
  availabilityCounts: getCountBookingCatalogueAvailabilityQueryKey({ mine: false })[0],
  calendarEvents: getSearchCalendarEventsQueryKey(window)[0],
};

/** Refresh every parameter variant of a list operation, including its aggregate queries. */
export async function invalidateBookingListOperation(
  queryClient: QueryClient,
  operation: keyof typeof operationPaths,
  options?: InvalidateOptions,
): Promise<void> {
  const path = operationPaths[operation];
  await queryClient.invalidateQueries({ predicate: ({ queryKey }) => queryKey[0] === path }, options);
}
