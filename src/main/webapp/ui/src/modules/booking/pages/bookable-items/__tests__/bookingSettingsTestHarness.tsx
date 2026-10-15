import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { server } from "@/__tests__/mswServer";
import BookingSettingsPage from "../BookingSettingsPage";

export const bookingSettingsFixture = {
  slotGranularityMinutes: 5,
  incrementUnit: "MINUTES",
  alignToIncrement: true,
  openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "18:00" })),
  bufferBeforeMinutes: 3,
  bufferAfterMinutes: 7,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  availabilityWindowStart: "08:00",
  availabilityWindowEnd: "18:00",
  timezoneMode: "BROWSER" as const,
  customTimezone: null,
  institutionTimezone: "UTC",
  defaultSharedWith: "ALL_USERS" as const,
  selectedAccessGrantees: [],
  configurationVersion: 0,
};

type BookingSettingsFixture = typeof bookingSettingsFixture;

export function createBookingSettingsFixture(overrides: Partial<BookingSettingsFixture> = {}) {
  return { ...bookingSettingsFixture, ...overrides };
}

export function serveBookingSettings(
  initial: Partial<BookingSettingsFixture> = {},
  saveSucceeds: () => boolean = () => true,
) {
  const submitted: Record<string, unknown>[] = [];
  let current = createBookingSettingsFixture(initial);
  server.use(
    http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(current)),
    http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      submitted.push(body);
      if (!saveSucceeds()) return HttpResponse.json({ status: 500 }, { status: 500 });
      current = { ...current, ...body, configurationVersion: current.configurationVersion + 1 };
      return HttpResponse.json(current);
    }),
  );
  return submitted;
}

export function renderBookingSettingsPage(
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) {
  return render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <BookingSettingsPage />
      </Suspense>
    </QueryClientProvider>,
  );
}
