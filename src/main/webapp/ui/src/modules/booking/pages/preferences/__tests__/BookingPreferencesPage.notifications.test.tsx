import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
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
        return HttpResponse.json({ ...(saved as object), emailDelivery: false });
      }),
      http.delete("/api/v2/users/me/booking-notification-subscriptions", () => {
        deleted += 1;
        return HttpResponse.json({ updatedCount: 3 });
      }),
    );
    const { container } = renderPage();
    const ownActions = await screen.findByRole("switch", { name: "booking:notificationSubscriptions.preferences.ownActions.label" });
    expect(ownActions).toBeChecked();
    await expectAccessible(container);
    await user.click(ownActions);
    await user.click(screen.getByRole("button", { name: "booking:notificationSubscriptions.preferences.cancel" }));
    expect(ownActions).toBeChecked();
    await user.click(ownActions);
    await user.click(await screen.findByRole("radio", { name: "booking:notificationSubscriptions.options.on" }));
    await user.click(screen.getByRole("button", { name: "booking:notificationSubscriptions.preferences.save" }));
    const savedButton = await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
    expect(savedButton).toHaveClass("bg-emerald-600");
    expect(savedButton).toBeDisabled();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(saved).toEqual({ notifyOwnActions: false, autoSubscribeOwnedItems: true, notifyOnCreated: true, notifyOnCancelled: true });

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
  it("saves the booking event toggles with the complete preference body and shows email delivery read-only", async () => {
    const user = userEvent.setup();
    let saved: unknown;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(inheritedBrowserBookingPreferences)),
      http.put("/api/v2/users/me/booking-notification-preferences", async ({ request }) => {
        saved = await request.json();
        return HttpResponse.json({ ...(saved as object), emailDelivery: false });
      }),
    );
    const { container } = renderPage();

    const created = await screen.findByRole("checkbox", {
      name: "booking:notificationSubscriptions.preferences.events.created",
    });
    const cancelled = screen.getByRole("checkbox", {
      name: "booking:notificationSubscriptions.preferences.events.cancelled",
    });
    expect(created).toBeChecked();
    expect(cancelled).toBeChecked();
    expect(screen.getByText(/booking:notificationSubscriptions\.preferences\.emailDelivery\.off/)).toBeVisible();
    expect(
      screen.getByRole("link", { name: "booking:notificationSubscriptions.preferences.emailDelivery.change" }),
    ).toHaveAttribute("href", "/userform#prefContainer");
    expect(screen.queryByRole("checkbox", { name: /emailDelivery/ })).not.toBeInTheDocument();

    await user.click(cancelled);
    expect(cancelled).not.toBeChecked();
    await user.click(screen.getByRole("button", { name: "booking:notificationSubscriptions.preferences.save" }));

    expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeDisabled();
    expect(saved).toEqual({ autoSubscribeOwnedItems: false, notifyOnCreated: true, notifyOnCancelled: false });
    expect(cancelled).not.toBeChecked();
    await expectAccessible(container);
  });
});
