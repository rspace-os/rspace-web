import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from "@tanstack/react-router";
import { Suspense } from "react";
import { disabledFeatureFlags } from "@/featureFlags/schema";
import { getGetApiV2ConfigQueryKey } from "@/modules/common/api/v2/generated/config/config";
import { getListFeatureFlagsQueryKey } from "@/modules/common/api/v2/generated/feature-flags/feature-flags";
import { getListMaintenancesQueryKey } from "@/modules/common/api/v2/generated/maintenances/maintenances";
import type { ApiV2CurrentUser } from "@/modules/common/api/v2/generated/models/apiV2CurrentUser";
import type { ListMaintenances200 } from "@/modules/common/api/v2/generated/models/listMaintenances200";
import { getGetCurrentUserQueryKey } from "@/modules/common/api/v2/generated/users/users";
import AppBar from "./AppBar";

const storyUser: ApiV2CurrentUser = {
  id: 1,
  username: "ada",
  email: "ada@example.com",
  firstName: "Ada",
  lastName: "Lovelace",
  homeFolderId: 2,
  workbenchId: 3,
  hasPiRole: true,
  hasSysAdminRole: false,
  profileImageUrl: null,
  profileImageApiUrl: null,
  orcid: { available: true, id: null },
  capabilities: { canUseInventory: true, canPublish: true, canViewSystem: false },
  livechat: { enabled: false, serverKey: null },
  session: {
    operatedAs: false,
    lastSession: null,
    canUseDevtools: false,
    canOverrideFeatureFlags: false,
    canChangeFeatureFlagBaselines: false,
  },
};

function AppBarRoute() {
  return (
    <>
      <AppBar
        currentPage="workspace"
        renderHamburger={() => <button type="button" aria-label="Menu" className="size-8 shrink-0" />}
      />
      <main aria-label="Scrollable content" className="h-[200vh]" />
    </>
  );
}

export function AppBarStory({
  administrator = false,
  bookingEnabled = false,
  bannerImageUrl = "",
}: {
  administrator?: boolean;
  bookingEnabled?: boolean;
  bannerImageUrl?: string;
}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(getGetCurrentUserQueryKey(), {
    ...storyUser,
    hasSysAdminRole: administrator,
    capabilities: { ...storyUser.capabilities, canViewSystem: administrator },
  });
  queryClient.setQueryData(getGetApiV2ConfigQueryKey(), {
    version: "2.99.1",
    branding: { bannerImageUrl },
    helpLinks: [{ label: "Local help", url: "https://help.example.com" }],
    deploymentDescription: "",
    deploymentHelpEmail: null,
  });
  queryClient.setQueryDefaults(getListMaintenancesQueryKey(), {
    enabled: false,
    initialData: { docs: [], page: 1, limit: 1 } satisfies ListMaintenances200,
  });
  queryClient.setQueryData([...getListFeatureFlagsQueryKey({ limit: 100 }), "featureFlags"], {
    ...disabledFeatureFlags(),
    bookingEnabled,
  });

  const rootRoute = createRootRoute({ component: AppBarRoute });
  const router = createRouter({
    routeTree: rootRoute,
    history: createMemoryHistory({ initialEntries: ["/"] }),
  });

  return (
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <RouterProvider router={router} />
      </Suspense>
    </QueryClientProvider>
  );
}
