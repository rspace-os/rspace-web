//@flow strict

import Grid from "@mui/material/Grid";
import React, {
  type Node,
  useEffect,
  useContext,
  useState,
  type AbstractComponent,
} from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import Button from "@mui/material/Button";
import OwnCloudIcon from "../icons/owncloud.svg";
import { useOwncloudEndpoint } from "../useOwncloud";

type OwnCloudArgs = {|
  integrationState: IntegrationStates["OWNCLOUD"],
  update: (IntegrationStates["OWNCLOUD"]) => void,
|};

/*
 * OwnCloud uses OAuth based authentication, as implemented by this form.
 *
 * The process of connection is:
 *   1. This form POSTs to /apps/owncloud/connect in a new window, which
 *      redirects to the OwnCloud authentication page.
 *   2. Once the user has authenticated, OwnCloud redirects the window
 *      to RSpace's redirect URL with a temporary code in the search params.
 *   2. The RSpace backend records the passed code, converts it into an
 *      authentication token which is stored, and redirects the window again to
 *      ../../../../../WEB-INF/pages/connect/owncloud/connected.jsp.
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
 * ../useOwncloud.
 */
function OwnCloud({ integrationState, update }: OwnCloudArgs): Node {
  const { addAlert } = useContext(AlertContext);
  const { disconnect } = useOwncloudEndpoint();
  const [connected, setConnected] = useState(
    integrationState.credentials.ACCESS_TOKEN.isPresent()
  );

  useEffect(() => {
    const f = () => {
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully connected to OwnCloud.",
        })
      );
    };
    window.addEventListener("OWNCLOUD_CONNECTED", f);
    return () => {
      window.removeEventListener("OWNCLOUD_CONNECTED", f);
    };
  }, []);

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="ownCloud"
        integrationState={integrationState}
        explanatoryText="Create, manage, and share your files through an open-source file hosting system."
        image={OwnCloudIcon}
        color={{
          hue: 217,
          saturation: 36,
          lightness: 20,
        }}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        helpLinkText="OwnCloud integration docs"
        website="owncloud.com"
        docLink="owncloud"
        usageText="You can make links to ownCloud documents directly from RSpace."
        setupSection={
          <>
            <ol>
              <li>
                Click on Connect to authorise RSpace to access your ownCloud
                account.
              </li>
              <li>Enable the integration.</li>
              <li>
                When editing a document, click on the ownCloud icon in the text
                editor toolbar.
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
                action="/apps/owncloud/connect"
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

export default (React.memo(OwnCloud): AbstractComponent<OwnCloudArgs>);
