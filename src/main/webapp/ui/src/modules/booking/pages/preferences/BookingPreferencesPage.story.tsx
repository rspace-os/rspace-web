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
import { currentUser } from "@/__tests__/helpers/currentUserFixture";
import { isPlainDate } from "@/modules/booking/domain/bookingTime";
import CalendarPage from "@/modules/booking/pages/calendar/CalendarPage";
import BookingPreferencesPage from "@/modules/booking/pages/preferences/BookingPreferencesPage";
import { getGetApiV2OpenApiDocumentQueryKey } from "@/modules/common/api/v2/generated/openapi/openapi";
import { getGetCurrentUserQueryKey } from "@/modules/common/api/v2/generated/users/users";
import { bookableItemsOpenApi } from "../bookable-items/mocks/bookableItemsMocks";

/** Fetches preferences so reload persistence is observable. */
export function BookingPreferencesPageStory() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData(getGetCurrentUserQueryKey(), currentUser);
  queryClient.setQueryData(getGetApiV2OpenApiDocumentQueryKey(), bookableItemsOpenApi);

  const root = createRootRoute({ component: Outlet });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const router = createRouter({
    routeTree: root.addChildren([
      booking.addChildren([
        createRoute({ getParentRoute: () => booking, path: "/preferences/", component: BookingPreferencesPage }),
        createRoute({
          getParentRoute: () => booking,
          path: "/calendar/",
          validateSearch: (search: Record<string, unknown>): { date?: string; target?: string } => ({
            date: typeof search.date === "string" && isPlainDate(search.date) ? search.date : undefined,
            ...(typeof search.target === "string" && /^IN\d+$/.test(search.target) ? { target: search.target } : {}),
          }),
          component: CalendarPage,
        }),
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
