import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  type AnyRoute,
  createMemoryHistory,
  createRootRouteWithContext,
  createRoute,
  createRouter,
  RouterProvider,
} from "@tanstack/react-router";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import type { AppBarConfig } from "@/modules/common/app/AppBar.types";
import AppShell, { type AppBarRouteContext, RouteTransitionIndicator } from "@/modules/common/app/AppShell";
import { queryKeys as authQueryKeys } from "@/modules/common/hooks/auth";
import {
  navigationProgressQueryFilters,
  navigationProgressQueryMeta,
} from "@/modules/common/queries/navigationProgress";
import { useSidebar } from "@/modules/common/ui/sidebar";

const routerState = vi.hoisted(() => ({ isLoading: false }));

vi.mock("@tanstack/react-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@tanstack/react-router")>();
  return { ...actual, useRouterState: () => routerState.isLoading };
});

vi.mock("@/modules/common/app/AppBar", () => {
  const AppBar = ({ currentPage, renderHamburger }: AppBarConfig) => (
    <header>
      <span>{currentPage}</span>
      {renderHamburger?.()}
    </header>
  );
  return { default: AppBar, PublicAppBar: AppBar };
});

vi.mock("@/modules/common/stores/userSessionStore", () => ({
  UserSessionBootstrap: () => null,
}));

vi.mock("@/featureFlags/FeatureFlagDevtoolsMount", () => ({
  default: () => <output aria-label="Feature flag devtools" />,
}));

function testQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function renderIndicator(queryClient = testQueryClient()) {
  const { container } = render(
    <QueryClientProvider client={queryClient}>
      <RouteTransitionIndicator />
    </QueryClientProvider>,
  );
  const indicator = container.firstElementChild;
  if (!(indicator instanceof HTMLElement)) throw new Error("Expected the route transition indicator to render");
  return { indicator, queryClient };
}

function SidebarState() {
  const { state } = useSidebar();
  return <output aria-label="Route sidebar state">{state}</output>;
}

const renderTestSidebar = () => <SidebarState />;
const plainRouteText = "Plain route";
const sidebarRouteText = "Sidebar route";

const paragraph = (text: string) => () => <p>{text}</p>;

function shellRoot() {
  return createRootRouteWithContext<AppBarRouteContext>()({
    component: AppShell,
    notFoundComponent: paragraph("Page not found"),
  });
}

/** A plain page plus a parent whose children inherit, override, hide or fail to load its chrome. */
function chromeRoutes() {
  const rootRoute = shellRoot();
  const sidebarRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/with-sidebar",
    beforeLoad: () => ({ appBar: { currentPage: "workspace" }, sidebar: renderTestSidebar }),
    notFoundComponent: paragraph("Page not found"),
  });
  const failedGuardRoute = createRoute({
    getParentRoute: () => sidebarRoute,
    path: "/failed-guard",
    beforeLoad: () => {
      throw new Error("Route guard failed");
    },
    errorComponent: paragraph("Route failed"),
  });
  return rootRoute.addChildren([
    createRoute({ getParentRoute: () => rootRoute, path: "/plain", component: paragraph(plainRouteText) }),
    sidebarRoute.addChildren([
      createRoute({ getParentRoute: () => sidebarRoute, path: "/", component: paragraph(sidebarRouteText) }),
      createRoute({ getParentRoute: () => sidebarRoute, path: "/details", component: paragraph("Nested destination") }),
      createRoute({
        getParentRoute: () => sidebarRoute,
        path: "/override",
        beforeLoad: () => ({ appBar: { currentPage: "gallery" }, sidebar: paragraph("Child sidebar") }),
        component: paragraph("Override destination"),
      }),
      createRoute({
        getParentRoute: () => sidebarRoute,
        path: "/without-app-bar",
        beforeLoad: () => ({ appBar: false as const }),
        component: paragraph("App bar hidden"),
      }),
      createRoute({
        getParentRoute: () => sidebarRoute,
        path: "/failed-loader",
        loader: () => {
          throw new Error("Route loader failed");
        },
        errorComponent: paragraph("Route failed"),
      }),
      failedGuardRoute.addChildren([
        createRoute({
          getParentRoute: () => failedGuardRoute,
          path: "/details",
          component: paragraph("Unreachable destination"),
        }),
      ]),
    ]),
  ]);
}

function devtoolsRoutes() {
  const rootRoute = shellRoot();
  return rootRoute.addChildren([
    createRoute({ getParentRoute: () => rootRoute, path: "/plain", component: () => null }),
    createRoute({
      getParentRoute: () => rootRoute,
      path: "/public",
      beforeLoad: () => ({ appBar: { currentPage: "rspace", authenticated: false } }),
      component: () => null,
    }),
    createRoute({
      getParentRoute: () => rootRoute,
      path: "/without-app-bar",
      beforeLoad: () => ({ appBar: false as const }),
      component: () => null,
    }),
  ]);
}

