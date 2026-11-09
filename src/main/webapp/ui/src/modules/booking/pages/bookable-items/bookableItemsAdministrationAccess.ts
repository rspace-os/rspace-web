import { useQuery } from "@tanstack/react-query";
import { useCountBookingConfigurations } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { retryUnlessClientError } from "@/modules/common/queries/queryRetry";
import { eligibleBookingTargetsQuery } from "./bookingConfigurationTargets";

const ADMINISTRATION_ACCESS_STALE_TIME = 10 * 60 * 1000;

export function useEligibleBookingTargets() {
  const { data: currentUser } = useCurrentUserQuery();
  return useQuery(eligibleBookingTargetsQuery(currentUser.id));
}

function useReadableBookingConfigurationCount() {
  return useCountBookingConfigurations(
    { max: 1 },
    { query: { staleTime: ADMINISTRATION_ACCESS_STALE_TIME, retry: retryUnlessClientError } },
  );
}

/** Administration is useful when the caller can read a configuration or set up an eligible item. */
export function useCanOpenBookableItemsAdministration(): boolean {
  const readable = useReadableBookingConfigurationCount();
  const eligible = useEligibleBookingTargets();
  return (readable.data?.totalDocs ?? 0) > 0 || (eligible.data?.length ?? 0) > 0;
}
