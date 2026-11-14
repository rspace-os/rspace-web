import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { currentUser } from "@/__tests__/helpers/currentUserFixture";
import { server } from "@/__tests__/mswServer";
import type { ApiV2CurrentUser } from "@/modules/common/api/v2/generated/models/apiV2CurrentUser";
import type { ApiV2Problem } from "@/modules/common/api/v2/generated/models/apiV2Problem";
import type { BookingCalendarSubscriptionStatus } from "@/modules/common/api/v2/generated/models/bookingCalendarSubscriptionStatus";
import type { BookingNotificationSubscriptionControllerPreferenceReplacement } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionControllerPreferenceReplacement";
import type { BookingNotificationSubscriptionControllerUnsubscribeResult } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionControllerUnsubscribeResult";
import type { BookingNotificationSubscriptionManagerPreferences } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionManagerPreferences";

import BookingPreferencesPage from "../BookingPreferencesPage";

export function useDefaultBookingPreferencesHandlers() {
  server.use(
    http.get<never, never, ApiV2CurrentUser>("/api/v2/users/me", () => HttpResponse.json(currentUser)),
    http.get<never, never, BookingNotificationSubscriptionManagerPreferences | ApiV2Problem>(
      "/api/v2/users/me/booking-notification-preferences",
      () =>
        HttpResponse.json({
          autoSubscribeOwnedItems: false,
          notifyOwnActions: true,
          notifyOnCreated: true,
          notifyOnCancelled: true,
          emailDelivery: false,
        }),
    ),
    http.put<
      never,
      BookingNotificationSubscriptionControllerPreferenceReplacement,
      BookingNotificationSubscriptionManagerPreferences | ApiV2Problem
    >("/api/v2/users/me/booking-notification-preferences", async ({ request }) =>
      HttpResponse.json({ ...(await request.json()), emailDelivery: false }),
    ),
    http.delete<never, never, BookingNotificationSubscriptionControllerUnsubscribeResult | ApiV2Problem>(
      "/api/v2/users/me/booking-notification-subscriptions",
      () => HttpResponse.json({ updatedCount: 0 }),
    ),
    http.get<never, never, BookingCalendarSubscriptionStatus | ApiV2Problem>(
      "/api/v2/users/me/booking-calendar-subscription",
      () =>
        HttpResponse.json(
          { active: false, updatedAt: null, subscriptionUrl: null },
          { headers: { ETag: '"inactive"' } },
        ),
    ),
  );
}

export function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const rendered = render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <BookingPreferencesPage />
      </Suspense>
    </QueryClientProvider>,
  );
  return { ...rendered, queryClient };
}
