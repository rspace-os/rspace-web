import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { ApiV2ProblemError } from "../booking";
import {
  fetchBookingNotificationPreferences,
  fetchBookingNotificationSubscription,
  replaceBookingNotificationPreferences,
  replaceBookingNotificationSubscription,
  unsubscribeFromAllBookingNotificationSubscriptions,
} from "../bookingNotificationSubscriptions";

const subscription = (configurationId: number, enabled = false, version = 0) => ({
  configurationId,
  enabled,
  version,
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
      http.get("/api/v2/users/me/booking-notification-preferences", ({ request }) => {
        expect(request.headers.get("Authorization")).toBe("Bearer token");
        return HttpResponse.json({ notifyOwnActions: true, autoSubscribeOwnedItems: true });
      }),
      http.put("/api/v2/users/me/booking-notification-preferences", async ({ request }) => {
        preferencesBody = (await request.json()) as { autoSubscribeOwnedItems: boolean };
        return HttpResponse.json(preferencesBody as { autoSubscribeOwnedItems: boolean });
      }),
      http.get("/api/v2/booking-configurations/7/notification-subscription", () => HttpResponse.json(subscription(7))),
      http.put("/api/v2/booking-configurations/7/notification-subscription", async ({ request }) => {
        itemBody = await request.json();
        return HttpResponse.json(subscription(7, true, 1));
      }),
      http.delete("/api/v2/users/me/booking-notification-subscriptions", () => {
        deleted = true;
        return HttpResponse.json({ updatedCount: 2 });
      }),
    );

    expect(await fetchBookingNotificationPreferences("token")).toEqual({
      notifyOwnActions: true,
      autoSubscribeOwnedItems: true,
    });
    await replaceBookingNotificationPreferences({ notifyOwnActions: true, autoSubscribeOwnedItems: false }, "token");
    expect(preferencesBody).toEqual({ notifyOwnActions: true, autoSubscribeOwnedItems: false });
    expect(await fetchBookingNotificationSubscription(7, "token")).toEqual(subscription(7));
    expect(await replaceBookingNotificationSubscription(7, true, 0, "token")).toEqual(subscription(7, true, 1));
    expect(itemBody).toEqual({ enabled: true, version: 0 });
    expect(await unsubscribeFromAllBookingNotificationSubscriptions("token")).toBe(2);
    expect(deleted).toBe(true);
  });

  it("preserves a 409 subscription conflict as an API problem", async () => {
    server.use(
      http.put("/api/v2/booking-configurations/7/notification-subscription", () =>
        HttpResponse.json(
          { status: 409, code: "errors.api.v2.bookingNotifications.subscriptionConflict" },
          { status: 409 },
        ),
      ),
    );

    await expect(replaceBookingNotificationSubscription(7, true, 0, "token")).rejects.toBeInstanceOf(ApiV2ProblemError);
  });
});
