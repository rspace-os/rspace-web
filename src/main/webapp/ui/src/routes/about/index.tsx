import { createFileRoute } from "@tanstack/react-router";
import AboutPage from "@/modules/about/pages/AboutPage";
import type { AppBarRouteContext } from "@/modules/common/app/AppShell";
import i18n from "@/modules/common/i18n";

export const Route = createFileRoute("/about/")({
  beforeLoad: () => ({ appBar: { currentPage: "aboutRSpace", authenticated: false } }) satisfies AppBarRouteContext,
  head: () => ({ meta: [{ title: i18n.t("common:appBar.aboutRSpace") }] }),
  component: AboutPage,
});
