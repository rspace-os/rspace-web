import { useQuery } from "@tanstack/react-query";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { eligibleBookingTargetsQuery } from "./bookingConfigurationTargets";

export function useEligibleBookingTargets() {
  const { data: currentUser } = useCurrentUserQuery();
  return useQuery(eligibleBookingTargetsQuery(currentUser.id));
}
