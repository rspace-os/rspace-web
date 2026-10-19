import { createRouter } from "@tanstack/react-router";
import { routeTree } from "@/routeTree.gen";

export const router = createRouter({
  routeTree,
  basepath: document.getElementById("app")?.dataset.contextPath || "/",
  defaultPreload: "intent",
  defaultViewTransition: true,
  defaultStaleReloadMode: "blocking",
});

router.subscribe("onBeforeNavigate", ({ hashChanged, hrefChanged, pathChanged }) => {
  if (hrefChanged && !pathChanged && !hashChanged) router.shouldViewTransition = false;
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
