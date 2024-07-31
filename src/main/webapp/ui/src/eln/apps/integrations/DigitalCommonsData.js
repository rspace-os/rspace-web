//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import Button from "@mui/material/Button";
import DcdIcon from "../icons/dcd.svg";
import { useDmptoolEndpoint } from "../useDmptoolEndpoint";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";

type DigitalCommonsDataArgs = {|
  integrationState: IntegrationStates["DIGITAL_COMMONS_DATA"],
  update: (IntegrationStates["DIGITAL_COMMONS_DATA"]) => void,
|};

export const COLOR = {
  hue: 26,
  saturation: 100,
  lightness: 50,
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
        explanatoryText="Export datasets to the data repository, with persistent unique identifiers to enable referencing and citation."
        image={DcdIcon}
        color={COLOR}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        helpLinkText="Digital Commons Data integration docs"
        website="elsevier.digitalcommonsdata.com"
        docLink="dcd"
        usageText="You can export your files and data directly from RSpace to Digital Commons Data."
        setupSection={
          <>
            <ol>
              <li>
                Click on Connect to authorise RSpace to access your Digital
                Commons Data account.
              </li>
              <li>Enable the integration.</li>
              <li>
                Digital Commons Data will now be available as an option in the
                export dialog.
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
