import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import React from "react";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import { LOGO_COLOR } from "../../../assets/branding/dmpassistant";
import DMPAssistantIcon from "../../../assets/branding/dmpassistant/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import IntegrationCard from "../IntegrationCard";
import { useDmpAssistantEndpoint } from "../useDmpAssistantEndpoint";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type DMPAssistantArgs = {
  integrationState: IntegrationStates["DMPASSISTANT"];
  update: (newIntegrationState: IntegrationStates["DMPASSISTANT"]) => void;
};

export interface DMPAssistantConnectedMessage extends Record<string, unknown> {
  type: "DMPASSISTANT_CONNECTED";
  error?: string;
}
export const DMPASSISTANT_CONNECTION_CHANNEL = "rspace.apps.dmpassistant.connection";

function DMPAssistant({ integrationState, update }: DMPAssistantArgs): React.ReactNode {
  const { addAlert } = React.useContext(AlertContext);
  const { disconnect } = useDmpAssistantEndpoint();
  const [connected, setConnected] = React.useState(integrationState.credentials.ACCESS_TOKEN.isPresent());

  useBroadcastChannel<DMPAssistantConnectedMessage>(
    DMPASSISTANT_CONNECTION_CHANNEL,
    (e: MessageEvent<DMPAssistantConnectedMessage>) => {
      if (e.data?.type !== "DMPASSISTANT_CONNECTED") return;
      if (e.data.error) {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Could not connect to DMP Assistant",
            message: e.data.error,
          }),
        );
        return;
      }
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully connected to DMP Assistant.",
        }),
      );
    },
  );

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
              <li>Click on Connect to authorise RSpace to access your DMP Assistant account.</li>
              <li>Enable the integration.</li>
              <li>
                You can now import a DMP when in the Gallery, and associate a DMP with data when in the export dialog.
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
              <form action="/apps/dmpassistant/connect" method="POST" target="_blank" rel="noopener opener">
                <Button type="submit" sx={{ mt: 1 }} value="Connect">
                  Connect
                </Button>
              </form>
            )}
          </>
        }
        update={(newMode) => update({ mode: newMode, credentials: integrationState.credentials })}
        integrationState={integrationState}
      />
    </Grid>
  );
}

export default React.memo(observer(DMPAssistant));
