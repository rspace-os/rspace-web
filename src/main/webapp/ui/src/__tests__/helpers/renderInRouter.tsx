import { type AnyRoute, createMemoryHistory, createRouter, RouterProvider } from "@tanstack/react-router";
import { type RenderResult, render } from "@testing-library/react";
import type { ReactElement } from "react";

type RenderUi = (ui: ReactElement) => RenderResult | Promise<RenderResult>;

export async function renderInRouter(routeTree: AnyRoute, renderUi: RenderUi = render) {
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: ["/"] }),
  });
  return { ...(await renderUi(<RouterProvider router={router} />)), router };
}
