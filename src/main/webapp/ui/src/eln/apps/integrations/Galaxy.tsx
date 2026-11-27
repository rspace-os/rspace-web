import Grid from "@mui/material/Grid";
import React from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "@/assets/branding/galaxy";
import TransRichText from "@/modules/common/i18n/TransRichText";
import GalaxyIcon from "../../../assets/branding/galaxy/logo.svg";
import IntegrationCard from "../IntegrationCard";
import { useIntegrationModeUpdate, useIntegrationState } from "../queries";
import ApiKeyServers from "./ApiKeyServers";

/*
 * Galaxy uses API-key based authentication, as implemented by the form below.
 */
function Galaxy(): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const integrationState = useIntegrationState("GALAXY");
  const updateMode = useIntegrationModeUpdate("GALAXY");

  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.galaxy.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.galaxy.description")}
        image={GalaxyIcon}
        color={LOGO_COLOR}
        usageText={t("integrations.galaxy.usage")}
        helpLinkText={t("integrations.galaxy.helpLink")}
        website="https://galaxyproject.org"
        docLink="galaxy"
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.galaxy.setup.instructions" />
            <ApiKeyServers
              integration="GALAXY"
              messages={{
                noServers: t("integrations.galaxy.noServers"),
                apiKeyLabel: (alias) => t("integrations.galaxy.apiKeyLabel", { alias }),
                saveSuccess: t("integrations.galaxy.alerts.saveSuccess"),
                saveError: t("integrations.galaxy.alerts.saveError"),
                deleteSuccess: t("integrations.galaxy.alerts.deleteSuccess"),
                deleteError: t("integrations.galaxy.alerts.deleteError"),
                addSuccess: t("integrations.galaxy.alerts.addSuccess"),
                addError: t("integrations.galaxy.alerts.addError"),
              }}
            />
          </>
        }
        update={updateMode}
      />
    </Grid>
  );
}

export default React.memo(Galaxy);
