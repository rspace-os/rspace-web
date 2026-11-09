import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import {
  getGetBookingCalendarSubscriptionQueryKey,
  getGetBookingConfigurationQueryKey,
  getGetMyBookingNotificationSubscriptionQueryKey,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { getGetMyBookingNotificationPreferencesQueryKey } from "@/modules/common/api/v2/generated/users/users";
import { invalidateBookingNotificationSubscriptions } from "../bookingNotificationSubscriptions";

describe("notification subscription invalidation", () => {
  it("refreshes every cached item subscription and leaves other operations alone", async () => {
    const client = new QueryClient();
    const subscriptions = [7, 19].map(getGetMyBookingNotificationSubscriptionQueryKey);
    const otherQueries = [
      getGetBookingConfigurationQueryKey(7),
      getGetBookingCalendarSubscriptionQueryKey(7),
      getGetMyBookingNotificationPreferencesQueryKey(),
    ];
    for (const key of [...subscriptions, ...otherQueries]) client.setQueryData(key, {});
    await invalidateBookingNotificationSubscriptions(client);
    for (const key of subscriptions) expect(client.getQueryState(key)?.isInvalidated).toBe(true);
    for (const key of otherQueries) expect(client.getQueryState(key)?.isInvalidated).toBe(false);
    client.clear();
  });
});
