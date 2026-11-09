import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import type { ApiV2Problem } from "@/modules/common/api/v2/generated/models/apiV2Problem";
import type { BookingDisplayPreferences } from "@/modules/common/api/v2/generated/models/bookingDisplayPreferences";
import type { BookingNotificationSubscriptionControllerPreferenceReplacement } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionControllerPreferenceReplacement";
import type { BookingNotificationSubscriptionControllerUnsubscribeResult } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionControllerUnsubscribeResult";
import type { BookingNotificationSubscriptionManagerPreferences } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionManagerPreferences";
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
      http.get<never, never, BookingDisplayPreferences>("/api/v2/users/me/booking-preferences", () =>
        HttpResponse.json(inheritedBrowserBookingPreferences),
      ),
      http.put<
        never,
        BookingNotificationSubscriptionControllerPreferenceReplacement,
        BookingNotificationSubscriptionManagerPreferences | ApiV2Problem
      >("/api/v2/users/me/booking-notification-preferences", async ({ request }) => {
        saved = await request.json();
        return HttpResponse.json(saved as { autoSubscribeOwnedItems: boolean });
      }),
      http.delete<never, never, BookingNotificationSubscriptionControllerUnsubscribeResult | ApiV2Problem>(
        "/api/v2/users/me/booking-notification-subscriptions",
        () => {
          deleted += 1;
          return HttpResponse.json({ updatedCount: 3 });
        },
      ),
    );
    const { container } = renderPage();

    const ownActions = await screen.findByRole("switch", {
      name: "booking:notificationSubscriptions.preferences.ownActions.label",
    });
    expect(ownActions).toBeChecked();
    expect(ownActions).toHaveAccessibleDescription(
      "booking:notificationSubscriptions.preferences.ownActions.description",
    );
    await expectAccessible(container);
    await user.click(ownActions);
    await user.click(screen.getByRole("button", { name: "booking:notificationSubscriptions.preferences.cancel" }));
    expect(ownActions).toBeChecked();
    await user.click(ownActions);
    await user.click(await screen.findByRole("radio", { name: "booking:notificationSubscriptions.options.on" }));
    await user.click(screen.getByRole("button", { name: "booking:notificationSubscriptions.preferences.save" }));
    expect(await screen.findByRole("status")).toHaveTextContent("booking:notificationSubscriptions.preferences.saved");
    expect(saved).toEqual({ notifyOwnActions: false, autoSubscribeOwnedItems: true });

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
