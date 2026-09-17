import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  type RouterHistory,
  RouterProvider,
} from "@tanstack/react-router";
import { Suspense, useEffect } from "react";
import { MemoryHistoryNuqsAdapter as NuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import { apiV2CollectionMetadataFromOpenApi } from "@/modules/common/table-list/adapters/apiV2/apiV2CollectionMetadata";
import BookingPage from "../BookingPage";
import { bookableItemsOpenApi } from "../bookable-items/mocks/bookableItemsMocks";
import { createBookableItemRoute } from "../bookable-items/routes";
import { institutionBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import AllBookableItemsPage from "./AllBookableItemsPage";
import { createAllBookableItemsRoute } from "./routes";

const storyClock = () => new Date("2026-08-17T08:30:00Z");

export function AllBookableItemsStory({
  containerWidth = 1500,
  history = createMemoryHistory({ initialEntries: ["/booking/all-items?date=2026-08-17"] }),
}: {
  containerWidth?: number;
  history?: RouterHistory;
} = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, institutionBookingPreferences);
  queryClient.setQueryData(
    ["api-v2", "openapi", "booking-configurations"],
    apiV2CollectionMetadataFromOpenApi(bookableItemsOpenApi, "booking-configurations"),
  );
  const rootRoute = createRootRoute({
    component: () => (
      <NuqsAdapter>
        <Outlet />
      </NuqsAdapter>
    ),
  });
  const bookingRoute = createRoute({ getParentRoute: () => rootRoute, path: "/booking", component: BookingPage });
  const router = createRouter({
    routeTree: rootRoute.addChildren([
      bookingRoute.addChildren([
        createAllBookableItemsRoute(bookingRoute, () => <AllBookableItemsPage clock={storyClock} userTimeZone="UTC" />),
        createBookableItemRoute(bookingRoute),
      ]),
    ]),
    history,
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
      <Suspense fallback={null}>
        <div style={{ width: containerWidth }}>
          <RouterProvider router={router as never} />
        </div>
      </Suspense>
    </QueryClientProvider>
  );
}
