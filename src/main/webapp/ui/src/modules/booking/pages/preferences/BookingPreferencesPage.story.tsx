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
import { currentUser } from "@/__tests__/helpers/currentUserFixture";
import { MemoryHistoryNuqsAdapter as NuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import { bookingFormSearch } from "@/modules/booking/pages/bookingFormSearch";
import CalendarPage from "@/modules/booking/pages/calendar/CalendarPage";
import BookingPreferencesPage from "@/modules/booking/pages/preferences/BookingPreferencesPage";
import { getGetApiV2OpenApiDocumentQueryKey } from "@/modules/common/api/v2/generated/openapi/openapi";
import { getGetCurrentUserQueryKey } from "@/modules/common/api/v2/generated/users/users";
import AllBookableItemsPage from "../all-bookable-items/AllBookableItemsPage";
import { bookableItemsOpenApi } from "../bookable-items/mocks/bookableItemsMocks";

const _storyClock = () => new Date("2026-08-28T16:00:00Z");

/** A multi-route story that deliberately fetches preferences so reload persistence is observable. */
export function BookingPreferencesPageStory({
  history = createMemoryHistory({ initialEntries: ["/booking/preferences"] }),
}: {
  history?: RouterHistory;
}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData(getGetCurrentUserQueryKey(), currentUser);
  queryClient.setQueryData(getGetApiV2OpenApiDocumentQueryKey(), bookableItemsOpenApi);

  const root = createRootRoute({
    component: () => (
      <NuqsAdapter>
        <Outlet />
      </NuqsAdapter>
    ),
  });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const router = createRouter({
    routeTree: root.addChildren([
      booking.addChildren([
        createRoute({ getParentRoute: () => booking, path: "/preferences/", component: BookingPreferencesPage }),
        createRoute({
          getParentRoute: () => booking,
          path: "/calendar/",
          validateSearch: bookingFormSearch,
          component: CalendarPage,
        }),
        createRoute({
          getParentRoute: () => booking,
          path: "/all-items/",
          component: () => <AllBookableItemsPage />,
        }),
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
