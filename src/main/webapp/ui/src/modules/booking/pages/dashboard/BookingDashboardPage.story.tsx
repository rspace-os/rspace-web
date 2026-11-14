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
import { currentUser } from "@/__tests__/helpers/currentUserFixture";
import { MemoryHistoryNuqsAdapter as NuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import {
  type BookingDisplayPreferencesDocument,
  mapBookingDisplayPreferences,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import { inheritedBrowserBookingPreferences } from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
import { getGetApiV2OpenApiDocumentQueryKey } from "@/modules/common/api/v2/generated/openapi/openapi";
import {
  getGetCurrentUserQueryKey,
  getGetMyBookingPreferencesQueryKey,
} from "@/modules/common/api/v2/generated/users/users";
import BookingPage from "../BookingPage";
import { bookingsOpenApi } from "../my-bookings/mocks/bookingMocks";
import BookingDashboardPage from "./BookingDashboardPage";

const bookingDashboardStoryUrl = "/booking";

function Destination({ title }: { title: string }) {
  return (
    <main>
      <h1>{title}</h1>
    </main>
  );
}

export function BookingDashboardPageStory({
  history = createMemoryHistory({ initialEntries: [bookingDashboardStoryUrl] }),
  preferences = mapBookingDisplayPreferences(inheritedBrowserBookingPreferences),
}: {
  history?: RouterHistory;
  preferences?: BookingDisplayPreferencesDocument;
} = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(getGetMyBookingPreferencesQueryKey(), preferences);
  queryClient.setQueryData(getGetCurrentUserQueryKey(), currentUser);
  queryClient.setQueryData(getGetApiV2OpenApiDocumentQueryKey(), bookingsOpenApi);

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
          path: "/calendar/",
          component: () => <Destination title="Calendar destination" />,
        }),
        createRoute({
          getParentRoute: () => booking,
          path: "/all-items",
          component: () => <Destination title="All items destination" />,
        }),
        createRoute({
          getParentRoute: () => booking,
          path: "/my-bookings/",
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
