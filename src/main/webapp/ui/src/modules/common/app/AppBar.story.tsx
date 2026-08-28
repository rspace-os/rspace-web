import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from "@tanstack/react-router";
import { Suspense } from "react";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { featureFlagQueryKeys } from "@/featureFlags/queries";
import { disabledFeatureFlags } from "@/featureFlags/schema";
import { queryKeys as authQueryKeys } from "@/modules/common/hooks/auth";
import type { CurrentUser } from "@/modules/common/queries/currentUser";
import { currentUserQueryKeys } from "@/modules/common/queries/currentUser";
import AppBar from "./AppBar";
import { appConfigQueryKeys } from "./queries/config";
import { nextMaintenanceQueryKeys } from "./queries/nextMaintenance";

const storyUser: CurrentUser = {
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
    <AppBar
      currentPage="workspace"
      renderHamburger={() => <button type="button" aria-label="Menu" className="size-8 shrink-0" />}
    />
  );
}

export function AppBarStory() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(authQueryKeys.oauthToken(false), OAUTH_TOKEN);
  queryClient.setQueryData(authQueryKeys.oauthToken(true), OAUTH_TOKEN);
  queryClient.setQueryData(currentUserQueryKeys.me(), storyUser);
  queryClient.setQueryData(featureFlagQueryKeys.flags(), disabledFeatureFlags());
  queryClient.setQueryData(appConfigQueryKeys.all, {
    version: "2.99.1",
    branding: { bannerImageUrl: "" },
    helpLinks: [{ label: "Local help", url: "https://help.example.com" }],
    deploymentDescription: "",
    deploymentHelpEmail: null,
  });
  queryClient.setQueryData(nextMaintenanceQueryKeys.next(), null);

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
