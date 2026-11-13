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
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import AllBookableItemsPage from "@/modules/booking/pages/all-bookable-items/AllBookableItemsPage";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";
import type { ApiV2CurrentUser } from "@/modules/common/api/v2/generated/models/apiV2CurrentUser";
import {
  getGetCurrentUserQueryKey,
  getGetMyBookingPreferencesQueryKey,
} from "@/modules/common/api/v2/generated/users/users";
import { inheritedBrowserBookingPreferences } from "../preferences/bookingPreferencesFixtures";

export const storyUser: ApiV2CurrentUser = {
  id: 1,
  username: "ada",
  email: "ada@example.com",
  firstName: "Ada",
  lastName: "Lovelace",
  homeFolderId: 2,
  workbenchId: 3,
  hasPiRole: false,
  hasSysAdminRole: true,
  profileImageUrl: null,
  profileImageApiUrl: null,
  orcid: { available: false, id: null },
  capabilities: { canUseInventory: true, canPublish: false, canViewSystem: true },
  livechat: { enabled: false, serverKey: null },
  session: {
    operatedAs: false,
    lastSession: null,
    canUseDevtools: false,
    canOverrideFeatureFlags: false,
    canChangeFeatureFlagBaselines: false,
  },
};

export function BookableItemPageStory({
  hasSysAdminRole = true,
  initialPath = "/booking/bookable-items/IN123",
  history,
}: {
  hasSysAdminRole?: boolean;
  initialPath?: string;
  history?: RouterHistory;
} = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  queryClient.setQueryData(getGetMyBookingPreferencesQueryKey(), inheritedBrowserBookingPreferences);
  queryClient.setQueryData(getGetCurrentUserQueryKey(), { ...storyUser, hasSysAdminRole });
  const rootRoute = createRootRoute({
    component: () => (
      <NuqsAdapter>
        <Outlet />
      </NuqsAdapter>
    ),
  });
  const bookingRoute = createRoute({ getParentRoute: () => rootRoute, path: "/booking", component: Outlet });
  const router = createRouter({
    routeTree: rootRoute.addChildren([
      bookingRoute.addChildren([
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/bookable-items/$globalId/{-$tab}/",
          validateSearch: bookableItemSearch,
          component: BookableItemPage,
        }),
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/config/bookable-items/",
          component: AllBookableItemsPage,
        }),
      ]),
    ]),
    history: history ?? createMemoryHistory({ initialEntries: [initialPath] }),
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
