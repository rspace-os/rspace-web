import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { TFunction } from "i18next";
import type { BookingMutation } from "@/modules/booking/domain/booking";
import { createBooking } from "@/modules/booking/domain/booking";
import { invalidateBookingListOperation } from "@/modules/booking/domain/bookingInvalidation";
import { ApiV2ProblemError } from "@/modules/common/api/v2";
import type { ErrorType } from "@/modules/common/api/v2/apiV2Fetch";
import {
  invalidateCountBookingConfigurations,
  invalidateGetBookingConfiguration,
  invalidateGetBookingConfigurationAccess,
  invalidateListBookingConfigurationAuditEvents,
  invalidateListBookingConfigurations,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import {
  getCreateBookingMutationOptions,
  invalidateCountBookings,
  invalidateListBookings,
} from "@/modules/common/api/v2/generated/bookings/bookings";
import type { BookingFormSubmission } from "./BookingForm";

const BOOKING_PROBLEM_KEYS = {
  "errors.api.v2.booking.startInPast": "bookings.errors.startInPast",
  "errors.api.v2.booking.window": "bookings.errors.window",
  "errors.api.v2.booking.duration": "bookings.errors.duration",
  "errors.api.v2.booking.maximumDuration": "bookings.errors.maximumDuration",
  "errors.api.v2.booking.overlap": "bookings.errors.overlap",
  "errors.api.v2.booking.granularity": "bookings.errors.granularity",
  "errors.api.v2.booking.openingHours": "bookings.errors.openingHours",
  "errors.api.v2.booking.target.unavailable": "bookings.errors.targetUnavailable",
  "errors.api.v2.forbidden": "bookings.errors.forbidden",
  "errors.api.v2.booking.state.transition": "bookings.errors.noLongerEditable",
} as const;

type MappedBookingProblemKey = (typeof BOOKING_PROBLEM_KEYS)[keyof typeof BOOKING_PROBLEM_KEYS];
type BookingProblemKey = MappedBookingProblemKey | "bookings.errors.generic" | "bookings.errors.concurrentModification";

type BookingProblemCode = keyof typeof BOOKING_PROBLEM_KEYS;

function isMappedBookingProblemCode(code: string | undefined): code is BookingProblemCode {
  return code !== undefined && Object.hasOwn(BOOKING_PROBLEM_KEYS, code);
}

export function bookingProblemKey(error: unknown): BookingProblemKey {
  if (!(error instanceof ApiV2ProblemError)) return "bookings.errors.generic";
  if (error.status === 412 || error.code === "errors.api.v2.booking.concurrentModification") {
    return "bookings.errors.concurrentModification";
  }
  return isMappedBookingProblemCode(error.code) ? BOOKING_PROBLEM_KEYS[error.code] : "bookings.errors.generic";
}

export function bookingProblemMessage(error: unknown, t: TFunction<"booking">): string {
  return t(bookingProblemKey(error));
}

export function bookingCreationProblemKey(error: unknown): BookingProblemKey | "bookings.errors.outcomeUncertain" {
  return isBookingOutcomeUncertain(error) ? "bookings.errors.outcomeUncertain" : bookingProblemKey(error);
}

/** A booking write may have committed even when its response was not received. */
export function isBookingOutcomeUncertain(error: unknown): boolean {
  if (error == null) return false;
  return !(error instanceof ApiV2ProblemError) || error.status < 400 || error.status >= 500;
}

export function useCreateBooking() {
  const queryClient = useQueryClient();
  const generated = getCreateBookingMutationOptions(queryClient);
  return useMutation<BookingMutation, ErrorType<unknown>, BookingFormSubmission>({
    retry: false,
    mutationFn: (submission: BookingFormSubmission) =>
      createBooking({
        target: { relationTo: "instruments", value: submission.target.targetId },
        start: submission.window.start,
        end: submission.window.end,
        purpose: submission.purpose,
        kind: submission.eventKind,
      }),
    onSuccess: (data, submission, result, context) =>
      generated.onSuccess?.(
        data,
        {
          data: {
            target: { relationTo: "instruments", value: submission.target.targetId },
            start: submission.window.start,
            end: submission.window.end,
            purpose: submission.purpose,
            kind: submission.eventKind,
          },
        },
        result,
        context,
      ),
    onError: (error, submission) => {
      const invalidateBookings =
        isBookingOutcomeUncertain(error) ||
        (error instanceof ApiV2ProblemError &&
          (error.code === "errors.api.v2.booking.overlap" ||
            error.code === "errors.api.v2.booking.concurrentModification"));
      if (invalidateBookings)
        void Promise.all([
          invalidateListBookings(queryClient),
          invalidateCountBookings(queryClient),
          invalidateBookingListOperation(queryClient, "catalogue"),
          invalidateBookingListOperation(queryClient, "locations"),
          invalidateBookingListOperation(queryClient, "calendarCatalogue"),
          invalidateBookingListOperation(queryClient, "calendarEvents"),
        ]);
      if (error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.target.unavailable") {
        void Promise.all([
          invalidateListBookingConfigurations(queryClient),
          invalidateCountBookingConfigurations(queryClient),
          invalidateGetBookingConfiguration(queryClient, submission.target.configurationId),
          invalidateGetBookingConfigurationAccess(queryClient, submission.target.configurationId),
          invalidateListBookingConfigurationAuditEvents(queryClient, submission.target.configurationId),
          invalidateBookingListOperation(queryClient, "catalogue"),
          invalidateBookingListOperation(queryClient, "calendarCatalogue"),
        ]);
      }
    },
  });
}
