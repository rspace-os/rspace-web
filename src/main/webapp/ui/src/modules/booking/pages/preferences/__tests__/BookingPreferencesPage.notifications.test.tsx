import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { inheritedBrowserBookingPreferences } from "../bookingPreferencesFixtures";
import { renderPage, useDefaultBookingPreferencesHandlers } from "./bookingPreferencesPageTestUtils";

describe("BookingPreferencesPage", () => {
  beforeEach(() => useDefaultBookingPreferencesHandlers());

  it("saves the owner auto-subscribe default and unsubscribes from existing instruments", async () => {
    const user = userEvent.setup();
    let saved: unknown;
    let deleted = 0;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(inheritedBrowserBookingPreferences)),
      http.put("/api/v2/users/me/booking-notification-preferences", async ({ request }) => {
        saved = await request.json();
        return HttpResponse.json(saved as { autoSubscribeOwnedItems: boolean });
      }),
      http.delete("/api/v2/users/me/booking-notification-subscriptions", () => {
        deleted += 1;
        return HttpResponse.json({ updatedCount: 3 });
      }),
    );
    renderPage();

    await user.click(await screen.findByRole("radio", { name: "booking:notificationSubscriptions.options.on" }));
    await user.click(screen.getByRole("button", { name: "booking:notificationSubscriptions.preferences.save" }));
    expect(await screen.findByRole("status")).toHaveTextContent("booking:notificationSubscriptions.preferences.saved");
    expect(saved).toEqual({ autoSubscribeOwnedItems: true });

    await user.click(
      screen.getByRole("button", { name: "booking:notificationSubscriptions.preferences.unsubscribeAll" }),
    );
    expect(await screen.findByText("booking:notificationSubscriptions.preferences.unsubscribed")).toBeVisible();
    expect(deleted).toBe(1);
    expect(screen.getByRole("radio", { name: "booking:notificationSubscriptions.options.on" })).toBeChecked();
    expect(
      screen.queryByRole("link", { name: "booking:notificationSubscriptions.preferences.manageSubscriptions" }),
    ).not.toBeInTheDocument();
  });
});
