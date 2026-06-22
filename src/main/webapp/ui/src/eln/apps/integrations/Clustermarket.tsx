import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import React, { useContext, useState } from "react";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import { LOGO_COLOR } from "../../../assets/branding/clustermarket";
import ClustermarketIcon from "../../../assets/branding/clustermarket/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import IntegrationCard from "../IntegrationCard";
import { useClustermarketEndpoint } from "../useClustermarket";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type ClustermarketArgs = {
  integrationState: IntegrationStates["CLUSTERMARKET"];
  update: (newIntegrationState: IntegrationStates["CLUSTERMARKET"]) => void;
};

export interface ClustermarketConnectedMessage extends Record<string, unknown> {
  type: "CLUSTERMARKET_CONNECTED";
  error?: string;
}
export const CLUSTERMARKET_CONNECTION_CHANNEL = "rspace.apps.clustermarket.connection";

/*
 * Clustermarket uses OAuth based authentication, as implemented by this form.
 *
 * The process of connection is:
 *   1. This form POSTs to /apps/clustermarket/connect in a new window, which
 *      redirects to the Clustermarket authentication page.
 *   2. Once the user has authenticated, Clustermarket redirects the window
 *      to RSpace's redirect URL with a temporary code in the search params.
 *   2. The RSpace backend records the passed code, converts it into an
 *      authentication token which is stored, and redirects the window again to
 *      ../../../../../WEB-INF/pages/connect/clustermarket/connected.jsp.
 *   3. That page then dispatches an event back to this page reporting that the
 *      connection was successful and closes itself.
 *   4. This component listens for that event and updates the UI accordingly.
 *
 * The reasons for this round-about system is so that the user is not
 * redirected away from the new apps page. It is a React-based Single Page
 * Application (SPA), and thus provides the best user experience by maintaining
 * state (e.g. keeping the user in the dialog they were just in, displaying
 * success alerts, etc.).
 *
 * The process of disconnecing is via a standard API call made by
 * ../useClustermarket.
 */
function Clustermarket({ integrationState, update }: ClustermarketArgs): React.ReactNode {
  const { addAlert } = useContext(AlertContext);
  const { disconnect } = useClustermarketEndpoint();
  const [connected, setConnected] = useState(integrationState.credentials.ACCESS_TOKEN.isPresent());

  useBroadcastChannel<ClustermarketConnectedMessage>(
    CLUSTERMARKET_CONNECTION_CHANNEL,
    (e: MessageEvent<ClustermarketConnectedMessage>) => {
      if (e.data?.type !== "CLUSTERMARKET_CONNECTED") return;
      if (e.data.error) {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Could not connect to Calira",
            message: e.data.error,
          }),
        );
        return;
      }
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully connected to Calira.",
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
        name="Calira"
        integrationState={integrationState}
        explanatoryText="Manage schedules of lab equipment, maintenance, and personnel through a web-based platform."
        image={ClustermarketIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: integrationState.credentials })}
        helpLinkText="Calira integration docs"
        website="calira.co"
        docLink="clustermarket"
        usageText="You can view and insert your equipment bookings from Calira into RSpace documents, as data tables. These tables will contain direct links back to the bookings in Calira."
        setupSection={
          <>
            <ol>
              <li>Register for a Calira account.</li>
              <li>Click on Connect to authorise RSpace to access your Calira account.</li>
              <li>Enable the integration.</li>
              <li>
                When editing a document, click on the Calira icon in the text editor toolbar to access and insert
                equipment data.
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
              <form action="/apps/clustermarket/connect" method="POST" target="_blank" rel="noopener opener">
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

export default React.memo(Clustermarket);
