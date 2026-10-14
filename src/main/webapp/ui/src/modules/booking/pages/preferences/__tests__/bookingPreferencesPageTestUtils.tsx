import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { expect } from "vitest";
import { currentUser } from "@/__tests__/helpers/currentUserFixture";
import { server } from "@/__tests__/mswServer";

import BookingPreferencesPage from "../BookingPreferencesPage";

export function useDefaultBookingPreferencesHandlers() {
  server.use(
    http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
    http.get("/api/v2/users/me/booking-notification-preferences", () =>
      HttpResponse.json({
        autoSubscribeOwnedItems: false,
        notifyOwnActions: true,
        notifyOnCreated: true,
        notifyOnCancelled: true,
        emailDelivery: false,
      }),
    ),
    http.put("/api/v2/users/me/booking-notification-preferences", async ({ request }) =>
      HttpResponse.json({ ...((await request.json()) as object), emailDelivery: false }),
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

export async function expectStatusMessage(message: string) {
  await waitFor(() => {
    const matchingStatuses = screen.getAllByRole("status").filter((status) => status.textContent === message);
    expect(matchingStatuses).toHaveLength(1);
    expect(matchingStatuses[0]).toHaveTextContent(message);
  });
}

export function expectNoAlertMessage(message: string) {
  const matchingAlerts = screen.getAllByRole("alert").filter((alert) => alert.textContent === message);
  expect(matchingAlerts).toHaveLength(0);
}
