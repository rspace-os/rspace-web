import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { server } from "@/__tests__/mswServer";

import {
  expectNoAlertMessage,
  renderPage,
  useDefaultBookingPreferencesHandlers,
} from "./bookingPreferencesPageTestUtils";

describe("BookingPreferencesPage", () => {
  beforeEach(() => useDefaultBookingPreferencesHandlers());

  it("creates a user-wide calendar subscription from Booking preferences", async () => {
    let creates = 0;
    server.use(
      http.post("/api/v2/users/me/booking-calendar-subscription", ({ request }) => {
        creates += 1;
        expect(request.headers.get("If-Match")).toBeNull();
        return HttpResponse.json(
          {
            active: true,
            updatedAt: "2026-08-30T12:00:00.000Z",
            subscriptionUrl: "https://example.test/public/booking/calendars/feed.ics?token=user",
          },
          { status: 201, headers: { ETag: '"subscription-0"' } },
        );
      }),
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

  it("shows the existing link without an error when Create finds one already made elsewhere", async () => {
    server.use(
      // The page loaded before another tab created the link; the create returns that link unchanged.
      http.post("/api/v2/users/me/booking-calendar-subscription", () =>
        HttpResponse.json(
          {
            active: true,
            updatedAt: "2026-08-30T12:00:00.000Z",
            subscriptionUrl: "https://example.test/public/booking/calendars/feed.ics?token=existing",
          },
          { status: 200, headers: { ETag: '"subscription-0"' } },
        ),
      ),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.create" }));

    expect(await screen.findByLabelText("booking:preferences.calendarSubscription.copyPrompt")).toHaveValue(
      "https://example.test/public/booking/calendars/feed.ics?token=existing",
    );
    expectNoAlertMessage("booking:preferences.calendarSubscription.createError");
  });

  it("replaces the private link only after the user confirms", async () => {
    const rotations: (string | null)[] = [];
    server.use(
      http.get("/api/v2/users/me/booking-calendar-subscription", () =>
        HttpResponse.json(
          {
            active: true,
            updatedAt: "2026-08-30T12:00:00.000Z",
            subscriptionUrl: "https://example.test/public/booking/calendars/feed.ics?token=first",
          },
          { headers: { ETag: '"subscription-0"' } },
        ),
      ),
      http.post("/api/v2/users/me/booking-calendar-subscription/rotate", ({ request }) => {
        rotations.push(request.headers.get("If-Match"));
        return HttpResponse.json(
          {
            active: true,
            updatedAt: "2026-08-30T13:00:00.000Z",
            subscriptionUrl: "https://example.test/public/booking/calendars/feed.ics?token=second",
          },
          { headers: { ETag: '"subscription-1"' } },
        );
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.replace" }));
    await user.click(
      await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.replaceDialog.cancel" }),
    );
    expect(rotations).toEqual([]);
    expect(screen.getByLabelText("booking:preferences.calendarSubscription.copyPrompt")).toHaveValue(
      "https://example.test/public/booking/calendars/feed.ics?token=first",
    );

    await user.click(screen.getByRole("button", { name: "booking:preferences.calendarSubscription.replace" }));
    await user.click(
      await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.replaceDialog.confirm" }),
    );

    await waitFor(() =>
      expect(screen.getByLabelText("booking:preferences.calendarSubscription.copyPrompt")).toHaveValue(
        "https://example.test/public/booking/calendars/feed.ics?token=second",
      ),
    );
    expect(rotations).toEqual(['"subscription-0"']);
  });
});
