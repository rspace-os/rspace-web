import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRoute, createRouter, RouterProvider } from "@tanstack/react-router";
import { render, renderHook, screen } from "@testing-library/react";
import { afterEach, expect, it } from "vitest";
import { LIGHTHOUSE_SCRIPT_ID, LIGHTHOUSE_SCRIPT_SRC, useLighthouseSdk } from "@/modules/common/app/lighthouse";
import { Route } from "@/routes/__root";

afterEach(() => document.getElementById(LIGHTHOUSE_SCRIPT_ID)?.remove());

it("does not load Lighthouse on a public route without a help menu", async () => {
  const publicTitle = "API docs";
  const publicRoute = createRoute({
    getParentRoute: () => Route,
    path: "/public/apiDocs",
    beforeLoad: () => ({ appBar: false as const }),
    component: () => <h1>{publicTitle}</h1>,
  });
  const router = createRouter({
    routeTree: Route.addChildren([publicRoute]),
    context: {},
    history: createMemoryHistory({ initialEntries: ["/public/apiDocs"] }),
  });
  render(
    <QueryClientProvider client={new QueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  expect(await screen.findByRole("heading", { name: "API docs" })).toBeVisible();
  expect(document.getElementById(LIGHTHOUSE_SCRIPT_ID)).not.toBeInTheDocument();
});

it("loads one SDK script when help consumers mount repeatedly", () => {
  const properties = { livechatEnabled: false };
  renderHook(() => useLighthouseSdk(properties));
  renderHook(() => useLighthouseSdk(properties));
  expect(document.getElementById(LIGHTHOUSE_SCRIPT_ID)).toHaveAttribute("src", LIGHTHOUSE_SCRIPT_SRC);
  expect(document.querySelectorAll(`script#${LIGHTHOUSE_SCRIPT_ID}`)).toHaveLength(1);
});
