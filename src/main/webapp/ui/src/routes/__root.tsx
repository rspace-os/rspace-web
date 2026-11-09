import { createRootRouteWithContext } from "@tanstack/react-router";
import AppShell, { type AppBarRouteContext } from "@/modules/common/app/AppShell";
import NotFoundPage from "@/modules/common/pages/notFound/NotFoundPage";

export const Route = createRootRouteWithContext<AppBarRouteContext>()({
  component: AppShell,
  notFoundComponent: NotFoundPage,
  head: () => ({
    meta: [{ title: "RSpace" }],
  }),
});
