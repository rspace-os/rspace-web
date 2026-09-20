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
import {
  type BookingDisplayPreferencesDocument,
  bookingDisplayPreferencesQueryKey,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import { inheritedBrowserBookingPreferences } from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
import { currentUserQueryKeys } from "@/modules/common/queries/currentUser";
import { apiV2CollectionMetadataFromOpenApi } from "@/modules/common/table-list/adapters/apiV2/apiV2CollectionMetadata";
import BookingPage from "../BookingPage";
import { currentUser } from "../calendar/calendarFixtures";
import { bookingsOpenApi } from "../my-bookings/mocks/bookingMocks";
import BookingDashboardPage from "./BookingDashboardPage";

export const bookingDashboardStoryUrl = "/booking";

function Destination({ title }: { title: string }) {
  return (
    <main>
      <h1>{title}</h1>
    </main>
  );
}

export function BookingDashboardPageStory({
  history = createMemoryHistory({ initialEntries: [bookingDashboardStoryUrl] }),
  preferences = inheritedBrowserBookingPreferences,
}: {
  history?: RouterHistory;
  preferences?: BookingDisplayPreferencesDocument;
} = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, preferences);
  queryClient.setQueryData(currentUserQueryKeys.me(), currentUser);
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
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: BookingPage });
  const router = createRouter({
    routeTree: root.addChildren([
      booking.addChildren([
        createRoute({ getParentRoute: () => booking, path: "/", component: BookingDashboardPage }),
        createRoute({
          getParentRoute: () => booking,
          path: "/calendar",
          component: () => <Destination title="Calendar destination" />,
        }),
        createRoute({
          getParentRoute: () => booking,
          path: "/all-items",
          component: () => <Destination title="All items destination" />,
        }),
        createRoute({
          getParentRoute: () => booking,
          path: "/my-bookings",
          component: () => <Destination title="My bookings destination" />,
        }),
        createRoute({
          getParentRoute: () => booking,
          path: "/calendar/bookings/$id",
          component: () => <Destination title="Booking details destination" />,
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
        <RouterProvider router={router as never} />
      </Suspense>
    </QueryClientProvider>
  );
}
