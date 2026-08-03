import * as React from "react";
import { useTranslation } from "react-i18next";
import RSpaceLogo from "@/assets/branding/rspace/logo.svg";
import ErrorBoundary from "@/components/ErrorBoundary";
import { DEFAULT_APP_CONFIG, useAppConfigQuery } from "@/modules/common/app/queries/config";
import { helpDocsArticleUrl } from "@/modules/common/i18n/TransRichText";
import { useApplicationVersionQuery } from "@/modules/common/queries/applicationVersion";
import { Card, CardContent } from "@/modules/common/ui/card";
import { Separator } from "@/modules/common/ui/separator";
import { Heading, Link, List, Text } from "@/modules/common/ui/typography";

const SUPPORT_EMAIL = "support@researchspace.com";

function VersionBlock() {
  const { t } = useTranslation("about");
  const version = useApplicationVersionQuery();
  return (
    <Text as="h2" variant="muted">
      {t("version.label", { version: version.data })}
    </Text>
  );
}

export default function AboutPanel() {
  const { t } = useTranslation("about");
  const { data: config = DEFAULT_APP_CONFIG } = useAppConfigQuery();

  return (
    <div className="container max-w-4xl">
      <Card>
        <CardContent className="flex flex-col gap-5 lg:flex-row">
          <div className="lg:basis-1/3">
            <img src={RSpaceLogo} alt={t("logo.alt")} className="w-full max-h-32 lg:max-h-64" />
          </div>

          <div className="w-0.75 hidden lg:block">
            <Separator orientation="vertical" className="h-full" />
          </div>

          <div className="flex flex-col gap-2">
            <div>
              <Heading level={3} as="h1" className="lg:text-3xl">
                {t("tagline")}
              </Heading>
              <ErrorBoundary message={t("version.unavailable")}>
                <React.Suspense fallback={<Text variant="muted">{t("version.loading")}</Text>}>
                  <VersionBlock />
                </React.Suspense>
              </ErrorBoundary>
              {config.deploymentDescription ? <Text>{config.deploymentDescription}</Text> : null}
            </div>
            <Separator />
            <div>
              <Heading level={6} as="h2">
                {t("support.heading")}
              </Heading>
              <List>
                <li>
                  <Link href={`mailto:${SUPPORT_EMAIL}`}>{t("support.generalLink")}</Link>
                </li>
                {config.deploymentHelpEmail ? (
                  <li>
                    <Link href={`mailto:${config.deploymentHelpEmail}`}>{t("support.accountsLink")}</Link>
                  </li>
                ) : null}
              </List>
            </div>
            <Text>{t("description")}</Text>

            <div>
              <Heading level={6} as="h2">
                {t("licensing.heading")}
              </Heading>
              <Text>{t("license")}</Text>

              <List>
                <li>
                  <Link href="https://researchspace.com">{t("links.website")}</Link>
                </li>
                <li>
                  <Link href={helpDocsArticleUrl("changelog")}>{t("links.changelog")}</Link>
                </li>
                <li>
                  <Link href="https://github.com/rspace-os">{t("links.sourceCode")}</Link>
                </li>
              </List>
            </div>
            <Separator />
            <Text variant="muted">{t("copyright")}</Text>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
