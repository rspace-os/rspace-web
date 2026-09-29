import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
  useMatches,
} from "@tanstack/react-router";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { DEFAULT_SCHEDULING_SETTINGS } from "@/modules/booking/configuration/schedulingSettings";
import { createBookingIndexRoute, createBookingRoute } from "@/modules/booking/pages/BookingPage";
import { ownerBookingAccess } from "@/modules/booking/pages/bookable-items/mocks/bookableItemsMocks";
import { createBookableItemRoute } from "@/modules/booking/pages/bookable-items/routes";
import { bookingsOpenApi } from "@/modules/booking/pages/my-bookings/mocks/bookingMocks";
import { inheritedBrowserBookingPreferences } from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
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

type AdministrationAccess = { readableConfigurations: number; eligibleTargets: number };

function renderAt(
  initialPath: string,
  hasSysAdminRole = true,
  preferencesReady = Promise.resolve(),
  { readableConfigurations, eligibleTargets }: AdministrationAccess = { readableConfigurations: 1, eligibleTargets: 0 },
) {
  server.use(
    // Answers only the sidebar's unfiltered count; a test's own lookup handler serves filtered reads.
    http.get("/api/v2/booking-configurations", ({ request }) =>
      new URL(request.url).searchParams.has("where")
        ? undefined
        : HttpResponse.json({
            docs: [],
            totalDocs: readableConfigurations,
            limit: 1,
            page: 1,
            pagingCounter: 1,
            totalPages: readableConfigurations,
            hasPrevPage: false,
            hasNextPage: readableConfigurations > 1,
            prevPage: null,
            nextPage: readableConfigurations > 1 ? 2 : null,
          }),
    ),
    http.get("/api/v2/booking-configuration-targets", () =>
      HttpResponse.json(
        Array.from({ length: eligibleTargets }, (_, index) => ({
          id: 500 + index,
          globalId: `IN${500 + index}`,
          name: `Unconfigured instrument ${index + 1}`,
          deleted: false,
        })),
      ),
    ),
    http.get("/api/v2/users/me", () => HttpResponse.json({ ...currentUser, hasSysAdminRole })),
    http.get("/api/v2/instruments/123", () =>
      HttpResponse.json({ parentContainerName: null, parentContainerGlobalId: null }),
    ),
    http.get("/api/v2/openapi.json", () => HttpResponse.json(bookingsOpenApi)),
    http.get("/api/v2/bookings", () =>
      HttpResponse.json({
        docs: [],
        totalDocs: 0,
        limit: 100,
        page: 1,
        pagingCounter: 1,
        totalPages: 0,
        hasPrevPage: false,
        hasNextPage: false,
        prevPage: null,
        nextPage: null,
      }),
    ),
    http.get("/api/v2/users/me/booking-preferences", async () => {
      await preferencesReady;
      return HttpResponse.json(inheritedBrowserBookingPreferences);
    }),
  );
  const rootRoute = createRootRoute({ component: TestShell });
  const bookingRoute = createBookingRoute(rootRoute);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createRouter({
    routeTree: rootRoute.addChildren([
      bookingRoute.addChildren([
        createBookingIndexRoute(bookingRoute),
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/calendar",
          component: () => (
            <main>
              <h1>{"Calendar destination"}</h1>
            </main>
          ),
        }),
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/calendar/bookings/add",
          component: () => <main>{"Add booking destination"}</main>,
        }),
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/config/settings",
          component: () => <main>{"Settings destination"}</main>,
        }),
        createBookableItemRoute(bookingRoute),
      ]),
    ]),
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

