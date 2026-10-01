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
import { Suspense } from "react";
import { MemoryHistoryNuqsAdapter as NuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import { apiV2CollectionMetadataFromOpenApi } from "@/modules/common/table-list/adapters/apiV2/apiV2CollectionMetadata";
import { createBookableItemRoute } from "../bookable-items/routes";
import { createBookingEventRouteTree } from "../bookings/routes";
import { inheritedBrowserBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import { MyBookingsRoutePage } from "./MyBookingsPage";
import { bookingsOpenApi } from "./mocks/bookingMocks";

const storySearch = new URLSearchParams({
  period: "upcoming",
  "my-bookings.q": "confocal",
  "my-bookings.where": "target.name=contains=scope",
  "my-bookings.columns": '{ "fields": ["target", "start", "end", "purpose", "timezone"] }',
  "my-bookings.sort": "-start",
});

export const myBookingsStoryUrl = `/booking/my-bookings?${storySearch}`;

export function MyBookingsPageStory({
  history = createMemoryHistory({ initialEntries: [myBookingsStoryUrl] }),
}: {
  history?: RouterHistory;
} = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  queryClient.setQueryData(
    ["api-v2", "openapi", "bookings"],
    apiV2CollectionMetadataFromOpenApi(bookingsOpenApi, "bookings"),
  );
  const root = createRootRoute({
    component: () => (
      <NuqsAdapter>
        <Outlet />
      </NuqsAdapter>
    ),
  });
  const bookingRoute = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const pageRoute = createRoute({
    getParentRoute: () => bookingRoute,
    path: "/my-bookings",
    component: () => <MyBookingsRoutePage requesterId={84} title="My Bookings" />,
  });
  const router = createRouter({
    routeTree: root.addChildren([
      bookingRoute.addChildren([
        pageRoute,
        createBookableItemRoute(bookingRoute),
        createBookingEventRouteTree(bookingRoute),
      ]),
    ]),
    history,
  });
  return (
    <QueryClientProvider client={queryClient}>
      <BookingCreationStoreProvider>
        <Suspense fallback={null}>
          <RouterProvider router={router as never} />
        </Suspense>
      </BookingCreationStoreProvider>
    </QueryClientProvider>
  );
}
