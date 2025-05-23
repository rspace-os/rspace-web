import Grid from "@mui/material/Grid";
import React from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import ArgosIcon from "../../../assets/branding/argos/logo.svg";
import { LOGO_COLOR } from "../../../assets/branding/argos";
import { observer } from "mobx-react-lite";

type ArgosArgs = {
  integrationState: IntegrationStates["ARGOS"];
  update: (newIntegrationState: IntegrationStates["ARGOS"]) => void;
};

/*
 * There is no authentication mechanism with Argos. All DMPs are public and by
 * simply enabling the integration users can import those DMPs into the Gallery
 */
function Argos({ integrationState, update }: ArgosArgs): React.ReactNode {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="ARGOS"
        explanatoryText="Create, manage and exchange Data Management Plans on an extensible open platform."
        image={ArgosIcon}
        color={LOGO_COLOR}
        usageText="You can import Data Management Plans (DMPs) from ARGOS into RSpace. You can then reference DMPs in RSpace documents, and attach DMPs to data deposits when exporting to repositories."
        helpLinkText="ARGOS integration docs"
        website="argos.openaire.eu"
        docLink="argos"
        setupSection={
          <ol>
            <li>Enable the integration.</li>
            <li>ARGOS DMPs can now be imported through the RSpace Gallery.</li>
          </ol>
        }
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        integrationState={integrationState}
      />
    </Grid>
  );
}

export default React.memo(observer(Argos));
