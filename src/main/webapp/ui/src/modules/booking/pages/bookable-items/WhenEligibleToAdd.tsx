import { CatchBoundary } from "@tanstack/react-router";
import { type ReactNode, Suspense } from "react";
import { useEligibleBookingTargets } from "./bookableItemsAdministrationAccess";

function Eligible({ children }: { children: ReactNode }) {
  return useEligibleBookingTargets().data.length > 0 ? children : null;
}

/**
 * Renders `children`, such as the Add action, only for a user with an eligible target. While eligibility loads, or
 * if it cannot be read, Add stays offered and the add page explains an ineligible choice.
 */
export function WhenEligibleToAdd({ children }: { children: ReactNode }) {
  return (
    <CatchBoundary getResetKey={() => "eligible-to-add"} errorComponent={() => children}>
      <Suspense fallback={children}>
        <Eligible>{children}</Eligible>
      </Suspense>
    </CatchBoundary>
  );
}
