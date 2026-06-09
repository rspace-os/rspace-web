import Grid from "@mui/material/Grid";
import React from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import DMPAssistantIcon from "../../../assets/branding/dmpassistant/logo.svg";
import { observer } from "mobx-react-lite";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import Button from "@mui/material/Button";
import { useDmpAssistantEndpoint } from "../useDmpAssistantEndpoint";
import { LOGO_COLOR } from "../../../assets/branding/dmpassistant";

type DMPAssistantArgs = {
  integrationState: IntegrationStates["DMPASSISTANT"];
  update: (newIntegrationState: IntegrationStates["DMPASSISTANT"]) => void;
};

function DMPAssistant({
  integrationState,
  update,
}: DMPAssistantArgs): React.ReactNode {
  const { addAlert } = React.useContext(AlertContext);
  const { disconnect } = useDmpAssistantEndpoint();
  const [connected, setConnected] = React.useState(
    integrationState.credentials.ACCESS_TOKEN.isPresent()
  );

  React.useEffect(() => {
    const f = () => {
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully connected to DMP Assistant.",
        })
      );
    };
    window.addEventListener("DMPASSISTANT_CONNECTED", f);
    return () => {
      window.removeEventListener("DMPASSISTANT_CONNECTED", f);
    };
  }, []);

  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name="DMP Assistant"
        explanatoryText="Portage Network's DMP Assistant — create and manage Data Management Plans in DMPRoadmap."
        image={DMPAssistantIcon}
        color={LOGO_COLOR}
        usageText="You can import Data Management Plans (DMPs) from DMP Assistant into RSpace, reference them in RSpace documents, and attach them to data deposits when exporting to repositories."
        helpLinkText="DMP Assistant integration docs"
        website="dmp-pgd.ca"
        docLink="dmpassistant"
        setupSection={
          <>
            <ol>
              <li>
                Click on Connect to authorise RSpace to access your DMP
                Assistant account.
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
                action="/apps/dmpassistant/connect"
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

export default React.memo(observer(DMPAssistant));
