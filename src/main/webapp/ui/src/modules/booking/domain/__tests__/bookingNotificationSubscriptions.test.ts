import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { ApiV2ProblemError } from "@/modules/common/api/v2";
import type { ApiV2Problem } from "@/modules/common/api/v2/generated/models/apiV2Problem";
import type { PreferenceReplacement } from "@/modules/common/api/v2/generated/models/preferenceReplacement";
import type { Preferences } from "@/modules/common/api/v2/generated/models/preferences";
import type { Status } from "@/modules/common/api/v2/generated/models/status";
import type { SubscriptionReplacement } from "@/modules/common/api/v2/generated/models/subscriptionReplacement";
import {
  fetchBookingNotificationPreferences,
  fetchBookingNotificationSubscription,
  replaceBookingNotificationPreferences,
  replaceBookingNotificationSubscription,
  unsubscribeFromAllBookingNotificationSubscriptions,
} from "../bookingNotificationSubscriptions";

const subscription = (configurationId: number, enabled = false, version = 0): Status => ({
  configurationId,
  enabled,
  version,
  createdEnabled: true,
  cancelledEnabled: true,
  emailEnabled: false,
});

const preferences: Preferences = {
  autoSubscribeOwnedItems: true,
  notifyOwnActions: true,
  notifyOnCreated: true,
  notifyOnCancelled: true,
  emailDelivery: false,
};

describe("booking notification subscriptions", () => {
  it("round trips defaults and item state, unsubscribes all", async () => {
    let preferencesBody: unknown;
    let itemBody: unknown;
    let deleted = false;
    server.use(
      http.get<never, never, Preferences>("/api/v2/users/me/booking-notification-preferences", () => {
        return HttpResponse.json(preferences);
      }),
      http.put<never, PreferenceReplacement, Preferences>(
        "/api/v2/users/me/booking-notification-preferences",
        async ({ request }) => {
          const replacement: PreferenceReplacement = await request.json();
          preferencesBody = replacement;
          return HttpResponse.json({ ...replacement, emailDelivery: true });
        },
      ),
      http.get<never, never, Status>("/api/v2/booking-configurations/7/notification-subscription", () =>
        HttpResponse.json(subscription(7)),
      ),
      http.put<never, SubscriptionReplacement, Status>(
        "/api/v2/booking-configurations/7/notification-subscription",
        async ({ request }) => {
          itemBody = await request.json();
          return HttpResponse.json(subscription(7, true, 1));
        },
      ),
      http.delete("/api/v2/users/me/booking-notification-subscriptions", () => {
        deleted = true;
        return HttpResponse.json({ updatedCount: 2 });
      }),
    );

    expect(await fetchBookingNotificationPreferences()).toEqual(preferences);
    // The read-only email delivery flag never reaches the strict PUT body.
    const replacement = {
      autoSubscribeOwnedItems: false,
      notifyOwnActions: true,
      notifyOnCreated: true,
      notifyOnCancelled: false,
      emailDelivery: false,
    };
    await replaceBookingNotificationPreferences(replacement);
    expect(preferencesBody).toEqual({
      autoSubscribeOwnedItems: false,
      notifyOwnActions: true,
      notifyOnCreated: true,
      notifyOnCancelled: false,
    });
    expect(await fetchBookingNotificationSubscription(7)).toEqual(subscription(7));
    expect(await replaceBookingNotificationSubscription(7, true, 0)).toEqual(subscription(7, true, 1));
    expect(itemBody).toEqual({ enabled: true, version: 0 });
    expect(await unsubscribeFromAllBookingNotificationSubscriptions()).toBe(2);
    expect(deleted).toBe(true);
  });

  it("preserves a 409 subscription conflict as an API problem", async () => {
    server.use(
      http.put("/api/v2/booking-configurations/7/notification-subscription", () =>
        HttpResponse.json<ApiV2Problem>(
          {
            status: 409,
            title: "Subscription conflict",
            code: "errors.api.v2.bookingNotifications.subscriptionConflict",
          },
          { status: 409 },
        ),
      ),
    );

    await expect(replaceBookingNotificationSubscription(7, true, 0)).rejects.toBeInstanceOf(ApiV2ProblemError);
  });
});
