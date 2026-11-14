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
import { Suspense } from "react";
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";
import { getGetApiV2OpenApiDocumentQueryKey } from "@/modules/common/api/v2/generated/openapi/openapi";
import { getGetMyBookingPreferencesQueryKey } from "@/modules/common/api/v2/generated/users/users";
import { createBookingEventTestRouteTree } from "../bookings/__tests__/bookingEventTestHarness";
import { inheritedBrowserBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import { MyBookingsRoutePage } from "./MyBookingsPage";
import { bookingsOpenApi } from "./mocks/bookingMocks";

const storySearch = new URLSearchParams({
  period: "upcoming",
  "my-bookings.q": "confocal",
  "my-bookings.where": "target.name=contains=scope",
  "my-bookings.columns": '{ "fields": ["target", "start", "end", "purpose"] }',
  "my-bookings.sort": "-start",
});

export function MyBookingsPageStory() {
  if (window.location.pathname !== "/booking/my-bookings") {
    window.history.replaceState({}, "", `/booking/my-bookings?${storySearch}`);
  }
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(getGetMyBookingPreferencesQueryKey(), inheritedBrowserBookingPreferences);
  queryClient.setQueryData(getGetApiV2OpenApiDocumentQueryKey(), bookingsOpenApi);
  const root = createRootRoute({ component: Outlet });
  const bookingRoute = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const pageRoute = createRoute({
    getParentRoute: () => bookingRoute,
    path: "/my-bookings/",
    component: () => <MyBookingsRoutePage requesterId={84} title="My Bookings" />,
  });
  const router = createRouter({
    routeTree: root.addChildren([
      bookingRoute.addChildren([
        pageRoute,
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/bookable-items/$globalId/{-$tab}/",
          validateSearch: bookableItemSearch,
          component: BookableItemPage,
        }),
        createBookingEventTestRouteTree(bookingRoute),
      ]),
    ]),
    history: createBrowserHistory(),
  });
  return (
    <QueryClientProvider client={queryClient}>
      <NuqsAdapter>
        <BookingCreationStoreProvider>
          <Suspense fallback={null}>
            <RouterProvider router={router as never} />
          </Suspense>
        </BookingCreationStoreProvider>
      </NuqsAdapter>
    </QueryClientProvider>
  );
}
