//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import MendeleyIcon from "../icons/mendeley.svg";

type MendeleyArgs = {|
  integrationState: IntegrationStates["MENDELEY"],
  update: (IntegrationStates["MENDELEY"]) => void,
|};

/*
 * Note that authentication with mendeley is performaned when the user goes to
 * use the integration in the RSpace document editor.
 */
function Mendeley({ integrationState, update }: MendeleyArgs): Node {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Mendeley"
        integrationState={integrationState}
        explanatoryText="Manage and share research papers through a scholarly reference manager."
        image={MendeleyIcon}
        color={{
          hue: 355,
          saturation: 78,
          lightness: 34,
        }}
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        usageText="You can make links to research papers and data from Mendeley in RSpace documents."
        helpLinkText="Mendeley integration docs"
        website="mendeley.com"
        docLink="mendeley"
        setupSection={
          <ol>
            <li>Enable the integration.</li>
            <li>
              When editing a document, click on the Mendeley icon in the text
              editor toolbar.
            </li>
          </ol>
        }
      />
    </Grid>
  );
}

export default (React.memo(Mendeley): AbstractComponent<MendeleyArgs>);
