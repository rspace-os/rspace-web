import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRouteWithContext,
  createRoute,
  createRouter,
  RouterProvider,
} from "@tanstack/react-router";
import { render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { beforeEach, expect, it } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import { getGetCurrentUserQueryKey } from "@/modules/common/api/v2/generated/users/users";
import AppShell, { type AppBarRouteContext } from "@/modules/common/app/AppShell";
import { resetUiTokenForTests } from "@/modules/common/utils/auth";

beforeEach(() => resetUiTokenForTests());

it.each(["/about", "/about/missing"])(
  "shows the public login bar on %s after authentication fails",
  async (path) => {
    let currentUserRequests = 0;
    server.use(
      http.get("/api/v2/users/me", () => {
        currentUserRequests += 1;
        return new HttpResponse(null, { status: 401 });
      }),
    );
    const restoreConsole = silenceConsole(
      ["error", "warn"],
      ["Unauthorized", "The above error occurred", "Warning: Error in route match"],
    );
    const aboutTitle = "About";
    const missingTitle = "Page not found";
    const root = createRootRouteWithContext<AppBarRouteContext>()({
      component: AppShell,
      notFoundComponent: () => <h1>{missingTitle}</h1>,
    });
    const about = createRoute({
      getParentRoute: () => root,
      path: "/about",
      beforeLoad: () => ({ appBar: { currentPage: "aboutRSpace", authenticated: false } }) satisfies AppBarRouteContext,
      component: () => <h1>{aboutTitle}</h1>,
    });
    const router = createRouter({
      routeTree: root.addChildren([about]),
      context: {},
      history: createMemoryHistory({ initialEntries: [path] }),
    });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    try {
      render(
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>,
      );
      expect(await screen.findByRole("heading", { name: path === "/about" ? aboutTitle : missingTitle })).toBeVisible();
      expect(await screen.findByRole("link", { name: "common:appBar.logIn" })).toHaveAttribute("href", "/login");
      await waitFor(() => expect(queryClient.getQueryState(getGetCurrentUserQueryKey())?.status).toBe("error"), {
        timeout: 9000,
      });
      expect(await screen.findByRole("heading", { name: path === "/about" ? aboutTitle : missingTitle })).toBeVisible();
      expect(screen.getByRole("link", { name: "common:appBar.logIn" })).toBeVisible();
      expect(currentUserRequests).toBeGreaterThan(0);
      expect(screen.queryByText("Something went wrong!")).not.toBeInTheDocument();
    } finally {
      restoreConsole();
    }
  },
  12000,
);
