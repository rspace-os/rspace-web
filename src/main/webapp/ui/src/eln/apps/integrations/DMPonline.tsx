import Grid from "@mui/material/Grid";
import React from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import DMPonlineIcon from "../../../assets/branding/dmponline/logo.svg";
import { observer } from "mobx-react-lite";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import Button from "@mui/material/Button";
import { useDmpOnlineEndpoint } from "../useDmpOnlineEndpoint";
import { LOGO_COLOR } from "../../../assets/branding/dmponline";

type DMPOnlineArgs = {
  integrationState: IntegrationStates["DMPONLINE"];
  update: (newIntegrationState: IntegrationStates["DMPONLINE"]) => void;
};

/*
 * There is no authentication mechanism with DMPonline. All DMPs are public and by
 * simply enabling the integration users can import those DMPs into the Gallery
 */
function DMPOnline({
  integrationState,
  update,
}: DMPOnlineArgs): React.ReactNode {
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
        explanatoryText="Create Data Management Plans for your research."
        image={DMPonlineIcon}
        color={LOGO_COLOR}
        usageText="You can import Data Management Plans (DMPs) from DMPonline into RSpace. You can then reference DMPs in RSpace documents, and attach DMPs to data deposits when exporting to repositories."
        helpLinkText="DMPonline integration docs"
        website="dmponline.dcc.ac.uk"
        docLink="dmponline"
        setupSection={
          <>
            <ol>
              <li>
                Click on Connect to authorise RSpace to access your DMPonline
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

export default React.memo(observer(DMPOnline));
