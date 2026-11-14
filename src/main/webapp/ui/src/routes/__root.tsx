import { createRootRouteWithContext } from "@tanstack/react-router";
import AppShell from "@/modules/common/app/AppShell";
import type { AppRouterContext } from "@/modules/common/app/router";
import NotFoundPage from "@/modules/common/pages/notFound/NotFoundPage";

export const Route = createRootRouteWithContext<AppRouterContext>()({
  component: AppShell,
  notFoundComponent: NotFoundPage,
  head: () => ({
    meta: [{ title: "RSpace" }],
  }),
});
