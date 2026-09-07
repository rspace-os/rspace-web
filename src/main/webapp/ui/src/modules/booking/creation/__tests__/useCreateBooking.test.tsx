import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { upcomingBooking } from "@/modules/booking/pages/my-bookings/mocks/bookingMocks";
import { useCreateBooking } from "../useCreateBooking";

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
