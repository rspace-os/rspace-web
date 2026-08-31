import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from "@tanstack/react-router";
import { act, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AppShell, { getAppBarConfig, getSidebarRenderer, RouteTransitionIndicator } from "@/modules/common/app/AppShell";
import { viewTransitionQueryFilters, viewTransitionQueryMeta } from "@/modules/common/queries/viewTransition";
import { useSidebar } from "@/modules/common/ui/sidebar";

const routerState = vi.hoisted(() => ({ isLoading: false }));

vi.mock("@tanstack/react-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@tanstack/react-router")>();
  return { ...actual, useRouterState: () => routerState.isLoading };
});

vi.mock("@/modules/common/app/AppBar", () => {
  const AppBar = ({ renderHamburger }: { renderHamburger?: () => ReactNode }) => <header>{renderHamburger?.()}</header>;
  return { default: AppBar, PublicAppBar: AppBar };
});

vi.mock("@/modules/common/stores/userSessionStore", () => ({
  UserSessionBootstrap: () => null,
}));

vi.mock("@/featureFlags/FeatureFlagDevtoolsMount", () => ({ default: () => null }));

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

function renderShell(initialPath: "/plain" | "/with-sidebar") {
  const rootRoute = createRootRoute({ component: AppShell });
  const plainRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/plain",
    beforeLoad: () => ({ appBar: { currentPage: "rspace" } }),
    component: () => <p>{plainRouteText}</p>,
  });
  const sidebarRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/with-sidebar",
    beforeLoad: () => ({ appBar: { currentPage: "rspace" }, sidebar: renderTestSidebar }),
    component: () => <p>{sidebarRouteText}</p>,
  });
  const router = createRouter({
    routeTree: rootRoute.addChildren([plainRoute, sidebarRoute]),
    history: createMemoryHistory({ initialEntries: [initialPath] }),
  });

  render(
    <QueryClientProvider client={testQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

beforeEach(() => {
  routerState.isLoading = false;
});

describe("getAppBarConfig", () => {
  it("uses the deepest matched route app bar config", () => {
    expect(
      getAppBarConfig([
        { context: { appBar: { currentPage: "Root" } } },
        { context: { appBar: { currentPage: "booking" } } },
      ]),
    ).toEqual({ currentPage: "booking" });
  });

  it("allows a route to opt out of the app bar", () => {
    expect(getAppBarConfig([{ context: { appBar: { currentPage: "Root" } } }, { context: { appBar: false } }])).toBe(
      false,
    );
  });

  it("defaults to a generic RSpace app bar", () => {
    expect(getAppBarConfig([{ context: {} }])).toEqual({ currentPage: "rspace" });
  });
});

describe("getSidebarRenderer", () => {
  it("uses the deepest matched route sidebar", () => {
    const rootSidebar = () => null;
    const childSidebar = () => null;

    expect(getSidebarRenderer([{ context: { sidebar: rootSidebar } }, { context: { sidebar: childSidebar } }])).toBe(
      childSidebar,
    );
  });

  it("returns undefined when no route declares a sidebar", () => {
    expect(getSidebarRenderer([{ context: { appBar: { currentPage: "rspace" } } }])).toBeUndefined();
  });
});

describe("route sidebar state", () => {
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
      meta: viewTransitionQueryMeta,
    });
    const secondFetch = queryClient.fetchQuery({
      queryKey: ["marked", "second"],
      queryFn: () => secondRequest.promise,
      meta: viewTransitionQueryMeta,
    });
    await waitFor(() => expect(queryClient.isFetching(viewTransitionQueryFilters)).toBe(2));
    expect(indicator).toHaveAttribute("aria-hidden", "false");

    firstRequest.resolve("done");
    await firstFetch;
    await waitFor(() => expect(queryClient.isFetching(viewTransitionQueryFilters)).toBe(1));
    expect(indicator).toHaveAttribute("aria-hidden", "false");

    secondRequest.resolve("done");
    await secondFetch;
    await waitFor(() => expect(indicator).toHaveAttribute("aria-hidden", "true"));
  });
});
