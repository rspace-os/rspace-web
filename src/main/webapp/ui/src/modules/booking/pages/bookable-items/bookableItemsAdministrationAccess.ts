import { useSuspenseQuery } from "@tanstack/react-query";
import { eligibleBookingTargetsQuery } from "./bookingConfigurationTargets";

export function useEligibleBookingTargets() {
  return useSuspenseQuery(eligibleBookingTargetsQuery());
}
