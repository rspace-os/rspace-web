//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import { observer } from "mobx-react-lite";
import EvernoteIcon from "../../../assets/branding/evernote/logo.svg";
import { LOGO_COLOR } from "../../../assets/branding/evernote";

type EvernoteArgs = {|
  integrationState: IntegrationStates["EVERNOTE"],
  update: (IntegrationStates["EVERNOTE"]) => void,
|};

/*
 * There is no authentication mechanism with Evernote. All users can use it to
 * import documents into RSpace.
 */
function Evernote({ integrationState, update }: EvernoteArgs): Node {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Evernote"
        integrationState={integrationState}
        explanatoryText="Take notes, manage tasks, and organise your notes into notebooks with embedded media."
        image={EvernoteIcon}
        color={LOGO_COLOR}
        usageText="You can directly import Evernote XML exports into RSpace. The import creates a separate RSpace document for each Note, and images and attachments will also be imported."
        helpLinkText="Evernote integration docs"
        website="evernote.com"
        docLink="evernote"
        setupSection={
          <ol>
            <li>Enable the integration.</li>
            <li>In the Workspace, select Create → Import from Evernote.</li>
          </ol>
        }
        update={(newMode) => update({ mode: newMode, credentials: {} })}
      />
    </Grid>
  );
}

export default (React.memo(
  observer(Evernote)
): AbstractComponent<EvernoteArgs>);
