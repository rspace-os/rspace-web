import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "../../../assets/branding/argos";
import ArgosIcon from "../../../assets/branding/argos/logo.svg";
import IntegrationCard from "../IntegrationCard";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type ArgosArgs = {
  integrationState: IntegrationStates["ARGOS"];
  update: (newIntegrationState: IntegrationStates["ARGOS"]) => void;
};

/*
 * There is no authentication mechanism with Argos. All DMPs are public and by
 * simply enabling the integration users can import those DMPs into the Gallery
 */
function Argos({ integrationState, update }: ArgosArgs): React.ReactNode {
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
        website="argos.openaire.eu"
        docLink="argos"
        setupSection={
          <ol>
            <li>{t("integrations.argos.setup.enable")}</li>
            <li>{t("integrations.argos.setup.imported")}</li>
          </ol>
        }
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        integrationState={integrationState}
      />
    </Grid>
  );
}

export default React.memo(observer(Argos));
