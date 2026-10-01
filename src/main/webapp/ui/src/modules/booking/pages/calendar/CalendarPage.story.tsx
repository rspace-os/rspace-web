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
import {
  type BookingDisplayPreferencesDocument,
  bookingDisplayPreferencesQueryKey,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import { isPlainDate } from "@/modules/booking/domain/bookingTime";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";
import CalendarPage from "@/modules/booking/pages/calendar/CalendarPage";
import { type CurrentUser, currentUserQueryKeys } from "@/modules/common/queries/currentUser";
import BookingPage from "../BookingPage";
import { inheritedBrowserBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import { currentUser } from "./calendarFixtures";

export function CalendarPageStory({
  user = currentUser,
  preferences = inheritedBrowserBookingPreferences,
}: {
  user?: CurrentUser;
  preferences?: BookingDisplayPreferencesDocument;
} = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, preferences);
  queryClient.setQueryData(currentUserQueryKeys.me(), user);
  const root = createRootRoute({ component: Outlet });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: BookingPage });
  const router = createRouter({
    routeTree: root.addChildren([
      booking.addChildren([
        createRoute({
          getParentRoute: () => booking,
          path: "/calendar/",
          validateSearch: (search: Record<string, unknown>): { date?: string; target?: string } => ({
            date: typeof search.date === "string" && isPlainDate(search.date) ? search.date : undefined,
            ...(typeof search.target === "string" && /^IN\d+$/.test(search.target) ? { target: search.target } : {}),
          }),
          component: CalendarPage,
        }),
        createRoute({
          getParentRoute: () => booking,
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
          <RouterProvider router={router as never} />
        </Suspense>
      </NuqsAdapter>
    </QueryClientProvider>
  );
}
