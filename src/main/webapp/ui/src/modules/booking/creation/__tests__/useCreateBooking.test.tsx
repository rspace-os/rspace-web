import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { ApiV2ProblemError } from "@/modules/booking/domain/booking";
import { upcomingBooking } from "@/modules/booking/pages/my-bookings/mocks/bookingMocks";
import { bookingCreationProblemKey, isBookingCreationOutcomeUncertain, useCreateBooking } from "../useCreateBooking";

it("maps a past start rejection separately from a reversed interval", () => {
  expect(bookingCreationProblemKey(new ApiV2ProblemError(400, "errors.api.v2.booking.startInPast", "past"))).toBe(
    "bookings.errors.startInPast",
  );
  expect(bookingCreationProblemKey(new ApiV2ProblemError(400, "errors.api.v2.booking.window", "window"))).toBe(
    "bookings.errors.endAfterStart",
  );
});

it("invalidates every booking view after creation, including the item list and availability index", async () => {
  server.use(http.post("/api/v2/bookings", () => HttpResponse.json(upcomingBooking)));
  const queryClient = new QueryClient();
  const keys = [
    ["api-v2", "bookings", "calendar-events"],
    ["api-v2", "bookings", "calendar-availability"],
    ["api-v2", "bookings", "bookable-item-events", 123],
    ["api-v2", "bookings", "availability-quick-index"],
  ];
  for (const key of keys) queryClient.setQueryData(key, []);
  const { result } = renderHook(() => useCreateBooking("token"), {
    wrapper: ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
  });
  await act(async () => {
    await result.current.mutateAsync({
      target: {
        configurationId: 7,
        targetId: 123,
        globalId: "IN123",
        name: "Microscope",
        timezone: "UTC",
        slotGranularityMinutes: 5,
        openingStart: "00:00",
        openingEnd: "24:00",
        bufferBeforeMinutes: 0,
        bufferAfterMinutes: 0,
        maxBookingDurationMinutes: 0,
        allowDoubleBooking: false,
      },
      window: { start: "2026-09-08T09:00:00Z", end: "2026-09-08T10:00:00Z" },
      purpose: null,
      eventKind: "BOOKING",
      returnDate: "2026-09-08",
    });
  });
  for (const key of keys) expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true);
});

it("treats a lost create response as uncertain and refreshes booking queries", async () => {
  server.use(http.post("/api/v2/bookings", () => HttpResponse.error()));
  const queryClient = new QueryClient();
  const key = ["api-v2", "bookings", "calendar-events"];
  queryClient.setQueryData(key, []);
  const { result } = renderHook(() => useCreateBooking("token"), {
    wrapper: ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
  });

  let error: unknown;
  await act(async () => {
    try {
      await result.current.mutateAsync({
        target: {
          configurationId: 7,
          targetId: 123,
          globalId: "IN123",
          name: "Microscope",
          timezone: "UTC",
          slotGranularityMinutes: 5,
          openingStart: "00:00",
          openingEnd: "24:00",
          bufferBeforeMinutes: 0,
          bufferAfterMinutes: 0,
          maxBookingDurationMinutes: 0,
          allowDoubleBooking: false,
        },
        window: { start: "2026-09-08T09:00:00Z", end: "2026-09-08T10:00:00Z" },
        purpose: null,
        eventKind: "BOOKING",
        returnDate: "2026-09-08",
      });
    } catch (caught) {
      error = caught;
    }
  });

  expect(isBookingCreationOutcomeUncertain(error)).toBe(true);
  expect(bookingCreationProblemKey(error)).toBe("bookings.errors.outcomeUncertain");
  expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true);
});
