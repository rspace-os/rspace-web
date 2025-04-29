//@flow strict

import Grid from "@mui/material/Grid";
import React from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import GoogleDriveIcon from "../../../assets/branding/googledrive/logo.svg";
import { LOGO_COLOR } from "../../../assets/branding/googledrive";

type GoogleDriveArgs = {
  integrationState: IntegrationStates["GOOGLEDRIVE"];
  update: (newIntegrationState: IntegrationStates["GOOGLEDRIVE"]) => void;
};

/*
 * Authentication with Google happens when the user uses the dialog on the document editor page.
 */
function GoogleDrive({
  integrationState,
  update,
}: GoogleDriveArgs): React.ReactNode {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Google Drive"
        integrationState={integrationState}
        explanatoryText="Create, organise, and share on your files through a collaborative cloud-based service."
        image={GoogleDriveIcon}
        color={LOGO_COLOR}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        usageText="You can include files from Google Drive in your RSpace documents. Files are embedded as links to the Google Drive location of that file."
        helpLinkText="Cloud Storage integrations docs"
        website="drive.google.com"
        docLink="cloudstorage"
        setupSection={
          <ol>
            <li>Enable the integration.</li>
            <li>
              When editing a document, click on the Google Drive icon in the
              text editor toolbar.
            </li>
          </ol>
        }
      />
    </Grid>
  );
}

export default React.memo(GoogleDrive);
