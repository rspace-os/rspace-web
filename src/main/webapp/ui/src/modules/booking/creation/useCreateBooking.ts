import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { TFunction } from "i18next";
import * as v from "valibot";
import type { BookingConflict } from "@/modules/booking/domain/availability";
import { ApiV2ProblemError, BookingEventKindSchema, createBooking } from "@/modules/booking/domain/booking";
import { MAX_BOOKING_DURATION_MINUTES } from "@/modules/booking/domain/bookingOpeningHours";
import { formatDurationMinutes } from "@/modules/booking/domain/bookingTime";
import type { BookingFormSubmission } from "./BookingForm";
import type { BookableItemOption } from "./bookableItemOption";

const OVERLAP_CODE = "errors.api.v2.booking.overlap";
const BUFFER_CODE = "errors.api.v2.booking.buffer";
const MAXIMUM_DURATION_CODE = "errors.api.v2.booking.maximumDuration";

const ProblemMinutesSchema = v.fallback(v.optional(v.pipe(v.number(), v.integer(), v.minValue(0))), undefined);
const BookingProblemDetailsSchema = v.object({
  conflict: v.fallback(
    v.optional(v.object({ id: v.number(), kind: BookingEventKindSchema, start: v.string(), end: v.string() })),
    undefined,
  ),
  bufferBeforeMinutes: ProblemMinutesSchema,
  bufferAfterMinutes: ProblemMinutesSchema,
  maximumDurationMinutes: ProblemMinutesSchema,
});

/** Booking-specific problem members, read from the parsed response body on `ApiV2ProblemError.problem`. */
export function bookingProblemDetails(error: unknown): v.InferOutput<typeof BookingProblemDetailsSchema> {
  const body = error instanceof ApiV2ProblemError && "problem" in error ? error.problem : undefined;
  const result = v.safeParse(BookingProblemDetailsSchema, body ?? {});
  return result.success ? result.output : {};
}

/** The server rejected the window because it overlaps, or is within the buffer of, another event. */
export function isBookingConflictError(error: unknown): boolean {
  return error instanceof ApiV2ProblemError && (error.code === OVERLAP_CODE || error.code === BUFFER_CODE);
}

/** The event a conflict rejection names, shaped like a client-side conflict so the form can list it. */
export function bookingProblemConflict(
  error: unknown,
  displayTimezone: string,
  instrumentTimeZone?: string | null,
): BookingConflict | undefined {
  if (!(error instanceof ApiV2ProblemError) || !isBookingConflictError(error)) return undefined;
  const { conflict } = bookingProblemDetails(error);
  if (!conflict) return undefined;
  return {
    ...conflict,
    // The problem never includes purpose or requester, so the list labels it "Booking #id".
    privacy: "full",
    purpose: null,
    bookedBy: null,
    timezone: displayTimezone,
    instrumentTimeZone: instrumentTimeZone ?? null,
    bufferOnly: error.code === BUFFER_CODE,
  };
}

/** Add a server-named conflict unless the client-side list already shows that event. */
export function withBookingProblemConflict(
  conflicts: readonly BookingConflict[],
  conflict: BookingConflict | undefined,
): readonly BookingConflict[] {
  return conflict && !conflicts.some(({ id }) => id === conflict.id) ? [...conflicts, conflict] : conflicts;
}

export function bookingProblemKey(
  error: unknown,
):
  | "bookings.errors.generic"
  | "bookings.errors.endAfterStart"
  | "bookings.errors.startInPast"
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
  if (error.code === "errors.api.v2.booking.startInPast") return "bookings.errors.startInPast";
  if (error.code === "errors.api.v2.booking.duration") return "bookings.errors.duration";
  if (error.code === MAXIMUM_DURATION_CODE) return "bookings.errors.maximumDuration";
  // Key-only callers get the nearest overlap text; bookingProblemMessage describes the buffer precisely.
  if (isBookingConflictError(error)) return "bookings.errors.overlap";
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

export function bookingCreationProblemKey(
  error: unknown,
): ReturnType<typeof bookingProblemKey> | "bookings.errors.outcomeUncertain" {
  return isBookingCreationOutcomeUncertain(error) ? "bookings.errors.outcomeUncertain" : bookingProblemKey(error);
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

/** The maximum-duration rejection with the limit spelled out, shared with the client-side check. */
export function maximumDurationMessage(maxBookingDurationMinutes: number, t: TFunction<"booking">): string {
  // The system limit applies to every item, so it is not described as the item's own maximum.
  if (maxBookingDurationMinutes >= MAX_BOOKING_DURATION_MINUTES) return t("bookings.errors.duration");
  return t("bookings.errors.maximumDurationLimit", {
    duration: formatDurationMinutes(maxBookingDurationMinutes),
  });
}

export function bookingCreationProblemMessage(
  error: unknown,
  t: TFunction<"booking">,
  maxBookingDurationMinutes?: number,
): string {
  return isBookingCreationOutcomeUncertain(error)
    ? t("bookings.errors.outcomeUncertain")
    : bookingProblemMessage(error, t, maxBookingDurationMinutes);
}

/** What a form shows for a failed save: a sentence, and the server-named conflict for the conflict list. */
export function bookingProblemFeedback(
  error: unknown,
  t: TFunction<"booking">,
  {
    displayTimezone,
    target,
    creation = false,
  }: {
    displayTimezone: string;
    target?: Pick<BookableItemOption, "timezone" | "maxBookingDurationMinutes">;
    creation?: boolean;
  },
): { message?: string; conflict?: BookingConflict } {
  if (!error) return {};
  const conflict = bookingProblemConflict(error, displayTimezone, target?.timezone);
  const message = creation
    ? bookingCreationProblemMessage(error, t, target?.maxBookingDurationMinutes)
    : bookingProblemMessage(error, t, target?.maxBookingDurationMinutes);
  // A listed overlap explains itself; a buffer conflict still needs the sentence naming the buffer.
  return { conflict, message: conflict && !conflict.bufferOnly ? undefined : message };
}

/** A create request may have committed even when its response was not received. */
export function isBookingCreationOutcomeUncertain(error: unknown): boolean {
  if (error == null) return false;
  return !(error instanceof ApiV2ProblemError) || error.status >= 500;
}

export function useCreateBooking(token: string) {
  const queryClient = useQueryClient();
  return useMutation({
    retry: false,
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
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
    },
    onError: (error) => {
      if (isBookingCreationOutcomeUncertain(error)) {
        void queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
      }
      if (error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.target.unavailable") {
        void queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      }
      if (
        isBookingConflictError(error) ||
        (error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.concurrentModification")
      ) {
        void queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
      }
    },
  });
}
