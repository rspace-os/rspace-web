import { type AnyRouter, createRouter } from "@tanstack/react-router";
import { type ApiV2TokenProvider, SessionTokenProvider } from "@/modules/common/api/v2/tokenProvider";
import { routeTree } from "@/routeTree.gen";
import type { AppBarRouteContext } from "./AppShell";
import { appShellSessionHandlers } from "./sessionHandlers";

export type AppRouterContext = AppBarRouteContext & { tokenProvider: ApiV2TokenProvider };

// The session handlers read the router's matches only when a session ends, after the router below exists.
const shell: Pick<AnyRouter, "state"> = {
  get state() {
    return router.state;
  },
};

export const router = createRouter({
  context: { tokenProvider: new SessionTokenProvider(appShellSessionHandlers(shell)) },
  routeTree,
  basepath: document.getElementById("app")?.dataset.contextPath || "/",
  defaultPreload: "intent",
  defaultStaleReloadMode: "blocking",
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
