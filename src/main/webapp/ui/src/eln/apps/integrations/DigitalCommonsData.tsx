import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import React from "react";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import { LOGO_COLOR } from "../../../assets/branding/digitalcommonsdata";
import DcdIcon from "../../../assets/branding/digitalcommonsdata/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import IntegrationCard from "../IntegrationCard";
import { useDigitalCommonsDataEndpoint } from "../useDigitalCommonsDataEndpoint";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type DigitalCommonsDataArgs = {
  integrationState: IntegrationStates["DIGITALCOMMONSDATA"];
  update: (newIntegrationState: IntegrationStates["DIGITALCOMMONSDATA"]) => void;
};

export interface DigitalCommonsDataConnectedMessage extends Record<string, unknown> {
  type: "DIGITALCOMMONSDATA_CONNECTED";
  error?: string;
}
export const DIGITALCOMMONSDATA_CONNECTION_CHANNEL = "rspace.apps.digitalcommonsdata.connection";

/*
 * Digital Commons Data uses OAuth based authentication, as implemeted by the form below.
 */
function DigitalCommonsData({ integrationState, update }: DigitalCommonsDataArgs): React.ReactNode {
  const { addAlert } = React.useContext(AlertContext);
  const { disconnect } = useDigitalCommonsDataEndpoint();
  const [connected, setConnected] = React.useState(integrationState.credentials.ACCESS_TOKEN.isPresent());

  useBroadcastChannel<DigitalCommonsDataConnectedMessage>(
    DIGITALCOMMONSDATA_CONNECTION_CHANNEL,
    (e: MessageEvent<DigitalCommonsDataConnectedMessage>) => {
      if (e.data?.type !== "DIGITALCOMMONSDATA_CONNECTED") return;
      if (e.data.error) {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Could not connect to Digital Commons Data",
            message: e.data.error,
          }),
        );
        return;
      }
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully connected to Digital Commons Data.",
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
        name="Digital Commons Data / Mendeley Data"
        integrationState={integrationState}
        explanatoryText="Export datasets to the data repository, with persistent unique identifiers to enable referencing and citation."
        image={DcdIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: integrationState.credentials })}
        helpLinkText="Digital Commons Data / Mendeley Data integration docs"
        website="elsevier.digitalcommonsdata.com"
        docLink="dcd"
        usageText="You can export your files and data directly from RSpace to Digital Commons Data or Mendeley Data."
        setupSection={
          <>
            <ol>
              <li>
                Click on Connect to authorise RSpace to access your Digital Commons Data and Mendeley Data account.
              </li>
              <li>Enable the integration.</li>
              <li>Digital Commons Data / Mendeley Data will now be available as an option in the export dialog.</li>
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
              <form action="/apps/digitalcommonsdata/connect" method="POST" target="_blank" rel="noopener opener">
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

export default React.memo(DigitalCommonsData);
