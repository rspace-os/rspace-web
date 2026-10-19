import { createFileRoute } from "@tanstack/react-router";
import i18n from "@/modules/common/i18n";
import MaintenanceInProgressPage from "@/modules/maintenance/pages/MaintenanceInProgressPage";

export const Route = createFileRoute("/public/maintenanceInProgress/")({
  beforeLoad: () => ({ appBar: false as const }),
  head: () => ({
    meta: [{ title: i18n.t("common:pageTitles.withProduct", { pageTitle: i18n.t("common:maintenanceMode.heading") }) }],
  }),
  component: MaintenanceInProgressPage,
});
