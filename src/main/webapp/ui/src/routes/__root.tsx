import { createRootRouteWithContext } from "@tanstack/react-router";
import AppShell, { type AppBarRouteContext } from "@/modules/common/app/AppShell";
import { LIGHTHOUSE_SCRIPT_ID, LIGHTHOUSE_SCRIPT_SRC } from "@/modules/common/app/lighthouse";
import NotFoundPage from "@/modules/common/pages/notFound/NotFoundPage";

export const Route = createRootRouteWithContext<AppBarRouteContext>()({
  component: AppShell,
  notFoundComponent: NotFoundPage,
  head: () => ({
    meta: [{ title: "RSpace" }],
    scripts: [
      {
        id: LIGHTHOUSE_SCRIPT_ID,
        type: "text/javascript",
        async: true,
        defer: true,
        src: LIGHTHOUSE_SCRIPT_SRC,
      },
    ],
  }),
});
