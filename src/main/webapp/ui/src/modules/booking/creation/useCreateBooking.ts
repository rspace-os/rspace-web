import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { TFunction } from "i18next";
import { ApiV2ProblemError, createBooking } from "@/modules/booking/domain/booking";
import type { BookingFormSubmission } from "./BookingForm";

function activeBookingQueryIntersects(
  queryKey: readonly unknown[],
  targetGlobalId: string,
  start: string,
  end: string,
): boolean {
  if (queryKey[0] !== "api-v2" || queryKey[1] !== "bookings") return false;
  if (queryKey[2] === "calendar-events") {
    const queryStart = queryKey[3];
    const queryEnd = queryKey[4];
    const targets = queryKey[5];
    return (
      typeof queryStart === "string" &&
      typeof queryEnd === "string" &&
      queryStart < end &&
      queryEnd > start &&
      (targets === "all" || (typeof targets === "string" && targets.split(",").includes(targetGlobalId)))
    );
  }
  if (queryKey[2] === "calendar-availability") {
    const rows = queryKey[3];
    const queryStart = queryKey[4];
    const queryEnd = queryKey[5];
    return (
      Array.isArray(rows) &&
      rows.some((row) => Array.isArray(row) && row[0] === targetGlobalId) &&
      typeof queryStart === "string" &&
      typeof queryEnd === "string" &&
      queryStart < end &&
      queryEnd > start
    );
  }
  return false;
}

function refreshIntersectingBookingQueries(
  queryClient: ReturnType<typeof useQueryClient>,
  submission: BookingFormSubmission,
) {
  void queryClient.invalidateQueries({
    predicate: (query) =>
      activeBookingQueryIntersects(
        query.queryKey,
        submission.target.globalId,
        submission.window.start,
        submission.window.end,
      ),
  });
}

export function bookingProblemKey(
  error: unknown,
):
  | "bookings.errors.generic"
  | "bookings.errors.endAfterStart"
  | "bookings.errors.duration"
  | "bookings.errors.maximumDuration"
  | "bookings.errors.overlap"
  | "bookings.errors.granularity"
  | "bookings.errors.openingHours"
  | "bookings.errors.targetUnavailable"
  | "bookings.errors.concurrentModification"
  | "bookings.errors.forbidden"
  | "bookings.errors.noLongerEditable" {
  if (!(error instanceof ApiV2ProblemError)) return "bookings.errors.generic";
  if (error.code === "errors.api.v2.booking.window") return "bookings.errors.endAfterStart";
  if (error.code === "errors.api.v2.booking.duration") return "bookings.errors.duration";
  if (error.code === "errors.api.v2.booking.maximumDuration") return "bookings.errors.maximumDuration";
  if (error.code === "errors.api.v2.booking.overlap") return "bookings.errors.overlap";
  if (error.code === "errors.api.v2.booking.granularity") return "bookings.errors.granularity";
  if (error.code === "errors.api.v2.booking.openingHours") return "bookings.errors.openingHours";
  if (error.code === "errors.api.v2.booking.target.unavailable") return "bookings.errors.targetUnavailable";
  if (error.status === 412 || error.code === "errors.api.v2.booking.concurrentModification") {
    return "bookings.errors.concurrentModification";
  }
  if (error.code === "errors.api.v2.forbidden") return "bookings.errors.forbidden";
  if (error.code === "errors.api.v2.booking.state.transition") return "bookings.errors.noLongerEditable";
  return "bookings.errors.generic";
}

export function bookingProblemMessage(error: unknown, t: TFunction<"booking">): string {
  return t(bookingProblemKey(error));
}

export function useCreateBooking(token: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (submission: BookingFormSubmission) =>
      createBooking(
        {
          target: { relationTo: "booking-instruments", value: submission.target.targetId },
          start: submission.window.start,
          end: submission.window.end,
          purpose: submission.purpose,
          kind: submission.eventKind,
        },
        token,
      ),
    onSuccess: (booking, submission) => {
      refreshIntersectingBookingQueries(queryClient, submission);
      void queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings", booking.id] });
    },
    onError: (error, submission) => {
      if (error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.target.unavailable") {
        void queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      }
      if (
        error instanceof ApiV2ProblemError &&
        (error.code === "errors.api.v2.booking.overlap" ||
          error.code === "errors.api.v2.booking.concurrentModification")
      ) {
        refreshIntersectingBookingQueries(queryClient, submission);
      }
    },
  });
}
