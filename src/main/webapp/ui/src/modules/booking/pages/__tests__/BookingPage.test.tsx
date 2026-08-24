import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRouter,
  Outlet,
  RouterProvider,
  useMatches,
} from "@tanstack/react-router";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { createBookingRoute } from "@/modules/booking/pages/BookingPage";
import { getSidebarRenderer } from "@/modules/common/app/AppShell";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import type { CurrentUser } from "@/modules/common/queries/currentUser";
import { Sidebar, SidebarContent, SidebarInset, SidebarProvider, SidebarTrigger } from "@/modules/common/ui/sidebar";

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: vi.fn(),
}));

const currentUser: CurrentUser = {
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
  capabilities: { canUseInventory: false, canPublish: false, canViewSystem: true },
  livechat: { enabled: false, serverKey: null },
  session: {
    operatedAs: false,
    lastSession: null,
    canUseDevtools: false,
    canOverrideFeatureFlags: false,
    canChangeFeatureFlagBaselines: false,
  },
};

const mockedUseOauthTokenQuery = vi.mocked(useOauthTokenQuery);

beforeEach(() => {
  mockedUseOauthTokenQuery.mockReturnValue({ data: "token" } as ReturnType<typeof useOauthTokenQuery>);
});

/** Mirrors how AppShell resolves and renders the sidebar, without its auth/query dependencies. */
function TestShell() {
  const renderSidebar = useMatches({ select: getSidebarRenderer });

  return (
    <SidebarProvider defaultOpen={Boolean(renderSidebar)}>
      <SidebarTrigger />
      <Sidebar>
        <SidebarContent>{renderSidebar?.()}</SidebarContent>
      </Sidebar>
      <SidebarInset>
        <Outlet />
      </SidebarInset>
    </SidebarProvider>
  );
}

function renderAt(initialPath: string, hasSysAdminRole = true) {
  server.use(http.get("/api/v2/users/me", () => HttpResponse.json({ ...currentUser, hasSysAdminRole })));
  const rootRoute = createRootRoute({ component: TestShell });
  const bookingRoute = createBookingRoute(rootRoute);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createRouter({
    routeTree: rootRoute.addChildren([bookingRoute]),
    history: createMemoryHistory({ initialEntries: [initialPath] }),
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <RouterProvider router={router as never} />
      </Suspense>
    </QueryClientProvider>,
  );
}

describe("booking sidebar", () => {
  it("is supplied to the shell by the booking route", async () => {
    const { container } = renderAt("/booking");

    // i18next runs in cimode under vitest, so t() renders "<namespace>:<key>"
    for (const key of [
      "dashboard",
      "calendar",
      "myBookings",
      "administration",
      "approvalQueue",
      "settings",
      "bookableItems",
    ]) {
      expect(await screen.findByRole("button", { name: `booking:sidebar.${key}` })).toBeInTheDocument();
    }

    await expectAccessible(container);
  });

  it("collapses the Administration sub-items", async () => {
    const user = userEvent.setup();
    renderAt("/booking");

    const administration = await screen.findByRole("button", { name: "booking:sidebar.administration" });
    expect(administration).toHaveAttribute("aria-expanded", "true");

    await user.click(administration);

    expect(administration).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("button", { name: "booking:sidebar.settings" })).not.toBeInTheDocument();
  });

  it("hides Administration from users who are not sysadmins", async () => {
    renderAt("/booking", false);

    expect(await screen.findByRole("button", { name: "booking:sidebar.dashboard" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:sidebar.administration" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:sidebar.bookableItems" })).not.toBeInTheDocument();
  });
});
