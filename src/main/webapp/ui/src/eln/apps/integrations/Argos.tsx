import Grid from "@mui/material/Grid";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/argos";
import ArgosIcon from "../../../assets/branding/argos/logo.svg";
import IntegrationCard from "../IntegrationCard";
import { useIntegrationModeUpdate, useIntegrationState } from "../queries";

/*
 * There is no authentication mechanism with Argos. All DMPs are public and by
 * simply enabling the integration users can import those DMPs into the Gallery
 */
function Argos(): React.ReactNode {
  const integrationState = useIntegrationState("ARGOS");
  const updateMode = useIntegrationModeUpdate("ARGOS");
  const { t } = useTranslation("apps");
  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.argos.name")}
        explanatoryText={t("integrations.argos.description")}
        image={ArgosIcon}
        color={LOGO_COLOR}
        usageText={t("integrations.argos.usage")}
        helpLinkText={t("integrations.argos.helpLink")}
        website="https://argos.openaire.eu"
        docLink="argos"
        setupSection={<TransRichText i18nKey="apps:integrations.argos.setup.instructions" />}
        update={updateMode}
        integrationState={integrationState}
      />
    </Grid>
  );
}

export default React.memo(Argos);
