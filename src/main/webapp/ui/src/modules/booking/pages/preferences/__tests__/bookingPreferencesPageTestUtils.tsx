import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { server } from "@/__tests__/mswServer";
import { currentUser } from "@/modules/booking/pages/calendar/calendarFixtures";
import BookingPreferencesPage from "../BookingPreferencesPage";

export function useDefaultBookingPreferencesHandlers() {
  server.use(
    http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
    http.get("/api/v2/users/me/booking-notification-preferences", () =>
      HttpResponse.json({ autoSubscribeOwnedItems: false }),
    ),
    http.put("/api/v2/users/me/booking-notification-preferences", async ({ request }) =>
      HttpResponse.json(await request.json()),
    ),
    http.delete("/api/v2/users/me/booking-notification-subscriptions", () => HttpResponse.json({ updatedCount: 0 })),
    http.get("/api/v2/users/me/booking-calendar-subscription", () =>
      HttpResponse.json({ active: false, updatedAt: null, subscriptionUrl: null }, { headers: { ETag: '"inactive"' } }),
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
