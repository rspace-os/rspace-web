//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import JoveIcon from "../../../assets/branding/jove/logo.svg";
import { LOGO_COLOR } from "../../../assets/branding/jove";

type JoveArgs = {|
  integrationState: IntegrationStates["JOVE"],
  update: (IntegrationStates["JOVE"]) => void,
|};

/*
 * There is no authentication mechanism for Jove. All users can use it to embed
 * videos into their documents.
 */
function Jove({ integrationState, update }: JoveArgs): Node {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="JoVE"
        integrationState={integrationState}
        explanatoryText="Watch, reference, and teach research methods and technologies through detailed process videos."
        image={JoveIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        usageText="You can search for JoVE videos and insert them into your RSpace documents, directly from RSpace. The embedded videos are directly playable from the document."
        helpLinkText="JoVE integration docs"
        website="jove.com"
        docLink="jove"
        setupSection={
          <ol>
            <li>Enable the integration.</li>
            <li>
              When editing a document, click on the JoVE icon in the text editor
              toolbar to access and insert videos or articles.
            </li>
          </ol>
        }
      />
    </Grid>
  );
}

export default (React.memo(Jove): AbstractComponent<JoveArgs>);
