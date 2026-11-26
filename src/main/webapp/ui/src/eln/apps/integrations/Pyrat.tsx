import Grid from "@mui/material/Grid";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/pyrat";
import PyratIcon from "../../../assets/branding/pyrat/logo.svg";
import IntegrationCard from "../IntegrationCard";
import { useIntegrationModeUpdate, useIntegrationState } from "../queries";
import ApiKeyServers from "./ApiKeyServers";

/*
 * Pyrat uses API-key based authentication, as implemeted by the form below.
 */
function Pyrat(): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const integrationState = useIntegrationState("PYRAT");
  const updateMode = useIntegrationModeUpdate("PYRAT");

  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.pyrat.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.pyrat.description")}
        image={PyratIcon}
        color={LOGO_COLOR}
        usageText={t("integrations.pyrat.usage")}
        helpLinkText={t("integrations.pyrat.helpLink")}
        website="https://scionics.com/pyrat"
        docLink="pyrat"
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.pyrat.setup.instructions" />
            <ApiKeyServers
              integration="PYRAT"
              messages={{
                noServers: t("integrations.pyrat.noServers"),
                apiKeyLabel: (alias) => t("integrations.pyrat.apiKeyLabel", { alias }),
                saveSuccess: t("integrations.pyrat.alerts.saveSuccess"),
                saveError: t("integrations.pyrat.alerts.saveError"),
                deleteSuccess: t("integrations.pyrat.alerts.deleteSuccess"),
                deleteError: t("integrations.pyrat.alerts.deleteError"),
                addSuccess: t("integrations.pyrat.alerts.addSuccess"),
                addError: t("integrations.pyrat.alerts.addError"),
              }}
            />
          </>
        }
        update={updateMode}
      />
    </Grid>
  );
}

/**
 * The card and dialog for configuring the PyRAT integration
 */
export default React.memo(Pyrat);
