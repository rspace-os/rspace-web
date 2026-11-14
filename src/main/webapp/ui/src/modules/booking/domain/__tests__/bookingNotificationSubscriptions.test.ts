import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { ApiV2ProblemError } from "@/modules/common/api/v2";
import type { BookingNotificationSubscriptionControllerPreferenceReplacement } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionControllerPreferenceReplacement";
import type { BookingNotificationSubscriptionManagerPreferences } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionManagerPreferences";
import {
  fetchBookingNotificationPreferences,
  fetchBookingNotificationSubscription,
  replaceBookingNotificationPreferences,
  replaceBookingNotificationSubscription,
  unsubscribeFromAllBookingNotificationSubscriptions,
} from "../bookingNotificationSubscriptions";

const subscription = (configurationId: number, enabled = false, _version = 0) => ({
  configurationId,
  enabled,
  createdEnabled: true,
  cancelledEnabled: true,
  emailEnabled: false,
});

describe("booking notification subscriptions", () => {
  it("round trips defaults and item state and unsubscribes all", async () => {
    let preferencesBody: unknown;
    let itemBody: unknown;
    let deleted = false;
    server.use(
      http.get("/api/v2/users/me/booking-notification-preferences", () => {
        return HttpResponse.json({ notifyOwnActions: true, autoSubscribeOwnedItems: true });
      }),
      http.put<
        never,
        BookingNotificationSubscriptionControllerPreferenceReplacement,
        BookingNotificationSubscriptionManagerPreferences
      >("/api/v2/users/me/booking-notification-preferences", async ({ request }) => {
        const replacement = await request.json();
        preferencesBody = replacement;
        return HttpResponse.json(replacement);
      }),
      http.get("/api/v2/booking-configurations/7/notification-subscription", () => HttpResponse.json(subscription(7))),
      http.put("/api/v2/booking-configurations/7/notification-subscription", async ({ request }) => {
        expect(request.headers.get("If-Match")).toBe('"v0-110"');
        itemBody = await request.json();
        return HttpResponse.json(subscription(7, true, 1));
      }),
      http.delete("/api/v2/users/me/booking-notification-subscriptions", () => {
        deleted = true;
        return HttpResponse.json({ updatedCount: 2 });
      }),
    );

    expect(await fetchBookingNotificationPreferences()).toEqual({
      notifyOwnActions: true,
      autoSubscribeOwnedItems: true,
    });
    await replaceBookingNotificationPreferences({ notifyOwnActions: true, autoSubscribeOwnedItems: false });
    expect(preferencesBody).toEqual({ notifyOwnActions: true, autoSubscribeOwnedItems: false });
    expect((await fetchBookingNotificationSubscription(7)).body).toEqual(subscription(7));
    expect((await replaceBookingNotificationSubscription(7, true, '"v0-110"')).body).toEqual(subscription(7, true, 1));
    expect(itemBody).toEqual({ enabled: true });
    expect(await unsubscribeFromAllBookingNotificationSubscriptions()).toBe(2);
    expect(deleted).toBe(true);
  });

  it("preserves a 412 subscription conflict as an API problem", async () => {
    server.use(
      http.put("/api/v2/booking-configurations/7/notification-subscription", () =>
        HttpResponse.json(
          {
            title: "Booking request failed",
            status: 412,
            code: "errors.api.v2.bookingNotifications.subscriptionConflict",
          },
          { status: 412 },
        ),
      ),
    );

    await expect(replaceBookingNotificationSubscription(7, true, '"v0-110"')).rejects.toBeInstanceOf(ApiV2ProblemError);
  });
});
