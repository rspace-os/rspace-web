import { useQuery } from "@tanstack/react-query";
import { requireBookingValue } from "@/modules/booking/domain/bookingMapping";
import { getSearchBookingConfigurationTargetsQueryOptions } from "@/modules/common/api/v2/generated/booking-configuration-targets/booking-configuration-targets";
import { useCountBookingConfigurations } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { BookingConfigurationTarget } from "@/modules/common/api/v2/generated/models/bookingConfigurationTarget";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { retryUnlessClientError } from "@/modules/common/queries/queryRetry";

const ADMINISTRATION_ACCESS_STALE_TIME = 10 * 60 * 1000;

export function useEligibleBookingTargets() {
  useCurrentUserQuery();
  return useQuery(
    getSearchBookingConfigurationTargetsQueryOptions(
      { limit: 20 },
      {
        query: {
          select: (response) => response.docs.map(requireEligibleBookingTarget),
          retry: retryUnlessClientError,
          staleTime: ADMINISTRATION_ACCESS_STALE_TIME,
        },
      },
    ),
  );
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

function requireEligibleBookingTarget(target: BookingConfigurationTarget) {
  if (target.deleted !== false) throw new Error("An eligible booking target must not be deleted");
  return {
    ...target,
    id: requireBookingValue(target.id, "id"),
    globalId: requireBookingValue(target.globalId, "globalId"),
    name: requireBookingValue(target.name, "name"),
  };
}
