import Grid from "@mui/material/Grid";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/onedrive";
import OneDriveIcon from "../../../assets/branding/onedrive/logo.svg";
import IntegrationCard from "../IntegrationCard";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type OneDriveArgs = {
  integrationState: IntegrationStates["ONEDRIVE"];
  update: (newIntegrationState: IntegrationStates["ONEDRIVE"]) => void;
};

/*
 * Note that authentication with microsoft is performed when the user goes to
 * use the integration by importing a file into their RSpace document.
 * The /integrations API does return a "onedrive.linking.enabled" boolean,
 * which a setting configured by the sysadmin, but it is not parsed out by
 * useIntegrationsEndpoint because it is not used by any of this code. Even if
 * the user has enabled the integration, if the sysadmin has set this flag to
 * false then the import file button will not be available in the RSpace
 * document editor.
 */
function OneDrive({ integrationState, update }: OneDriveArgs): React.ReactNode {
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
        name={t("integrations.oneDrive.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.oneDrive.description")}
        image={OneDriveIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        usageText={t("integrations.oneDrive.usage")}
        helpLinkText={t("integrations.oneDrive.helpLink")}
        website="onedrive.live.com"
        docLink="cloudstorage"
        setupSection={<TransRichText i18nKey="apps:integrations.oneDrive.setup.instructions" />}
      />
    </Grid>
  );
}

export default React.memo(OneDrive);
