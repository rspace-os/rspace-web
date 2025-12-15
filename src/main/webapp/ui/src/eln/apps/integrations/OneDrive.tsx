import Grid from "@mui/material/Grid";
import React from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import OneDriveIcon from "../../../assets/branding/onedrive/logo.svg";
import { LOGO_COLOR } from "../../../assets/branding/onedrive";

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
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="OneDrive"
        integrationState={integrationState}
        explanatoryText="Store, share, and sync your files with a file hosting service integrated with Microsoft 365."
        image={OneDriveIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        usageText="You can include files from OneDrive in your RSpace documents. Files are embedded as links to the OneDrive location of that file."
        helpLinkText="Cloud Storage integrations docs"
        website="onedrive.live.com"
        docLink="cloudstorage"
        setupSection={
          <ol>
            <li>Enable the integration.</li>
            <li>
              When editing a document, click on the OneDrive icon in the text
              editor toolbar.
            </li>
          </ol>
        }
      />
    </Grid>
  );
}

export default React.memo(OneDrive);
