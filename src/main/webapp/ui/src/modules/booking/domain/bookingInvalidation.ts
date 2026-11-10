import type { QueryClient } from "@tanstack/react-query";
import { getSearchCalendarEventsQueryKey } from "@/modules/common/api/v2/generated/booking-calendar/booking-calendar";
import {
  getSearchBookingCatalogueLocationsQueryKey,
  getSearchBookingCatalogueQueryKey,
  getSearchCalendarCatalogueQueryKey,
} from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";

const window = { start: "1970-01-01T00:00:00Z", end: "1970-01-02T00:00:00Z", mine: false, page: 1, limit: 1 };
const operationPaths = {
  catalogue: getSearchBookingCatalogueQueryKey({ page: 1, limit: 1 })[0],
  locations: getSearchBookingCatalogueLocationsQueryKey({ page: 1, limit: 1 })[0],
  calendarCatalogue: getSearchCalendarCatalogueQueryKey(window)[0],
  calendarEvents: getSearchCalendarEventsQueryKey(window)[0],
};
export async function invalidateBookingListOperation(
  queryClient: QueryClient,
  operation: keyof typeof operationPaths,
): Promise<void> {
  await queryClient.invalidateQueries({ predicate: ({ queryKey }) => queryKey[0] === operationPaths[operation] });
}
