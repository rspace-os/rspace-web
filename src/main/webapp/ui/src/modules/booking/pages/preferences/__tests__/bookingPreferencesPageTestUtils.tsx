import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { currentUser } from "@/__tests__/helpers/currentUserFixture";
import { server } from "@/__tests__/mswServer";
import type { ApiV2CurrentUser } from "@/modules/common/api/v2/generated/models/apiV2CurrentUser";
import type { ApiV2Problem } from "@/modules/common/api/v2/generated/models/apiV2Problem";
import type { BookingCalendarSubscriptionStatus } from "@/modules/common/api/v2/generated/models/bookingCalendarSubscriptionStatus";
import type { BookingNotificationPreferenceReplacement } from "@/modules/common/api/v2/generated/models/bookingNotificationPreferenceReplacement";
import type { BookingNotificationPreferences } from "@/modules/common/api/v2/generated/models/bookingNotificationPreferences";
import type { BookingNotificationUnsubscribeResult } from "@/modules/common/api/v2/generated/models/bookingNotificationUnsubscribeResult";

import BookingPreferencesPage from "../BookingPreferencesPage";

export function useDefaultBookingPreferencesHandlers() {
  server.use(
    http.get<never, never, ApiV2CurrentUser>("/api/v2/users/me", () => HttpResponse.json(currentUser)),
    http.get<never, never, BookingNotificationPreferences | ApiV2Problem>(
      "/api/v2/users/me/booking-notification-preferences",
      () => HttpResponse.json({ notifyOwnActions: true, autoSubscribeOwnedItems: false }),
    ),
    http.put<never, BookingNotificationPreferenceReplacement, BookingNotificationPreferences | ApiV2Problem>(
      "/api/v2/users/me/booking-notification-preferences",
      async ({ request }) => HttpResponse.json(await request.json()),
    ),
    http.delete<never, never, BookingNotificationUnsubscribeResult | ApiV2Problem>(
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
