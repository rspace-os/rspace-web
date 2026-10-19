import { createRouter } from "@tanstack/react-router";
import { routeTree } from "@/routeTree.gen";

export const router = createRouter({
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
