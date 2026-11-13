import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { expect, it } from "vitest";
import { createTestI18n } from "@/__tests__/helpers/createTestI18n";
import { server } from "@/__tests__/mswServer";
import { upcomingBooking } from "@/modules/booking/pages/my-bookings/mocks/bookingMocks";
import { ApiV2ProblemError } from "@/modules/common/api/v2";
import { getCountBookingsQueryKey, getListBookingsQueryKey } from "@/modules/common/api/v2/generated/bookings/bookings";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import type { BookingFormSubmission } from "../BookingForm";
import {
  bookingCreationProblemKey,
  bookingProblemDetails,
  bookingProblemFeedback,
  bookingProblemMessage,
  isBookingConflictError,
  isBookingOutcomeUncertain,
  useCreateBooking,
} from "../useCreateBooking";
import { confocalTargetWith } from "./creationTestFixtures";

const conflict = { id: 59, kind: "BOOKING", start: "2026-09-28T08:00:00Z", end: "2026-09-28T10:00:00Z" } as const;

const bookingSubmission = {
  target: confocalTargetWith({ name: "Microscope", timezone: "UTC" }),
  window: { start: "2026-09-08T09:00:00Z", end: "2026-09-08T10:00:00Z" },
  purpose: null,
  eventKind: "BOOKING",
  returnDate: "2026-09-08",
} satisfies BookingFormSubmission;

/** A booking problem with extension members. */
function problem(status: number, code: string, body: Record<string, unknown> = {}) {
  return new ApiV2ProblemError(status, code, "private detail", {
    ...body,
    status,
    code,
    title: "Booking request failed",
    detail: "private detail",
  });
}

async function englishT() {
  // Only the messages under test come from the English catalog, with real ICU interpolation; others stay as keys.
  const { buffer, bufferAfter, bufferBefore, bufferUnknown, maximumDurationLimit } = bookingEnglish.bookings.errors;
  const errors = { buffer, bufferAfter, bufferBefore, bufferUnknown, maximumDurationLimit };
  return (await createTestI18n({ booking: { bookings: { errors } } }, "booking")).getFixedT("en-US", "booking");
}

it("invalidates every booking view after creation, including the item list and availability index", async () => {
  server.use(http.post("/api/v2/bookings", () => HttpResponse.json(upcomingBooking)));
  const queryClient = new QueryClient();
  const keys = [
    [...getListBookingsQueryKey({ page: 1 }), "calendarAvailability"],
    getListBookingsQueryKey({ page: 2 }),
    getCountBookingsQueryKey(),
  ];
  for (const key of keys) queryClient.setQueryData(key, []);
  const { result } = renderHook(() => useCreateBooking(), {
    wrapper: ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
  });
  await act(async () => {
    await result.current.mutateAsync(bookingSubmission);
  });
  for (const key of keys) expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true);
});

it("treats a lost create response as uncertain and refreshes booking queries", async () => {
  server.use(http.post("/api/v2/bookings", () => HttpResponse.error()));
  const queryClient = new QueryClient();
  const key = getListBookingsQueryKey();
  queryClient.setQueryData(key, []);
  const { result } = renderHook(() => useCreateBooking(), {
    wrapper: ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
  });

  let error: unknown;
  await act(async () => {
    try {
      await result.current.mutateAsync(bookingSubmission);
    } catch (caught) {
      error = caught;
    }
  });

  expect(isBookingOutcomeUncertain(error)).toBe(true);
  expect(bookingCreationProblemKey(error)).toBe("bookings.errors.outcomeUncertain");
  expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true);
});

it.each([
  [null, false],
  [new Error("Lost response"), true],
  [new ApiV2ProblemError(302, undefined, "Unexpected redirect"), true],
  [new ApiV2ProblemError(400, "errors.api.v2.booking.window", "Rejected"), false],
  [new ApiV2ProblemError(503, undefined, "Unavailable"), true],
])("classifies booking write outcome uncertainty for %s", (error, uncertain) => {
  expect(isBookingOutcomeUncertain(error)).toBe(uncertain);
});

