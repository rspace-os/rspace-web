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
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import { bookingFormSearch } from "@/modules/booking/pages/bookingFormSearch";
import CalendarPage from "@/modules/booking/pages/calendar/CalendarPage";
import BookingPreferencesPage from "@/modules/booking/pages/preferences/BookingPreferencesPage";
import { currentUserQueryKeys } from "@/modules/common/queries/currentUser";
import { apiV2CollectionMetadataFromOpenApi } from "@/modules/common/table-list/adapters/apiV2/apiV2CollectionMetadata";
import { bookableItemsOpenApi } from "../bookable-items/mocks/bookableItemsMocks";
import { currentUser } from "../mocks/currentUserFixture";

/** Fetches preferences so reload persistence is observable. */
export function BookingPreferencesPageStory() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(currentUserQueryKeys.me(), currentUser);
  queryClient.setQueryData(
    ["api-v2", "openapi", "booking-configurations"],
    apiV2CollectionMetadataFromOpenApi(bookableItemsOpenApi, "booking-configurations"),
  );

  const root = createRootRoute({ component: Outlet });
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
