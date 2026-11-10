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
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";
import { getGetApiV2OpenApiDocumentQueryKey } from "@/modules/common/api/v2/generated/openapi/openapi";
import { getGetMyBookingPreferencesQueryKey } from "@/modules/common/api/v2/generated/users/users";
import BookingPage from "../BookingPage";
import { bookableItemsOpenApi } from "../bookable-items/mocks/bookableItemsMocks";
import { institutionBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import AllBookableItemsPage from "./AllBookableItemsPage";

export function AllBookableItemsStory({
  containerWidth = 1500,
  history = createMemoryHistory({ initialEntries: ["/booking/all-items"] }),
}: {
  containerWidth?: number;
  history?: RouterHistory;
} = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(getGetMyBookingPreferencesQueryKey(), institutionBookingPreferences);
  queryClient.setQueryData(getGetApiV2OpenApiDocumentQueryKey(), bookableItemsOpenApi);
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
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/all-items/",
          component: () => <AllBookableItemsPage />,
        }),
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/bookable-items/$globalId/{-$tab}/",
          validateSearch: bookableItemSearch,
          component: BookableItemPage,
        }),
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
