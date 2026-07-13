import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { setupServer } from "msw/node";
import { type ComponentProps, Suspense } from "react";
import { afterAll, afterEach, beforeAll, beforeEach, expect, it, vi } from "vitest";
import AppShell from "@/modules/common/app/AppShell";
import { useLighthouseSdk } from "@/modules/common/app/lighthouse";
import { useLivechatPropertiesQuery } from "@/modules/common/app/queries/livechatProperties";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { UIStoreProvider } from "@/modules/common/stores/uiStore";
import { UserSessionStoreProvider } from "@/modules/common/stores/userSessionStore";

vi.mock("@tanstack/react-router", () => ({
  HeadContent: () => null,
  Link: ({ to, ...props }: { to: string } & ComponentProps<"a">) => <a href={to} {...props} />,
  Outlet: () => null,
  useMatches: () => ({ currentPage: "workspace" }),
  useRouterState: () => false,
}));

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: vi.fn(),
}));

vi.mock("@/modules/common/app/queries/livechatProperties", () => ({
  useLivechatPropertiesQuery: vi.fn(),
}));

vi.mock("@/modules/common/app/lighthouse", () => ({
  useLighthouseSdk: vi.fn(),
}));

const server = setupServer();

beforeAll(() => {
  fetchMock.disableMocks();
  server.listen({ onUnhandledRequest: "error" });
});

beforeEach(() => {
  vi.mocked(useOauthTokenQuery).mockReturnValue({ data: "token" } as ReturnType<typeof useOauthTokenQuery>);
  vi.mocked(useLivechatPropertiesQuery).mockReturnValue({
    data: { livechatEnabled: false, currentUser: "ada" },
  } as ReturnType<typeof useLivechatPropertiesQuery>);
  vi.mocked(useLighthouseSdk).mockReturnValue({ lighthouseReady: false, showLighthouse: vi.fn() });
});

afterEach(() => server.resetHandlers());

afterAll(() => {
  server.close();
  fetchMock.enableMocks();
});

it("shares one current-user request across the authenticated app shell and never calls v1 navigation data", async () => {
  let currentUserRequests = 0;
  let legacyNavigationRequests = 0;
  server.use(
    http.get("/api/v2/users/me", () => {
      currentUserRequests += 1;
      return HttpResponse.json({
        id: 123,
        username: "ada",
        email: "ada@example.com",
        firstName: "Ada",
        lastName: "Lovelace",
        homeFolderId: 456,
        workbenchId: 789,
        hasPiRole: false,
        hasSysAdminRole: false,
        profileImageUrl: null,
        orcid: { available: false, id: null },
        capabilities: {
          canUseInventory: true,
          canPublish: false,
          canViewSystem: false,
        },
        session: {
          operatedAs: false,
          lastSession: null,
        },
      });
    }),
    http.get("/api/v2/config", () =>
      HttpResponse.json({
        branding: { bannerImageUrl: "/public/banner" },
        helpLinks: [],
        deploymentDescription: "",
        deploymentHelpEmail: "",
      }),
    ),
    http.get("/api/v1/userDetails/uiNavigationData", () => {
      legacyNavigationRequests += 1;
      return HttpResponse.json({});
    }),
  );
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  render(
    <QueryClientProvider client={queryClient}>
      <UIStoreProvider>
        <UserSessionStoreProvider>
          <Suspense fallback={null}>
            <AppShell />
          </Suspense>
        </UserSessionStoreProvider>
      </UIStoreProvider>
    </QueryClientProvider>,
  );

  await waitFor(() => expect(currentUserRequests).toBe(1));
  expect(legacyNavigationRequests).toBe(0);
});
