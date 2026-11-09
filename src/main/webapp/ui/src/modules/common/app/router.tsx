import type { QueryClient } from "@tanstack/react-query";
import { createRouter } from "@tanstack/react-router";
import { routeTree } from "@/routeTree.gen";
import type { AppBarRouteContext } from "./AppShell";

export type AppRouterContext = AppBarRouteContext & { queryClient: QueryClient };

export const createAppRouter = (queryClient: QueryClient) =>
  createRouter({
    context: { queryClient },
    routeTree,
    basepath: document.getElementById("app")?.dataset.contextPath || "/",
    defaultPreload: "intent",
    defaultPreloadStaleTime: 0,
    defaultStaleReloadMode: "blocking",
  });

declare module "@tanstack/react-router" {
  interface Register {
    router: ReturnType<typeof createAppRouter>;
  }
}
