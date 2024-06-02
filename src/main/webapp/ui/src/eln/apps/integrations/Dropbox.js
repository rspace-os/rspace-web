//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import DropboxIcon from "../icons/Dropbox.svg";

type DropboxArgs = {|
  integrationState: IntegrationStates["DROPBOX"],
  update: (IntegrationStates["DROPBOX"]) => void,
|};

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
function Dropbox({ integrationState, update }: DropboxArgs): Node {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Dropbox"
        integrationState={integrationState}
        explanatoryText="Store, access, and share your files across devices with others through online cloud storage."
        image={DropboxIcon}
        color={{
          hue: 217,
          saturation: 100,
          lightness: 50,
        }}
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        usageText="You can include files from Dropbox in your RSpace documents. Files are embedded as links to the Dropbox location of that file."
        helpLinkText="Cloud Storage integrations docs"
        website="dropbox.com"
        docLink="cloudstorage"
        setupSection={
          <ol>
            <li>Enable the integration.</li>
            <li>
              When editing a document, click on the Dropbox icon in the text
              editor toolbar.
            </li>
          </ol>
        }
      />
    </Grid>
  );
}

export default (React.memo(Dropbox): AbstractComponent<DropboxArgs>);
