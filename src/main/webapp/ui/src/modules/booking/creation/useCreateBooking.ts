import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { TFunction } from "i18next";
import * as v from "valibot";
import type { BookingMutation } from "@/modules/booking/domain/booking";
import { createBooking } from "@/modules/booking/domain/booking";
import { invalidateBookingListOperation } from "@/modules/booking/domain/bookingInvalidation";
import { MAX_BOOKING_DURATION_MINUTES } from "@/modules/booking/domain/bookingOpeningHours";
import { formatDurationMinutes } from "@/modules/booking/domain/bookingTime";
import { ApiV2ProblemError } from "@/modules/common/api/v2";
import type { ErrorType } from "@/modules/common/api/v2/apiV2Fetch";
import { invalidateListBookingConfigurations } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import {
  getCreateBookingMutationOptions,
  invalidateCountBookings,
  invalidateListBookings,
} from "@/modules/common/api/v2/generated/bookings/bookings";
import { BookingsReadKind } from "@/modules/common/api/v2/generated/models/bookingsReadKind";
import type { BookingFormSubmission } from "./BookingForm";
import type { BookableItemOption } from "./bookableItemOption";

const OVERLAP_CODE = "errors.api.v2.booking.overlap";
const BUFFER_CODE = "errors.api.v2.booking.buffer";
const MAXIMUM_DURATION_CODE = "errors.api.v2.booking.maximumDuration";

const ProblemMinutesSchema = v.fallback(v.optional(v.pipe(v.number(), v.integer(), v.minValue(0))), undefined);
const BookingProblemDetailsSchema = v.object({
  conflict: v.fallback(
    v.optional(v.object({ id: v.number(), kind: v.enum(BookingsReadKind), start: v.string(), end: v.string() })),
    undefined,
  ),
  bufferBeforeMinutes: ProblemMinutesSchema,
  bufferAfterMinutes: ProblemMinutesSchema,
  maximumDurationMinutes: ProblemMinutesSchema,
});

/** Booking-specific problem members, read from the parsed response body on `ApiV2ProblemError.problem`. */
export function bookingProblemDetails(error: unknown): v.InferOutput<typeof BookingProblemDetailsSchema> {
  const body = error instanceof ApiV2ProblemError ? error.problem : undefined;
  const result = v.safeParse(BookingProblemDetailsSchema, body ?? {});
  return result.success ? result.output : {};
}

/** The server rejected the window because it overlaps, or is within the buffer of, another event. */
export function isBookingConflictError(error: unknown): boolean {
  return error instanceof ApiV2ProblemError && (error.code === OVERLAP_CODE || error.code === BUFFER_CODE);
}

const BOOKING_PROBLEM_KEYS = {
  "errors.api.v2.booking.window": "bookings.errors.window",
  "errors.api.v2.booking.duration": "bookings.errors.duration",
  [MAXIMUM_DURATION_CODE]: "bookings.errors.maximumDuration",
  "errors.api.v2.booking.overlap": "bookings.errors.overlap",
  [BUFFER_CODE]: "bookings.errors.overlap",
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

export function bookingProblemMessage(
  error: unknown,
  t: TFunction<"booking">,
  maxBookingDurationMinutes?: number,
): string {
  if (error instanceof ApiV2ProblemError && error.code === BUFFER_CODE) {
    const { bufferBeforeMinutes = 0, bufferAfterMinutes = 0 } = bookingProblemDetails(error);
    const before = formatDurationMinutes(bufferBeforeMinutes);
    const after = formatDurationMinutes(bufferAfterMinutes);
    if (bufferBeforeMinutes > 0 && bufferAfterMinutes > 0) {
      return t("bookings.errors.buffer", {
        before,
        after,
      });
    }
    if (bufferBeforeMinutes > 0) {
      return t("bookings.errors.bufferBefore", {
        before,
      });
    }
    if (bufferAfterMinutes > 0) {
      return t("bookings.errors.bufferAfter", {
        after,
      });
    }
    return t("bookings.errors.bufferUnknown");
  }
  if (error instanceof ApiV2ProblemError && error.code === MAXIMUM_DURATION_CODE) {
    const limit = bookingProblemDetails(error).maximumDurationMinutes ?? maxBookingDurationMinutes;
    if (limit) return maximumDurationMessage(limit, t);
  }
  return t(bookingProblemKey(error));
}

export function bookingCreationProblemKey(error: unknown): BookingProblemKey | "bookings.errors.outcomeUncertain" {
  return isBookingOutcomeUncertain(error) ? "bookings.errors.outcomeUncertain" : bookingProblemKey(error);
}

/** The maximum-duration rejection with the limit spelled out, shared with the client-side check. */
export function maximumDurationMessage(maxBookingDurationMinutes: number, t: TFunction<"booking">): string {
  // The system limit applies to every item, so it is not described as the item's own maximum.
  if (maxBookingDurationMinutes >= MAX_BOOKING_DURATION_MINUTES) return t("bookings.errors.duration");
  return t("bookings.errors.maximumDurationLimit", {
    duration: formatDurationMinutes(maxBookingDurationMinutes),
  });
}

/** What a form shows for a failed save: a sentence. */
export function bookingProblemFeedback(
  error: unknown,
  t: TFunction<"booking">,
  {
    target,
    creation = false,
  }: {
    target?: Pick<BookableItemOption, "maxBookingDurationMinutes">;
    creation?: boolean;
  },
): { message?: string } {
  if (!error) return {};
  const message =
    creation && isBookingOutcomeUncertain(error)
      ? t("bookings.errors.outcomeUncertain")
      : bookingProblemMessage(error, t, target?.maxBookingDurationMinutes);
  return { message };
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
    onError: (error) => {
      if (
        isBookingOutcomeUncertain(error) ||
        isBookingConflictError(error) ||
        (error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.concurrentModification")
      ) {
        void Promise.all([
          invalidateListBookings(queryClient),
          invalidateCountBookings(queryClient),
          invalidateBookingListOperation(queryClient, "catalogue"),
          invalidateBookingListOperation(queryClient, "locations"),
        ]);
      }
      if (error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.target.unavailable") {
        void Promise.all([
          invalidateListBookingConfigurations(queryClient),
          invalidateBookingListOperation(queryClient, "catalogue"),
        ]);
      }
    },
  });
}
