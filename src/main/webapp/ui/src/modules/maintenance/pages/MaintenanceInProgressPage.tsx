import { type AnyRoute, createRoute } from "@tanstack/react-router";
import * as React from "react";
import { useTranslation } from "react-i18next";
import RSpaceLogo from "@/assets/branding/rspace/logo.svg";
import i18n from "@/modules/common/i18n";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { Card, CardContent } from "@/modules/common/ui/card";
import { Heading, Text } from "@/modules/common/ui/typography";
import { useMaintenanceStatusQuery } from "@/modules/maintenance/queries";

export default function MaintenanceInProgressPage() {
  const { t } = useTranslation("common");
  const { data: status } = useMaintenanceStatusQuery();

  React.useEffect(() => {
    if (status === "clear") {
      window.location.href = "/login";
    }
  }, [status]);

  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <Card className="w-full max-w-md">
        <CardContent className="flex flex-col items-center gap-4 py-2 text-center">
          <img src={RSpaceLogo} alt={t("helpDocs.rspaceAlt")} className="h-16 w-16" />
          <Heading level={2} as="h1">
            {t("maintenanceMode.heading")}
          </Heading>
          <Text variant="muted">{t("maintenanceMode.description")}</Text>
          <Text variant="muted">
            <TransRichText i18nKey="common:maintenanceMode.recheckNotice" />
          </Text>
        </CardContent>
      </Card>
    </main>
  );
}

export function createMaintenanceInProgressRoute<TParentRoute extends AnyRoute>(rootRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => rootRoute,
    path: "/public/maintenanceInProgress",
    beforeLoad: () => ({ appBar: false as const }),
    head: () => ({
      // `common` loads eagerly at i18next init, so this synchronous lookup is safe
      // outside the component tree; other namespaces load lazily and would return the raw key here.
      meta: [
        {
          title: i18n.t("common:pageTitles.withProduct", {
            pageTitle: i18n.t("common:maintenanceMode.heading"),
          }),
        },
      ],
    }),
    component: MaintenanceInProgressPage,
  });
}
