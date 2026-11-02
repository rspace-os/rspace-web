import { useQuery } from "@tanstack/react-query";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { eligibleBookingTargetsQuery } from "./bookingConfigurationTargets";

export function useEligibleBookingTargets() {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  return useQuery(eligibleBookingTargetsQuery(currentUser.id, token));
}
