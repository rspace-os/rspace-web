import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createBrowserHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { NuqsAdapter } from "nuqs/adapters/react";
import { Suspense, useEffect } from "react";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import { allBookableItemsSearch } from "@/modules/booking/pages/all-bookable-items/search";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";
import { apiV2CollectionMetadataFromOpenApi } from "@/modules/common/table-list/api-v2/metadata";
import BookingPage from "../BookingPage";
import { bookableItemsOpenApi } from "../bookable-items/mocks/bookableItemsMocks";
import { institutionBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import AllBookableItemsPage from "./AllBookableItemsPage";

const storyClock = () => new Date("2026-08-17T08:30:00Z");

export function AllBookableItemsStory({ containerWidth = 1500 }: { containerWidth?: number } = {}) {
  if (window.location.pathname !== "/booking/all-items") {
    window.history.replaceState({}, "", "/booking/all-items?date=2026-08-17");
  }
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, institutionBookingPreferences);
  queryClient.setQueryData(
    ["api-v2", "openapi", "booking-configurations"],
    apiV2CollectionMetadataFromOpenApi(bookableItemsOpenApi, "booking-configurations"),
  );
  const rootRoute = createRootRoute({ component: Outlet });
  const bookingRoute = createRoute({ getParentRoute: () => rootRoute, path: "/booking", component: BookingPage });
  const router = createRouter({
    routeTree: rootRoute.addChildren([
      bookingRoute.addChildren([
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/all-items/",
          validateSearch: allBookableItemsSearch,
          component: () => <AllBookableItemsPage clock={storyClock} userTimeZone="UTC" />,
        }),
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/bookable-items/$globalId/{-$tab}/",
          validateSearch: bookableItemSearch,
          component: BookableItemPage,
        }),
      ]),
    ]),
    history: createBrowserHistory(),
  });
  useEffect(
    () => () => {
      router.history.destroy();
      queryClient.clear();
    },
    [router, queryClient],
  );

  return (
    <QueryClientProvider client={queryClient}>
      <NuqsAdapter>
        <Suspense fallback={null}>
          <div style={{ width: containerWidth }}>
            <RouterProvider router={router as never} />
          </div>
        </Suspense>
      </NuqsAdapter>
    </QueryClientProvider>
  );
}
