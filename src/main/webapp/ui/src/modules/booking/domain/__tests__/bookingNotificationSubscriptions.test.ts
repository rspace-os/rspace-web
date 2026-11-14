import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { ApiV2ProblemError } from "@/modules/common/api/v2";
import type { ApiV2Problem } from "@/modules/common/api/v2/generated/models/apiV2Problem";
import type { BookingNotificationSubscriptionControllerPreferenceReplacement } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionControllerPreferenceReplacement";
import type { BookingNotificationSubscriptionControllerSubscriptionReplacement } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionControllerSubscriptionReplacement";
import type { BookingNotificationSubscriptionControllerSubscriptionStatus } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionControllerSubscriptionStatus";
import type { BookingNotificationSubscriptionManagerPreferences } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionManagerPreferences";
import {
  fetchBookingNotificationPreferences,
  fetchBookingNotificationSubscription,
  replaceBookingNotificationPreferences,
  replaceBookingNotificationSubscription,
  unsubscribeFromAllBookingNotificationSubscriptions,
} from "../bookingNotificationSubscriptions";

const subscription = (
  configurationId: number,
  enabled = false,
  _version = 0,
): BookingNotificationSubscriptionControllerSubscriptionStatus => ({
  configurationId,
  enabled,
  createdEnabled: true,
  cancelledEnabled: true,
  emailEnabled: false,
});

const preferences: BookingNotificationSubscriptionManagerPreferences = {
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
      http.get<never, never, BookingNotificationSubscriptionManagerPreferences>(
        "/api/v2/users/me/booking-notification-preferences",
        () => {
          return HttpResponse.json(preferences);
        },
      ),
      http.put<
        never,
        BookingNotificationSubscriptionControllerPreferenceReplacement,
        BookingNotificationSubscriptionManagerPreferences
      >("/api/v2/users/me/booking-notification-preferences", async ({ request }) => {
        const replacement: BookingNotificationSubscriptionControllerPreferenceReplacement = await request.json();
        preferencesBody = replacement;
        return HttpResponse.json({ ...replacement, emailDelivery: true });
      }),
      http.get<never, never, BookingNotificationSubscriptionControllerSubscriptionStatus>(
        "/api/v2/booking-configurations/7/notification-subscription",
        () => HttpResponse.json(subscription(7)),
      ),
      http.put<
        never,
        BookingNotificationSubscriptionControllerSubscriptionReplacement,
        BookingNotificationSubscriptionControllerSubscriptionStatus
      >("/api/v2/booking-configurations/7/notification-subscription", async ({ request }) => {
        expect(request.headers.get("If-Match")).toBe('"v0-110"');
        itemBody = await request.json();
        return HttpResponse.json(subscription(7, true, 1));
      }),
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
    expect((await fetchBookingNotificationSubscription(7)).body).toEqual(subscription(7));
    expect((await replaceBookingNotificationSubscription(7, true, '"v0-110"')).body).toEqual(subscription(7, true, 1));
    expect(itemBody).toEqual({ enabled: true });
    expect(await unsubscribeFromAllBookingNotificationSubscriptions()).toBe(2);
    expect(deleted).toBe(true);
  });

  it("preserves a 412 subscription conflict as an API problem", async () => {
    server.use(
      http.put("/api/v2/booking-configurations/7/notification-subscription", () =>
        HttpResponse.json<ApiV2Problem>(
          {
            status: 412,
            title: "Subscription conflict",
            code: "errors.api.v2.bookingNotifications.subscriptionConflict",
          },
          { status: 412 },
        ),
      ),
    );

    await expect(replaceBookingNotificationSubscription(7, true, '"v0-110"')).rejects.toBeInstanceOf(ApiV2ProblemError);
  });
});
