import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import * as v from "valibot";
import { expect } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { ApiV2Problem } from "@/modules/common/api/v2/generated/models/apiV2Problem";
import type { BookingSettingsControllerAdminSettingsDocument } from "@/modules/common/api/v2/generated/models/bookingSettingsControllerAdminSettingsDocument";
import type { BookingSettingsControllerSettingsPatch } from "@/modules/common/api/v2/generated/models/bookingSettingsControllerSettingsPatch";
import { BookingSettingsControllerAdminSettingsDocumentSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import BookingSettingsPage from "../BookingSettingsPage";

export const bookingSettingsFixture = {
  slotGranularityMinutes: 5,
  incrementUnit: "MINUTES" as const,
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
  configurationVersion: 0,
};

export function createBookingSettingsFixture(overrides: Partial<BookingSettingsControllerAdminSettingsDocument> = {}) {
  return { ...bookingSettingsFixture, ...overrides };
}

export function serveBookingSettings(
  initial: Partial<BookingSettingsControllerAdminSettingsDocument> = {},
  saveSucceeds: () => boolean = () => true,
) {
  const submitted: BookingSettingsControllerSettingsPatch[] = [];
  let current: BookingSettingsControllerAdminSettingsDocument = createBookingSettingsFixture(initial);
  server.use(
    http.get("/api/v2/booking-settings/admin", () =>
      HttpResponse.json<BookingSettingsControllerAdminSettingsDocument>(current, {
        headers: { ETag: `"${current.configurationVersion}"` },
      }),
    ),
    http.patch<never, BookingSettingsControllerSettingsPatch>("/api/v2/booking-settings/admin", async ({ request }) => {
      expect(request.headers.get("If-Match")).toBe(`"${current.configurationVersion}"`);
      const body = await request.json();
      expect(body).not.toHaveProperty("configurationVersion");
      submitted.push(body);
      if (!saveSucceeds())
        return HttpResponse.json<ApiV2Problem>(
          { title: "Request failed", code: "errors.api.v2.unexpected", status: 500 },
          { status: 500 },
        );
      current = v.parse(BookingSettingsControllerAdminSettingsDocumentSchema, {
        ...current,
        ...body,
        configurationVersion: (current.configurationVersion ?? 0) + 1,
      });
      return HttpResponse.json<BookingSettingsControllerAdminSettingsDocument>(current, {
        headers: { ETag: `"${current.configurationVersion}"` },
      });
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
