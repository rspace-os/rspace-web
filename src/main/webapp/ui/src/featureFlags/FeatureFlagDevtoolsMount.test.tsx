import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from "@tanstack/react-router";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { expect, test, vi } from "vitest";
import { currentUserResponse } from "@/__tests__/helpers/currentUserResponse";
import { server } from "@/__tests__/mswServer";
import AppShell from "@/modules/common/app/AppShell";
import FeatureFlagDevtoolsMount from "./FeatureFlagDevtoolsMount";

vi.mock("@/modules/common/app/AppBar", () => ({ default: () => null, PublicAppBar: () => null }));

vi.mock("./FeatureFlagDevtools", () => ({
  default: () => <output aria-label="Devtools" />,
}));

test.each(["true", "false", undefined])("legacy page capability attribute %s gates requests", async (attribute) => {
  const mint = vi.fn(() => HttpResponse.json({ accessToken: "mount-test-token" }));
  const me = vi.fn(() =>
    HttpResponse.json({ ...currentUserResponse, session: { ...currentUserResponse.session, canUseDevtools: true } }),
  );
  server.use(http.post("/api/v2/oauth/tokens", mint), http.get("/api/v2/users/me", me));
  const element = document.createElement("div");
  if (attribute !== undefined) element.dataset.canUseDevtools = attribute;
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <FeatureFlagDevtoolsMount canUseDevtools={element.dataset.canUseDevtools === "true"} />
    </QueryClientProvider>,
  );
  if (attribute === "true") {
    expect(await screen.findByRole("status", { name: "Devtools" })).toBeInTheDocument();
    expect(me).toHaveBeenCalledOnce();
  } else {
    expect(client.isFetching()).toBe(0);
    expect(mint).not.toHaveBeenCalled();
    expect(me).not.toHaveBeenCalled();
  }
});

test("app shell keeps resolving eligibility without a JSP attribute", async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  server.use(
    http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "shell-test-token" })),
    http.get("/api/v2/users/me", () =>
      HttpResponse.json({ ...currentUserResponse, session: { ...currentUserResponse.session, canUseDevtools: true } }),
    ),
  );
  const root = createRootRoute({ component: AppShell, notFoundComponent: () => null });
  const router = createRouter({ routeTree: root, history: createMemoryHistory({ initialEntries: ["/"] }) });
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  expect(await screen.findByRole("status", { name: "Devtools" })).toBeInTheDocument();
});
