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
import { allBookableItemsSearch } from "@/modules/booking/pages/all-bookable-items/search";
import { bookingFormSearch } from "@/modules/booking/pages/bookingFormSearch";
import CalendarPage from "@/modules/booking/pages/calendar/CalendarPage";
import BookingPreferencesPage from "@/modules/booking/pages/preferences/BookingPreferencesPage";
import { currentUserQueryKeys } from "@/modules/common/queries/currentUser";
import { apiV2CollectionMetadataFromOpenApi } from "@/modules/common/table-list/adapters/apiV2/apiV2CollectionMetadata";
import AllBookableItemsPage from "../all-bookable-items/AllBookableItemsPage";
import { bookableItemsOpenApi } from "../bookable-items/mocks/bookableItemsMocks";
import { currentUser } from "../mocks/currentUserFixture";

const storyClock = () => new Date("2026-08-28T16:00:00Z");

/** A multi-route story that deliberately fetches preferences so reload persistence is observable. */
export function BookingPreferencesPageStory({
  history = createMemoryHistory({ initialEntries: ["/booking/preferences"] }),
}: {
  history?: RouterHistory;
}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(currentUserQueryKeys.me(), currentUser);
  queryClient.setQueryData(
    ["api-v2", "openapi", "booking-configurations"],
    apiV2CollectionMetadataFromOpenApi(bookableItemsOpenApi, "booking-configurations"),
  );

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
          validateSearch: allBookableItemsSearch,
          component: () => <AllBookableItemsPage clock={storyClock} />,
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
