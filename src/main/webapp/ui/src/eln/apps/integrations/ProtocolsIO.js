//@flow strict

import Grid from "@mui/material/Grid";
import React, {
  type Node,
  useEffect,
  useState,
  useContext,
  type AbstractComponent,
} from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import Button from "@mui/material/Button";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import ProtocolsIOIcon from "../icons/protocolsio.svg";
import { useProtocolsioEndpoint } from "../useProtocolsio";

type ProtocolsIOArgs = {|
  integrationState: IntegrationStates["PROTOCOLS_IO"],
  update: (IntegrationStates["PROTOCOLS_IO"]) => void,
|};

/*
 * ProtocolsIO uses OAuth based authentication, as implemented by this form.
 *
 * The process of connection is:
 *   1. This form POSTs to /apps/protocols/connect in a new window, which
 *      redirects to the Protcols IO authentication page.
 *   2. Once the user has authenticated, Protocols IO redirects the window
 *      to RSpace's redirect URL with a temporary code in the search params.
 *   2. The RSpace backend records the passed code, converts it into an
 *      authentication token which is stored, and redirects the window again to
 *      ../../../../../WEB-INF/pages/connect/protocolsio/connected.jsp.
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
 * ../useProtocolsio.
 */
function ProtocolsIO({ integrationState, update }: ProtocolsIOArgs): Node {
  const { addAlert } = useContext(AlertContext);
  const { disconnect } = useProtocolsioEndpoint();
  const [connected, setConnected] = useState(
    integrationState.credentials.ACCESS_TOKEN.isPresent()
  );

  useEffect(() => {
    const f = () => {
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully connected to Protocols IO.",
        })
      );
    };
    window.addEventListener("PROTOCOLS_IO_CONNECTED", f);
    return () => {
      window.removeEventListener("PROTOCOLS_IO_CONNECTED", f);
    };
  }, []);

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="protocols.io"
        integrationState={integrationState}
        explanatoryText="Develop, organise, and share reproducible experimental protocols through a secure repository."
        image={ProtocolsIOIcon}
        color={{
          hue: 0,
          saturation: 0,
          lightness: 21,
        }}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        usageText="You can browse private and public protocols, and import them into RSpace."
        helpLinkText="protocols.io integration docs"
        website="protocols.io"
        docLink="protocolsio"
        setupSection={
          <>
            <ol>
              <li>Register for a protocols.io account.</li>
              <li>
                Click on Connect to authorise RSpace to access your protocols.io
                account.
              </li>
              <li>Enable the integration.</li>
              <li>
                You can now import protocols from the Workspace Create menu, or
                from the text editor toolbar when editing a document.
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
                action="/apps/protocolsio/connect"
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

export default (React.memo(ProtocolsIO): AbstractComponent<ProtocolsIOArgs>);
