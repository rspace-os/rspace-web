import { queryOptions } from "@tanstack/react-query";
import { bookingTargetKeys } from "@/modules/booking/domain/bookingKeys";
import { retryUnlessClientError } from "@/modules/common/queries/queryRetry";
import { searchBookingTargets } from "./bookingConfigurationApi";

export { BookingTargetSchema, MINIMUM_TARGET_QUERY_LENGTH, searchBookingTargets } from "./bookingConfigurationApi";

export const eligibleBookingTargetsQuery = (scope: number, token: string) =>
  queryOptions({
    queryKey: [...bookingTargetKeys.all, "browse", scope] as const,
    queryFn: ({ signal }) => searchBookingTargets("", token, signal),
    retry: retryUnlessClientError,
    staleTime: 10 * 60 * 1000,
  });
