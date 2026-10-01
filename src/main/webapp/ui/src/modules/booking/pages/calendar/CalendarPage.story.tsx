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
import type { CurrentUser } from "@/modules/common/queries/currentUser";
import { currentUserQueryKeys } from "@/modules/common/queries/currentUser";
import BookingPage from "../BookingPage";
import { createBookableItemRoute } from "../bookable-items/routes";
import { createAddBookingRoute, createBookingEventRouteTree } from "../bookings/routes";
import { inheritedBrowserBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import { currentUser } from "./calendarFixtures";
import { createCalendarRoute } from "./routes";

export function CalendarPageStory({
  history = createMemoryHistory({ initialEntries: ["/booking/calendar?date=2026-08-17"] }),
  user = currentUser,
  preferences = inheritedBrowserBookingPreferences,
}: {
  history?: RouterHistory;
  user?: CurrentUser;
  preferences?: BookingDisplayPreferencesDocument;
} = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, preferences);
  queryClient.setQueryData(currentUserQueryKeys.me(), user);
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
        createCalendarRoute(booking),
        createAddBookingRoute(booking),
        createBookingEventRouteTree(booking),
        createBookableItemRoute(booking),
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