function bookingRoutes() {
  const rootRoute = shellRoot();
  return rootRoute.addChildren([
    createRoute({
      getParentRoute: () => rootRoute,
      path: "/booking",
      beforeLoad: () => ({ appBar: { currentPage: "booking" }, sidebar: renderTestSidebar }),
      component: paragraph("Booking destination"),
    }),
  ]);
}

function renderShell(initialPath: string, routeTree: AnyRoute = chromeRoutes()) {
  const router = createRouter({
    context: {},
    routeTree,
    history: createMemoryHistory({ initialEntries: [initialPath] }),
  });

  const queryClient = testQueryClient();
  queryClient.setQueryData(authQueryKeys.oauthToken(true), "test-token");
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

beforeEach(() => {
  routerState.isLoading = false;
});

describe("disabled Booking shell", () => {
  it.each([false, true, "unavailable"] as const)(
    "waits for the actual flag, then gates both content and sidebar: %s",
    async (enabled) => {
      const flag = Promise.withResolvers<void>();
      server.use(
        http.get("/api/v2/feature-flags", async () => {
          await flag.promise;
          if (enabled === "unavailable") return new HttpResponse(null, { status: 403 });
          return HttpResponse.json({
            docs: [
              { name: "bookingEnabled", value: enabled, baselineValue: enabled, source: "DEFAULT", canOverride: false },
            ],
            totalDocs: 1,
            limit: 100,
            page: 1,
            pagingCounter: 1,
            totalPages: 1,
            hasPrevPage: false,
            hasNextPage: false,
            prevPage: null,
            nextPage: null,
          });
        }),
      );
      const router = renderShell("/booking", bookingRoutes());
      const navigate = vi.spyOn(router, "navigate").mockResolvedValue(undefined);
      try {
        expect(await screen.findByRole("status")).toHaveTextContent("common:loading");
        expect(screen.queryByText("Booking destination")).not.toBeInTheDocument();
        expect(screen.queryByRole("status", { name: "Route sidebar state" })).not.toBeInTheDocument();
        expect(navigate).not.toHaveBeenCalled();
        flag.resolve();
        if (enabled === true) {
          expect(await screen.findByText("Booking destination")).toBeVisible();
          expect(screen.getByRole("status", { name: "Route sidebar state" })).toBeVisible();
          expect(navigate).not.toHaveBeenCalled();
        } else {
          await waitFor(() =>
            expect(navigate).toHaveBeenCalledWith({
              from: "/booking",
              href: "/workspace",
              reloadDocument: true,
              replace: true,
            }),
          );
          expect(screen.queryByText("Booking destination")).not.toBeInTheDocument();
          expect(screen.queryByRole("status", { name: "Route sidebar state" })).not.toBeInTheDocument();
        }
      } finally {
        flag.resolve();
        navigate.mockRestore();
      }
    },
  );
});

describe("route chrome", () => {
  it("inherits the parent app bar and sidebar on a nested route", async () => {
    renderShell("/with-sidebar/details");

    expect(await screen.findByText("Nested destination")).toBeVisible();
    expect(screen.getByRole("banner")).toHaveTextContent("workspace");
    expect(screen.getByRole("status", { name: "Route sidebar state" })).toHaveTextContent("expanded");
  });

  it("uses child app bar and sidebar overrides", async () => {
    renderShell("/with-sidebar/override");

    expect(await screen.findByText("Override destination")).toBeVisible();
    expect(screen.getByRole("banner")).toHaveTextContent("gallery");
    expect(screen.getByText("Child sidebar")).toBeVisible();
    expect(screen.queryByRole("status", { name: "Route sidebar state" })).not.toBeInTheDocument();
  });

  it("allows a child route to hide its inherited app bar", async () => {
    renderShell("/with-sidebar/without-app-bar");

    expect(await screen.findByText("App bar hidden")).toBeVisible();
    expect(screen.queryByRole("banner")).not.toBeInTheDocument();
    expect(screen.getByRole("status", { name: "Route sidebar state" })).toBeVisible();
  });

  it("defaults to an RSpace app bar without a route sidebar", async () => {
    renderShell("/plain");

    expect(await screen.findByText(plainRouteText)).toBeVisible();
    expect(screen.getByRole("banner")).toHaveTextContent("rspace");
    expect(screen.queryByRole("status", { name: "Route sidebar state" })).not.toBeInTheDocument();
  });

  it("keeps inherited chrome on a nested not-found route", async () => {
    renderShell("/with-sidebar/missing");

    expect(await screen.findByText("Page not found")).toBeVisible();
    expect(screen.getByRole("banner")).toHaveTextContent("workspace");
    expect(screen.getByRole("status", { name: "Route sidebar state" })).toBeVisible();
  });

  it("uses the default chrome on a root not-found route", async () => {
    renderShell("/missing");

    expect(await screen.findByText("Page not found")).toBeVisible();
    expect(screen.getByRole("banner")).toHaveTextContent("rspace");
    expect(screen.queryByRole("status", { name: "Route sidebar state" })).not.toBeInTheDocument();
  });

  it.each(["/with-sidebar/failed-loader", "/with-sidebar/failed-guard/details"])(
    "keeps inherited chrome when loading %s fails",
    async (path) => {
      const restoreConsole = silenceConsole(
        ["warn", "error"],
        ["Route loader failed", "Route guard failed", "Warning: Error in route match:"],
      );
      try {
        renderShell(path);

        expect(await screen.findByText("Route failed")).toBeVisible();
        expect(screen.getByRole("banner")).toHaveTextContent("workspace");
        expect(screen.getByRole("status", { name: "Route sidebar state" })).toBeVisible();
      } finally {
        restoreConsole();
      }
    },
  );
});

describe("route sidebar state", () => {
  it.each(["/plain", "/public", "/without-app-bar"] as const)("mounts feature flag devtools on %s", async (path) => {
    renderShell(path, devtoolsRoutes());

    expect(await screen.findByRole("status", { name: "Feature flag devtools" })).toBeVisible();
  });

  it("opens on entry and removes the empty sidebar when leaving", async () => {
    const router = renderShell("/plain");
    expect(await screen.findByText(plainRouteText)).toBeVisible();
    expect(screen.queryAllByRole("button", { name: "common:sidebar.toggle" })).toHaveLength(0);

    await act(() => router.history.push("/with-sidebar"));

    expect(await screen.findByRole("status", { name: "Route sidebar state" })).toHaveTextContent("expanded");
    expect(screen.getAllByRole("button", { name: "common:sidebar.toggle" })).not.toHaveLength(0);

    await act(() => router.history.push("/plain"));

    expect(await screen.findByText(plainRouteText)).toBeVisible();
    expect(screen.queryByRole("status", { name: "Route sidebar state" })).not.toBeInTheDocument();
    expect(screen.queryAllByRole("button", { name: "common:sidebar.toggle" })).toHaveLength(0);
  });

  it("hides the mobile sidebar sheet when a navigation starts", async () => {
    const desktopWidth = window.innerWidth;
    window.innerWidth = 500;
    const user = userEvent.setup();
    try {
      const router = renderShell("/with-sidebar");
      expect(await screen.findByText(sidebarRouteText)).toBeVisible();

      await user.click(screen.getByRole("button", { name: "common:sidebar.toggle" }));
      expect(await screen.findByRole("dialog")).toBeVisible();

      await act(() => router.history.push("/with-sidebar?page=2"));

      await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    } finally {
      window.innerWidth = desktopWidth;
    }
  });
});

describe("RouteTransitionIndicator", () => {
  it("is visible while the router is loading", () => {
    routerState.isLoading = true;

    const { indicator } = renderIndicator();

    expect(indicator).toHaveAttribute("aria-hidden", "false");
  });

  it("ignores an unmarked query", async () => {
    const request = Promise.withResolvers<string>();
    const { indicator, queryClient } = renderIndicator();

    const fetchPromise = queryClient.fetchQuery({ queryKey: ["unmarked"], queryFn: () => request.promise });
    await waitFor(() => expect(queryClient.isFetching()).toBe(1));

    expect(indicator).toHaveAttribute("aria-hidden", "true");

    request.resolve("done");
    await fetchPromise;
  });

  it("stays visible until all marked queries settle", async () => {
    const firstRequest = Promise.withResolvers<string>();
    const secondRequest = Promise.withResolvers<string>();
    const { indicator, queryClient } = renderIndicator();

    const firstFetch = queryClient.fetchQuery({
      queryKey: ["marked", "first"],
      queryFn: () => firstRequest.promise,
      meta: navigationProgressQueryMeta,
    });
    const secondFetch = queryClient.fetchQuery({
      queryKey: ["marked", "second"],
      queryFn: () => secondRequest.promise,
      meta: navigationProgressQueryMeta,
    });
    await waitFor(() => expect(queryClient.isFetching(navigationProgressQueryFilters)).toBe(2));
    expect(indicator).toHaveAttribute("aria-hidden", "false");

    firstRequest.resolve("done");
    await firstFetch;
    await waitFor(() => expect(queryClient.isFetching(navigationProgressQueryFilters)).toBe(1));
    expect(indicator).toHaveAttribute("aria-hidden", "false");

    secondRequest.resolve("done");
    await secondFetch;
    await waitFor(() => expect(indicator).toHaveAttribute("aria-hidden", "true"));
  });
});
