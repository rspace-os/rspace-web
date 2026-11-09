import { getSearchBookingConfigurationTargetsQueryOptions } from "@/modules/common/api/v2/generated/booking-configuration-targets/booking-configuration-targets";
import { retryUnlessClientError } from "@/modules/common/queries/queryRetry";
import { mapBookingTargetDirectory } from "./bookingConfigurationApi";

export { MINIMUM_TARGET_QUERY_LENGTH, searchBookingTargets } from "./bookingConfigurationApi";
export function eligibleBookingTargetsQuery(_scope: number) {
  return getSearchBookingConfigurationTargetsQueryOptions(
    { limit: 20 },
    { query: { select: mapBookingTargetDirectory, retry: retryUnlessClientError, staleTime: 10 * 60 * 1000 } },
  );
}
