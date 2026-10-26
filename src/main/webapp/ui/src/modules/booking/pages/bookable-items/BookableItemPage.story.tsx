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
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import { BookingNoticesProvider } from "@/modules/booking/feedback/BookingNotices";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import BookableItemsPage from "@/modules/booking/pages/bookable-items/BookableItemsPage";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";
import { type CurrentUser, currentUserQueryKeys } from "@/modules/common/queries/currentUser";
import { inheritedBrowserBookingPreferences } from "../preferences/bookingPreferencesFixtures";

export const storyUser: CurrentUser = {
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
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  queryClient.setQueryData(currentUserQueryKeys.me(), { ...storyUser, hasSysAdminRole });
  const rootRoute = createRootRoute({
    component: () => (
      <NuqsAdapter>
        <Outlet />
      </NuqsAdapter>
    ),
  });
  const bookingRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/booking",
    component: () => (
      <BookingNoticesProvider>
        <Outlet />
      </BookingNoticesProvider>
    ),
  });
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
          component: BookableItemsPage,
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
