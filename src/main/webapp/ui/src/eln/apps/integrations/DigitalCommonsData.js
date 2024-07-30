//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import Button from "@mui/material/Button";
import DMPToolIcon from "../icons/dmptool.svg";
import { useDmptoolEndpoint } from "../useDmptoolEndpoint";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";

type DigitalCommonsDataArgs = {|
  integrationState: IntegrationStates["DIGITAL_COMMONS_DATA"],
  update: (IntegrationStates["DIGITAL_COMMONS_DATA"]) => void,
|};

export const COLOR = {
  hue: 23,
  saturation: 95,
  lightness: 57,
};

/*
 * Digital Commons Data uses OAuth based authentication, as implemeted by the form below.
 */
function DigitalCommonsData({
  integrationState,
  update,
}: DigitalCommonsDataArgs): Node {
  const { addAlert } = React.useContext(AlertContext);
  const { disconnect } = useDmptoolEndpoint();
  const [connected, setConnected] = React.useState(
    integrationState.credentials.ACCESS_TOKEN.isPresent()
  );

  React.useEffect(() => {
    const f = () => {
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully connected to DMPTool.",
        })
      );
    };
    window.addEventListener("DMPTOOL_CONNECTED", f);
    return () => {
      window.removeEventListener("DMPTOOL_CONNECTED", f);
    };
  }, []);

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Digital Commons Data"
        integrationState={integrationState}
        explanatoryText="Create Data Management Plans for your research through a guided web-based tool with templates."
        image={DMPToolIcon}
        color={COLOR}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        helpLinkText="DMPTool integration docs"
        website="dmptool.org"
        docLink="dmptool"
        usageText="You can import Data Management Plans (DMPs) from DMPTool into RSpace, and associate DMPs with repository exports. Exporting from RSpace automatically updates the DMP in DMPTool with a DOI of the repository deposit."
        setupSection={
          <>
            <ol>
              <li>
                Click on Connect to authorise RSpace to access your DMPTool
                account.
              </li>
              <li>Enable the integration.</li>
              <li>
                You can now import a DMP when in the Gallery, and associate a
                DMP with data when in the export dialog.
              </li>
            </ol>
            {connected ? (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  void (async () => {
                    await disconnect();
                    setConnected(false);
                  })();
                }}
              >
                <Button type="submit" sx={{ mt: 1 }}>
                  Disconnect
                </Button>
              </form>
            ) : (
              <form
                action="/apps/dmptool/connect"
                method="POST"
                target="_blank"
                rel="opener"
              >
                <Button type="submit" sx={{ mt: 1 }} value="Connect">
                  Connect
                </Button>
              </form>
            )}
          </>
        }
      />
    </Grid>
  );
}

export default (React.memo(
  DigitalCommonsData
): AbstractComponent<DigitalCommonsDataArgs>);
