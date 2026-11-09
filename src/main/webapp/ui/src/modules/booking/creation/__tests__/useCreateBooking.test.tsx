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
  bookingProblemConflict,
  bookingProblemDetails,
  bookingProblemFeedback,
  bookingProblemMessage,
  isBookingConflictError,
  isBookingOutcomeUncertain,
  useCreateBooking,
  withBookingProblemConflict,
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

it("maps a past start rejection separately from a reversed interval", () => {
  expect(bookingCreationProblemKey(new ApiV2ProblemError(400, "errors.api.v2.booking.startInPast", "past"))).toBe(
    "bookings.errors.startInPast",
  );
  expect(bookingCreationProblemKey(new ApiV2ProblemError(400, "errors.api.v2.booking.window", "window"))).toBe(
    "bookings.errors.window",
  );
});

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

it("shapes a server-named conflict like a client-side one, without purpose or requester", () => {
  expect(
    bookingProblemConflict(problem(409, "errors.api.v2.booking.overlap", { conflict }), "UTC", "Asia/Tokyo"),
  ).toEqual({
    ...conflict,
    privacy: "full",
    purpose: null,
    bookedBy: null,
    timezone: "UTC",
    instrumentTimeZone: "Asia/Tokyo",
    bufferOnly: false,
  });
  expect(bookingProblemConflict(problem(409, "errors.api.v2.booking.buffer", { conflict }), "UTC")?.bufferOnly).toBe(
    true,
  );
  expect(
    bookingProblemConflict(problem(400, "errors.api.v2.booking.granularity", { conflict }), "UTC"),
  ).toBeUndefined();
});

it("adds a server-named conflict only when the client list does not already show it", () => {
  const serverConflict = bookingProblemConflict(problem(409, "errors.api.v2.booking.overlap", { conflict }), "UTC");
  if (!serverConflict) throw new Error("Expected the server-named conflict");
  const clientConflict = { ...serverConflict, purpose: "Cell imaging" };
  expect(withBookingProblemConflict([], serverConflict)).toEqual([serverConflict]);
  expect(withBookingProblemConflict([clientConflict], serverConflict)).toEqual([clientConflict]);
  expect(withBookingProblemConflict([clientConflict], undefined)).toEqual([clientConflict]);
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

it("lets a listed overlap speak for itself but keeps the buffer sentence", async () => {
  const t = await englishT();
  const context = { displayTimezone: "UTC", creation: true };

  expect(bookingProblemFeedback(problem(409, "errors.api.v2.booking.overlap", { conflict }), t, context)).toEqual({
    conflict: expect.objectContaining({ id: 59, bufferOnly: false }),
    message: undefined,
  });
  expect(
    bookingProblemFeedback(
      problem(409, "errors.api.v2.booking.buffer", { conflict, bufferAfterMinutes: 15 }),
      t,
      context,
    ),
  ).toEqual({
    conflict: expect.objectContaining({ id: 59, bufferOnly: true }),
    message: "Too close to another booking. This item needs 15 minutes after each booking.",
  });
  expect(bookingProblemFeedback(problem(409, "errors.api.v2.booking.overlap"), t, context)).toEqual({
    conflict: undefined,
    message: "bookings.errors.overlap",
  });
  expect(bookingProblemFeedback(null, t, context)).toEqual({});
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

it("reuses the create key after a lost response and starts a new intent after success", async () => {
  const keys: (string | null)[] = [];
  server.use(
    http.post("/api/v2/bookings", ({ request }) => {
      keys.push(request.headers.get("Idempotency-Key"));
      return keys.length === 1 ? HttpResponse.error() : HttpResponse.json(upcomingBooking);
    }),
  );
  const queryClient = new QueryClient();
  const { result } = renderHook(() => useCreateBooking(), {
    wrapper: ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
  });
  await act(async () => {
    await result.current.mutateAsync(bookingSubmission).catch(() => undefined);
    await result.current.mutateAsync({ ...bookingSubmission });
    await result.current.mutateAsync(bookingSubmission);
  });
  expect(keys[0]).toBeTruthy();
  expect(keys[1]).toBe(keys[0]);
  expect(keys[2]).not.toBe(keys[0]);
});
