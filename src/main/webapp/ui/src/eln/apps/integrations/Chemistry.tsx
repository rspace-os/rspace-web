import Grid from "@mui/material/Grid";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/chemistry";
import ChemistryIcon from "../../../assets/branding/chemistry/logo.svg";
import IntegrationCard from "../IntegrationCard";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type ChemistryArgs = {
  integrationState: IntegrationStates["CHEMISTRY"];
  update: (newIntegrationState: IntegrationStates["CHEMISTRY"]) => void;
};

/*
 * There is no authentication mechanism with Chemistry. All users can
 * enable the integration to be able to use it when editing a document.
 */
function Chemistry({ integrationState, update }: ChemistryArgs): React.ReactNode {
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
        name={t("integrations.chemistry.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.chemistry.description")}
        image={ChemistryIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        usageText={t("integrations.chemistry.usage")}
        helpLinkText={t("integrations.chemistry.helpLink")}
        docLink="wfxm4xwtio-chemistry-integration"
        setupSection={<TransRichText i18nKey="apps:integrations.chemistry.setup.instructions" />}
      />
    </Grid>
  );
}

export default React.memo(Chemistry);
