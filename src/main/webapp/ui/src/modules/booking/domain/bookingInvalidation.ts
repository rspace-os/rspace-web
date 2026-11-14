import type { QueryClient } from "@tanstack/react-query";
import {
  getSearchBookingCatalogueLocationsQueryKey,
  getSearchBookingCatalogueQueryKey,
} from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";

const operationPaths = {
  catalogue: getSearchBookingCatalogueQueryKey({ page: 1, limit: 1 })[0],
  locations: getSearchBookingCatalogueLocationsQueryKey({ page: 1, limit: 1 })[0],
};
export async function invalidateBookingListOperation(
  queryClient: QueryClient,
  operation: keyof typeof operationPaths,
): Promise<void> {
  await queryClient.invalidateQueries({ predicate: ({ queryKey }) => queryKey[0] === operationPaths[operation] });
}
