import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { server } from "@/__tests__/mswServer";
import type { ApiV2Problem } from "@/modules/common/api/v2/generated/models/apiV2Problem";
import type { BookingCalendarSubscriptionStatus } from "@/modules/common/api/v2/generated/models/bookingCalendarSubscriptionStatus";
import BookingPreferencesPage from "../BookingPreferencesPage";

export function useDefaultBookingPreferencesHandlers() {
  server.use(
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
