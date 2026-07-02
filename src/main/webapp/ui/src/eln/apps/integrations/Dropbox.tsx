import Grid from "@mui/material/Grid";
import React from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "../../../assets/branding/dropbox";
import DropboxIcon from "../../../assets/branding/dropbox/logo.svg";
import IntegrationCard from "../IntegrationCard";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type DropboxArgs = {
  integrationState: IntegrationStates["DROPBOX"];
  update: (newIntegrationState: IntegrationStates["DROPBOX"]) => void;
};

/*
 * Note that authentication with dropbox is performed when the user goes to
 * use the integration by inserting a file into their RSpace document.
 * The /integrations API does return a "dropbox.linking.enabled" boolean,
 * which a setting configured by the sysadmin, but it is not parsed out by
 * useIntegrationsEndpoint because it is not used by any of this code. Even if
 * the user has enabled the integration, if the sysadmin has set this flag to
 * false then the import file button will not be available in the RSpace
 * document editor.
 */
function Dropbox({ integrationState, update }: DropboxArgs): React.ReactNode {
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
        name={t("integrations.dropbox.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.dropbox.description")}
        image={DropboxIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        usageText={t("integrations.dropbox.usage")}
        helpLinkText={t("integrations.dropbox.helpLink")}
        website="dropbox.com"
        docLink="cloudstorage"
        setupSection={
          <ol>
            <li>{t("integrations.dropbox.setup.enable")}</li>
            <li>{t("integrations.dropbox.setup.toolbar")}</li>
          </ol>
        }
      />
    </Grid>
  );
}

export default React.memo(Dropbox);
