//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import ChemistryIcon from "../../../assets/branding/chemistry/logo.svg";
import { LOGO_COLOR } from "../../../assets/branding/chemistry";

type ChemistryArgs = {|
  integrationState: IntegrationStates["CHEMISTRY"],
  update: (IntegrationStates["CHEMISTRY"]) => void,
|};

/*
 * There is no authentication mechanism with Chemistry. All users can
 * enable the integration to be able to use it when editing a document.
 */
function Chemistry({ integrationState, update }: ChemistryArgs): Node {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Chemistry"
        integrationState={integrationState}
        explanatoryText="Draw and modify standard and advanced chemical structures with a web-based chemical editor."
        image={ChemistryIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        usageText="You can draw chemical structures and reactions in documents, search for chemical structures across your Workspace, and import or export to standard formats such as ChemDraw, mol, SMILES, and others."
        helpLinkText="Chemistry integration docs"
        docLink="chemistry"
        setupSection={
          <ol>
            <li>Enable the integration.</li>
            <li>
              When editing a document, click on the integration’s icon in the
              text editor toolbar to open the chemical sketcher.
            </li>
            <li>
              You can also drag and drop existing chemical structure files into
              a document text field, and edit them.
            </li>
          </ol>
        }
      />
    </Grid>
  );
}

export default (React.memo(Chemistry): AbstractComponent<ChemistryArgs>);