it("reads booking problem members and ignores malformed ones", () => {
  expect(
    bookingProblemDetails(
      problem(409, "errors.api.v2.booking.buffer", { conflict, bufferBeforeMinutes: 15, bufferAfterMinutes: 30 }),
    ),
  ).toEqual({ conflict, bufferBeforeMinutes: 15, bufferAfterMinutes: 30 });
  expect(
    bookingProblemDetails(
      problem(409, "errors.api.v2.booking.overlap", { conflict: { id: "59" }, maximumDurationMinutes: -1 }),
    ),
  ).toEqual({});
  expect(bookingProblemDetails(new ApiV2ProblemError(409, "errors.api.v2.booking.overlap", "x"))).toEqual({});
});

it("treats buffer and overlap rejections as blocking conflicts", () => {
  expect(isBookingConflictError(problem(409, "errors.api.v2.booking.buffer"))).toBe(true);
  expect(isBookingConflictError(problem(409, "errors.api.v2.booking.overlap"))).toBe(true);
  expect(isBookingConflictError(problem(400, "errors.api.v2.booking.maximumDuration"))).toBe(false);
});

it("names the buffer instead of reporting an overlap", async () => {
  const t = await englishT();
  const buffer = (body: Record<string, unknown>) =>
    bookingProblemMessage(problem(409, "errors.api.v2.booking.buffer", body), t);

  expect(buffer({ bufferBeforeMinutes: 15, bufferAfterMinutes: 90 })).toBe(
    "Too close to another booking. This item needs 15 minutes before and 1 hour, 30 minutes after each booking.",
  );
  expect(buffer({ bufferBeforeMinutes: 15, bufferAfterMinutes: 0 })).toBe(
    "Too close to another booking. This item needs 15 minutes before each booking.",
  );
  expect(buffer({ bufferBeforeMinutes: 0, bufferAfterMinutes: 1 })).toBe(
    "Too close to another booking. This item needs 1 minute after each booking.",
  );
  expect(buffer({})).toBe("Too close to another booking.");
});

it("spells out the maximum duration from the problem, then from the item", async () => {
  const t = await englishT();
  const withLimit = problem(400, "errors.api.v2.booking.maximumDuration", { maximumDurationMinutes: 120 });
  const withoutLimit = problem(400, "errors.api.v2.booking.maximumDuration");

  expect(bookingProblemMessage(withLimit, t, 30)).toBe(
    "This booking exceeds the bookable item's maximum duration of 2 hours.",
  );
  expect(bookingProblemMessage(withoutLimit, t, 90)).toBe(
    "This booking exceeds the bookable item's maximum duration of 1 hour, 30 minutes.",
  );
  expect(bookingProblemMessage(withoutLimit, t)).toBe("bookings.errors.maximumDuration");
});

it("adds the outcome-uncertain sentence only to a creation", async () => {
  const t = await englishT();

  expect(
    bookingProblemFeedback(problem(409, "errors.api.v2.booking.buffer", { bufferAfterMinutes: 15 }), t, {
      creation: true,
    }),
  ).toEqual({ message: "Too close to another booking. This item needs 15 minutes after each booking." });
  expect(bookingProblemFeedback(new Error("Lost response"), t, { creation: true })).toEqual({
    message: "bookings.errors.outcomeUncertain",
  });
  expect(bookingProblemFeedback(new Error("Lost response"), t, {})).toEqual({ message: "bookings.errors.generic" });
  expect(bookingProblemFeedback(null, t, { creation: true })).toEqual({});
});

it("refreshes bookings after a buffer rejection", async () => {
  server.use(
    http.post("/api/v2/bookings", () =>
      HttpResponse.json(
        { status: 409, title: "Booking conflict", code: "errors.api.v2.booking.buffer" },
        { status: 409 },
      ),
    ),
  );
  const queryClient = new QueryClient();
  const key = [...getListBookingsQueryKey(), "calendarAvailability"];
  queryClient.setQueryData(key, []);
  const { result } = renderHook(() => useCreateBooking(), {
    wrapper: ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
  });

  await act(async () => {
    await result.current
      .mutateAsync({
        ...bookingSubmission,
        target: { ...bookingSubmission.target, bufferBeforeMinutes: 15, bufferAfterMinutes: 15 },
      })
      .catch(() => undefined);
  });

  expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true);
});
