//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import DMPonlineIcon from "../icons/dmponline.svg";
import { observer } from "mobx-react-lite";

type DMPOnlineArgs = {|
  integrationState: IntegrationStates["DMPONLINE"],
  update: (IntegrationStates["DMPONLINE"]) => void,
|};

export const COLOR = {
  hue: 39,
  saturation: 99,
  lightness: 46,
};

/*
 * There is no authentication mechanism with DMPonline. All DMPs are public and by
 * simply enabling the integration users can import those DMPs into the Gallery
 */
function DMPOnline({ integrationState, update }: DMPOnlineArgs): Node {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="DMPonline"
        explanatoryText="Available soon."
        image={DMPonlineIcon}
        color={COLOR}
        usageText="You can import Data Management Plans (DMPs) from DMPonline into RSpace. You can then reference DMPs in RSpace documents, and attach DMPs to data deposits when exporting to repositories."
        helpLinkText="DMPonline integration docs"
        website="dmponline.dcc.ac.uk"
        docLink="dmponline"
        setupSection={
          <ol>
            <li>Enable the integration.</li>
            <li>
              DMPonline DMPs can now be imported through the RSpace Gallery.
            </li>
          </ol>
        }
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        integrationState={integrationState}
      />
    </Grid>
  );
}

export default (React.memo(
  observer(DMPOnline)
): AbstractComponent<DMPOnlineArgs>);
