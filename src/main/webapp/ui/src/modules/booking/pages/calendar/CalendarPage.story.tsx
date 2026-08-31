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
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import type { CurrentUser } from "@/modules/common/queries/currentUser";
import { currentUserQueryKeys } from "@/modules/common/queries/currentUser";
import BookingPage from "../BookingPage";
import { createBookableItemRoute } from "../bookable-items/routes";
import { createAddBookingRoute, createEditBookingRoute } from "../bookings/routes";
import { inheritedBrowserBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import { currentUser } from "./calendarFixtures";
import { createCalendarRoute } from "./routes";

export function CalendarPageStory({ user = currentUser }: { user?: CurrentUser } = {}) {
  if (window.location.pathname === "/") {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
  }
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  queryClient.setQueryData(currentUserQueryKeys.me(), user);
  const root = createRootRoute({ component: Outlet });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: BookingPage });
  const router = createRouter({
    routeTree: root.addChildren([
      booking.addChildren([
        createCalendarRoute(booking),
        createAddBookingRoute(booking),
        createEditBookingRoute(booking),
        createBookableItemRoute(booking),
      ]),
    ]),
    history: createBrowserHistory(),
  });

  return (
    <QueryClientProvider client={queryClient}>
      <NuqsAdapter>
        <Suspense fallback={null}>
          <RouterProvider router={router as never} />
        </Suspense>
      </NuqsAdapter>
    </QueryClientProvider>
  );
}
