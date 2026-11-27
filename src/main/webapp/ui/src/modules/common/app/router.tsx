import type { QueryClient } from "@tanstack/react-query";
import { type AnyRouter, createRouter } from "@tanstack/react-router";
import { type ApiV2TokenProvider, SessionTokenProvider } from "@/modules/common/api/v2/tokenProvider";
import { routeTree } from "@/routeTree.gen";
import type { AppBarRouteContext } from "./AppShell";
import { appShellSessionHandlers } from "./sessionHandlers";

export type AppRouterContext = AppBarRouteContext & { queryClient: QueryClient; tokenProvider: ApiV2TokenProvider };

export const createAppRouter = (queryClient: QueryClient) => {
  // The session handlers read the router's matches only when a session ends, after the router below exists.
  const shell: Pick<AnyRouter, "state"> = {
    get state() {
      return router.state;
    },
  };
  const router = createRouter({
    context: { queryClient, tokenProvider: new SessionTokenProvider(appShellSessionHandlers(shell)) },
    routeTree,
    basepath: document.getElementById("app")?.dataset.contextPath || "/",
    defaultPreload: "intent",
    defaultPreloadStaleTime: 0,
    defaultStaleReloadMode: "blocking",
  });
  return router;
};

declare module "@tanstack/react-router" {
  interface Register {
    router: ReturnType<typeof createAppRouter>;
  }
}
