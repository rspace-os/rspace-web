import type { TFunction } from "i18next";
import { withBookingProblemConflict } from "@/modules/booking/creation/useCreateBooking";
import type { BookingConflict } from "@/modules/booking/domain/availability";

type ProblemFeedback = {
  message?: string;
  conflict?: BookingConflict;
};

type AvailabilityFeedback = {
  conflicts: readonly BookingConflict[];
  failed: boolean;
  conflictSeverity: "warning" | "error";
};

export function bookingFormFeedback({
  problem,
  availability,
  interactionActive,
  fallbackError,
  t,
}: {
  problem: ProblemFeedback;
  availability: AvailabilityFeedback;
  interactionActive: boolean;
  fallbackError?: string;
  t: TFunction<"booking">;
}) {
  return {
    error: problem.message ?? fallbackError,
    warning: !interactionActive && availability.failed ? t("bookings.warnings.availabilityUnknown") : undefined,
    conflicts: interactionActive ? [] : withBookingProblemConflict(availability.conflicts, problem.conflict),
    conflictSeverity: problem.conflict ? "error" : availability.conflictSeverity,
  };
}
