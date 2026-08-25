import Grid from "@mui/material/Grid";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/dbrepo";
import DBRepoIcon from "../../../assets/branding/dbrepo/logo.svg";
import IntegrationCard from "../IntegrationCard";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type DBRepoArgs = {
  integrationState: IntegrationStates["DBREPO"];
  update: (newIntegrationState: IntegrationStates["DBREPO"]) => void;
};

/*
 * DBRepo integration - empty scaffold. Replace placeholder catalog text before release.
 */
function DBRepo({ integrationState, update }: DBRepoArgs): React.ReactNode {
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
        name={t("integrations.dbrepo.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.dbrepo.description")}
        image={DBRepoIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        usageText={t("integrations.dbrepo.usage")}
        helpLinkText={t("integrations.dbrepo.helpLink")}
        website="https://dbrepo-project.github.io/dbrepo-docs/latest/"
        docLink="dbrepo"
        setupSection={<TransRichText i18nKey="apps:integrations.dbrepo.setup.instructions" />}
      />
    </Grid>
  );
}

export default React.memo(DBRepo);
