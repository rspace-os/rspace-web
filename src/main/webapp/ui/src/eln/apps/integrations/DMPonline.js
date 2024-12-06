//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import DMPonlineIcon from "../icons/dmponline.svg";
import { observer } from "mobx-react-lite";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import Button from "@mui/material/Button";
import { useDmpOnlineEndpoint } from "../useDmpOnlineEndpoint";

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
  const { addAlert } = React.useContext(AlertContext);
  const { disconnect } = useDmpOnlineEndpoint();
  const [connected, setConnected] = React.useState(
    integrationState.credentials.ACCESS_TOKEN.isPresent()
  );

  React.useEffect(() => {
    const f = () => {
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully connected to DMPOnline.",
        })
      );
    };
    window.addEventListener("DMPONLINE_CONNECTED", f);
    return () => {
      window.removeEventListener("DMPONLINE_CONNECTED", f);
    };
  }, []);

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
          <>
            <ol>
              <li>Enable the integration.</li>
              <li>
                DMPonline DMPs can now be imported through the RSpace Gallery.
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
                action="/apps/dmponline/connect"
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
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        integrationState={integrationState}
      />
    </Grid>
  );
}

export default (React.memo(
  observer(DMPOnline)
): AbstractComponent<DMPOnlineArgs>);
