import type { ReactNode } from "react";
import { QueryBoundary } from "@/modules/common/queries/QueryBoundary";
import { useEligibleBookingTargets } from "./bookableItemsAdministrationAccess";

function Eligible({ children }: { children: ReactNode }) {
  return useEligibleBookingTargets().data.length > 0 ? children : null;
}

/** Renders `children`, such as the Add action, only once the user is known to have an eligible target. */
export function WhenEligibleToAdd({ children }: { children: ReactNode }) {
  return (
    <QueryBoundary fallback={null} error={() => null}>
      <Eligible>{children}</Eligible>
    </QueryBoundary>
  );
}
