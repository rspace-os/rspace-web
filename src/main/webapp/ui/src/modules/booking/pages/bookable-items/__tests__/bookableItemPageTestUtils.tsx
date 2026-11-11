import { getGetMyBookingPreferencesQueryKey } from "@/modules/common/api/v2/generated/users/users";
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
import { Suspense } from "react";
import { vi } from "vitest";
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { bookableItemFixtures } from "../mocks/bookableItemsMocks";

export const configuration = {
  ...bookableItemFixtures[0],
  configurationVersion: 0,
  timezone: "UTC",
  openingHours: [
    { dayOfWeek: 1, start: "08:00", end: "17:30" },
    { dayOfWeek: 2, start: "10:00", end: "16:00" },
  ],
  bufferBeforeMinutes: 5,
  bufferAfterMinutes: 15,
  createdAt: "2026-08-17T00:00:00Z",
  createdByName: "Grace Hopper",
  updatedAt: null,
};
export const mockedUseCurrentUserQuery = vi.mocked(useCurrentUserQuery);

export function setupPage() {
  mockedUseCurrentUserQuery.mockReturnValue({
    data: { hasSysAdminRole: true, session: { operatedAs: false } },
  } as ReturnType<typeof useCurrentUserQuery>);
}

export function renderPage(initialEntry = "/booking/bookable-items/IN123") {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(getGetMyBookingPreferencesQueryKey(), inheritedBrowserBookingPreferences);
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
