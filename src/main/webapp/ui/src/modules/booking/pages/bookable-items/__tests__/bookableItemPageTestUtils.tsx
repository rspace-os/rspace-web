import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { bookableItemFixtures } from "../mocks/bookableItemsMocks";

export const configuration = {
  ...bookableItemFixtures[0],
  configurationVersion: 0,
  timezone: "UTC",
  slotGranularityMinutes: 5,
  incrementUnit: "MINUTES",
  alignToIncrement: true,
  openingHours: [
    { dayOfWeek: 1, start: "08:00", end: "17:30" },
    { dayOfWeek: 2, start: "10:00", end: "16:00" },
  ],

  bufferBeforeMinutes: 5,
  bufferAfterMinutes: 15,
  createdAt: "2026-08-17T00:00:00Z",
  createdByName: "Grace Hopper (grace)",
  updatedAt: null,
};
const mockedUseOauthTokenQuery = vi.mocked(useOauthTokenQuery);
export const mockedUseCurrentUserQuery = vi.mocked(useCurrentUserQuery);

export function setupPage() {
  let notificationSubscription = {
    configurationId: 7,
    enabled: false,
    version: 0,
    createdEnabled: true,
    cancelledEnabled: true,
    emailEnabled: false,
  };
  server.use(
    http.get("/api/v2/booking-configurations/7/notification-subscription", () =>
      HttpResponse.json(notificationSubscription),
    ),
    http.put("/api/v2/booking-configurations/7/notification-subscription", () => {
      notificationSubscription = { ...notificationSubscription, enabled: true, version: 1 };
      return HttpResponse.json(notificationSubscription);
    }),
  );
  mockedUseOauthTokenQuery.mockReturnValue({ data: "token" } as ReturnType<typeof useOauthTokenQuery>);
  mockedUseCurrentUserQuery.mockReturnValue({
    data: { id: 1, hasSysAdminRole: true, session: { operatedAs: false } },
  } as ReturnType<typeof useCurrentUserQuery>);
}

export function renderPage(
  initialEntry = "/booking/bookable-items/IN123",
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) {
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  const root = createRootRoute({ component: Outlet });
  const bookingRoute = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const router = createRouter({
    routeTree: root.addChildren([
      bookingRoute.addChildren([
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/bookable-items/$globalId/{-$tab}/",
          validateSearch: bookableItemSearch,
          component: BookableItemPage,
        }),
      ]),
    ]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
  });
  const result = render(
    <QueryClientProvider client={queryClient}>
      <BookingCreationStoreProvider>
        <Suspense fallback={null}>
          <RouterProvider router={router as never} />
        </Suspense>
      </BookingCreationStoreProvider>
    </QueryClientProvider>,
  );
  return { ...result, queryClient, router };
}
