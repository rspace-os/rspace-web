import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import type { ApiV2Problem } from "@/modules/common/api/v2/generated/models/apiV2Problem";
import type { BookingCalendarSubscriptionCreated } from "@/modules/common/api/v2/generated/models/bookingCalendarSubscriptionCreated";
import type { BookingDisplayPreferences } from "@/modules/common/api/v2/generated/models/bookingDisplayPreferences";
import { inheritedBrowserBookingPreferences } from "../bookingPreferencesFixtures";
import { renderPage, useDefaultBookingPreferencesHandlers } from "./bookingPreferencesPageTestUtils";

describe("BookingPreferencesPage", () => {
  beforeEach(() => useDefaultBookingPreferencesHandlers());

  it("creates a user-wide calendar subscription from Booking preferences", async () => {
    let creates = 0;
    server.use(
      oauthTokenHandler(true),
      http.get<never, never, BookingDisplayPreferences>("/api/v2/users/me/booking-preferences", () =>
        HttpResponse.json(inheritedBrowserBookingPreferences),
      ),
      http.post<never, never, BookingCalendarSubscriptionCreated | ApiV2Problem>(
        "/api/v2/users/me/booking-calendar-subscription",
        ({ request }) => {
          creates += 1;
          expect(request.headers.get("If-Match")).toBe('"inactive"');
          return HttpResponse.json(
            {
              active: true,
              updatedAt: "2026-08-30T12:00:00.000Z",
              subscriptionUrl: "https://example.test/public/booking/calendars/feed.ics?token=user",
            },
            { headers: { ETag: '"subscription-0"' } },
          );
        },
      ),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.create" }));

    expect(creates).toBe(1);
    expect(screen.getByLabelText("booking:preferences.calendarSubscription.copyPrompt")).toHaveValue(
      "https://example.test/public/booking/calendars/feed.ics?token=user",
    );
    expect(screen.getByRole("link", { name: "booking:preferences.calendarSubscription.google" })).toBeVisible();
  });
});