describe("booking layout", () => {
  it("omits breadcrumbs from the Booking root", async () => {
    renderAt("/booking");

    expect(await screen.findByRole("heading", { name: "booking:sidebar.dashboard" })).toBeVisible();
    expect(screen.queryByRole("navigation", { name: "booking:breadcrumbs.label" })).not.toBeInTheDocument();
  });

  it("shows the dashboard calendar without a subheading or bookable-items primer", async () => {
    renderAt("/booking", false);

    expect(await screen.findByRole("region", { name: "booking:dashboard.calendar.title" })).not.toHaveAttribute(
      "aria-describedby",
    );
    expect(screen.queryByText("booking:bookableItems.primer.description")).not.toBeInTheDocument();
  });

  it("shows linked ancestors and the current page on nested Booking routes", async () => {
    const { container } = renderAt("/booking/calendar/bookings/add");

    const breadcrumbs = await screen.findByRole("navigation", { name: "booking:breadcrumbs.label" });
    expect(within(breadcrumbs).getByRole("link", { name: "booking:sidebar.label" })).toHaveAttribute(
      "href",
      "/booking",
    );
    expect(within(breadcrumbs).getByRole("link", { name: "booking:calendar.title" })).toHaveAttribute(
      "href",
      "/booking/calendar",
    );
    expect(within(breadcrumbs).getByText("booking:bookings.addTitle")).toHaveAttribute("aria-current", "page");
    await expectAccessible(container);
  });

  it("keeps the shell mounted while preferences suspend the sidebar and inactive creation dialog", async () => {
    const preferences = Promise.withResolvers<void>();
    const { container } = renderAt("/booking", false, preferences.promise);
    try {
      const statuses = await screen.findAllByRole("status");
      expect(statuses.some((status) => status.textContent === "common:loading")).toBe(true);
      expect(statuses.every((status) => status.classList.contains("sr-only"))).toBe(true);
      expect(screen.queryByRole("link", { name: "booking:sidebar.settings" })).not.toBeInTheDocument();
      await expectAccessible(container);
    } finally {
      preferences.resolve();
    }
    expect(await screen.findByRole("link", { name: "booking:sidebar.calendar" })).toBeVisible();
  });

  it("is supplied to the shell by the booking route", async () => {
    const { container } = renderAt("/booking");
    expect(await screen.findByRole("heading", { name: "booking:sidebar.dashboard" })).toHaveClass(
      "text-2xl",
      "font-semibold",
    );
    expect(screen.getAllByRole("main")).toHaveLength(1);

    expect(await screen.findByRole("button", { name: "booking:sidebar.administration" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:sidebar.approvalQueue" })).not.toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "booking:sidebar.dashboard" })).toHaveAttribute("href", "/booking");

    expect(await screen.findByRole("link", { name: "booking:sidebar.myBookings" })).toHaveAttribute(
      "href",
      "/booking/my-bookings?period=upcoming",
    );

    expect(await screen.findByRole("link", { name: "booking:sidebar.settings" })).toHaveAttribute(
      "href",
      "/booking/config/settings",
    );

    expect(await screen.findByRole("link", { name: "booking:sidebar.calendar" })).toHaveAttribute(
      "href",
      expect.stringMatching(/^\/booking\/calendar\?date=\d{4}-\d{2}-\d{2}$/),
    );

    expect(await screen.findByRole("link", { name: "booking:sidebar.allItems" })).toHaveAttribute(
      "href",
      expect.stringMatching(/^\/booking\/all-items\?date=\d{4}-\d{2}-\d{2}$/),
    );

    expect(await screen.findByRole("link", { name: "booking:sidebar.addBooking" })).toHaveAttribute(
      "href",
      expect.stringMatching(/^\/booking\/calendar\/bookings\/add\?date=\d{4}-\d{2}-\d{2}$/),
    );

    expect(await screen.findByRole("link", { name: "booking:sidebar.bookableItems" })).toHaveAttribute(
      "href",
      "/booking/config/bookable-items",
    );

    await expectAccessible(container);
  });

  it("returns to Dashboard from Calendar for non-sysadmins", async () => {
    const user = userEvent.setup();
    renderAt("/booking/calendar?calendar-resources.q=Confocal", false);
    expect(await screen.findByRole("heading", { name: "Calendar destination" })).toBeVisible();
    const dashboard = await screen.findByRole("link", { name: "booking:sidebar.dashboard" });
    expect(dashboard).not.toHaveAttribute("aria-current", "page");

    await user.click(dashboard);

    expect(await screen.findByRole("heading", { name: "booking:sidebar.dashboard" })).toBeVisible();
    expect(dashboard).toHaveAttribute("aria-current", "page");
    expect(screen.queryByRole("heading", { name: "Calendar destination" })).not.toBeInTheDocument();
  });

  it("collapses the Administration sub-items", async () => {
    const user = userEvent.setup();
    renderAt("/booking");

    const administration = await screen.findByRole("button", { name: "booking:sidebar.administration" });
    expect(administration).toHaveAttribute("aria-expanded", "true");

    await user.click(administration);

    expect(administration).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("link", { name: "booking:sidebar.settings" })).not.toBeInTheDocument();
  });

  it("shows Bookable items but not Settings to users who can read a booking configuration", async () => {
    renderAt("/booking", false, undefined, { readableConfigurations: 3, eligibleTargets: 0 });

    expect(await screen.findByRole("link", { name: "booking:sidebar.calendar" })).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "booking:sidebar.bookableItems" })).toHaveAttribute(
      "href",
      "/booking/config/bookable-items",
    );
    expect(screen.getByRole("button", { name: "booking:sidebar.administration" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "booking:sidebar.settings" })).not.toBeInTheDocument();
  });

  it("shows Bookable items to users who can only set up an instrument", async () => {
    renderAt("/booking", false, undefined, { readableConfigurations: 0, eligibleTargets: 1 });

    expect(await screen.findByRole("link", { name: "booking:sidebar.bookableItems" })).toHaveAttribute(
      "href",
      "/booking/config/bookable-items",
    );
    expect(screen.queryByRole("link", { name: "booking:sidebar.settings" })).not.toBeInTheDocument();
  });

  it("hides Administration from users who can neither read nor set up a bookable item", async () => {
    let signalRequests = 0;
    server.events.on("request:start", ({ request }) => {
      const path = new URL(request.url).pathname;
      if (path === "/api/v2/booking-configurations" || path === "/api/v2/booking-configuration-targets") {
        signalRequests += 1;
      }
    });
    try {
      const { container } = renderAt("/booking", false, undefined, { readableConfigurations: 0, eligibleTargets: 0 });

      expect(await screen.findByRole("link", { name: "booking:sidebar.calendar" })).toBeInTheDocument();
      await waitFor(() => expect(signalRequests).toBe(2));
      expect(screen.queryByRole("button", { name: "booking:sidebar.administration" })).not.toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "booking:sidebar.bookableItems" })).not.toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "booking:sidebar.settings" })).not.toBeInTheDocument();
      await expectAccessible(container);
    } finally {
      server.events.removeAllListeners("request:start");
    }
  });

  it("keeps Settings for a sysadmin who has no bookable items to administer", async () => {
    renderAt("/booking", true, undefined, { readableConfigurations: 0, eligibleTargets: 0 });

    expect(await screen.findByRole("link", { name: "booking:sidebar.settings" })).toHaveAttribute(
      "href",
      "/booking/config/settings",
    );
    expect(screen.queryByRole("link", { name: "booking:sidebar.bookableItems" })).not.toBeInTheDocument();
  });

  it("links the Administration breadcrumb to the bookable items list", async () => {
    renderAt("/booking/config/settings");

    const breadcrumbs = await screen.findByRole("navigation", { name: "booking:breadcrumbs.label" });
    expect(within(breadcrumbs).getByRole("link", { name: "booking:sidebar.administration" })).toHaveAttribute(
      "href",
      "/booking/config/bookable-items",
    );
    expect(within(breadcrumbs).getByText("booking:settings.title")).toHaveAttribute("aria-current", "page");
  });

  it("stays mounted on the merged bookable item route", async () => {
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json({
          docs: [
            {
              id: 42,
              configurationVersion: 0,
              target: {
                relationTo: "booking-instruments",
                value: { id: 123, name: "Confocal microscope", deleted: false },
                globalId: "IN123",
              },
              enabled: true,
              state: "ACTIVE",
              timezone: "Europe/Berlin",
              ...DEFAULT_SCHEDULING_SETTINGS,
              updatedAt: null,
              ...ownerBookingAccess,
            },
          ],
          totalDocs: 1,
          limit: 2,
          page: 1,
          pagingCounter: 1,
          totalPages: 1,
          hasPrevPage: false,
          hasNextPage: false,
          prevPage: null,
          nextPage: null,
        }),
      ),
      http.get("/api/v2/bookings", () =>
        HttpResponse.json({
          docs: [],
          totalDocs: 0,
          limit: 10,
          page: 1,
          pagingCounter: 1,
          totalPages: 0,
          hasPrevPage: false,
          hasNextPage: false,
          prevPage: null,
          nextPage: null,
        }),
      ),
    );
    renderAt("/booking/bookable-items/IN123/details?edit=true");

    expect(await screen.findByRole("link", { name: "booking:sidebar.calendar" })).toBeInTheDocument();
    // The bookable item itself is the page heading; "Bookable item" is now the
    // eyebrow label above it.
    expect(await screen.findByRole("heading", { level: 1, name: "Confocal microscope" })).toBeVisible();
    expect(await screen.findByRole("button", { name: "booking:bookableItems.actions.save" })).toBeVisible();
  });
});
