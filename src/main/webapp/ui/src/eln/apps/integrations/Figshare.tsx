import Grid from "@mui/material/Grid";
import React, { useEffect, useState, useContext } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import Button from "@mui/material/Button";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import FigshareIcon from "../../../assets/branding/figshare/logo.svg";
import { useFigshareEndpoint } from "../useFigshare";
import { LOGO_COLOR } from "../../../assets/branding/figshare";

type FigshareArgs = {
  integrationState: IntegrationStates["FIGSHARE"];
  update: (newIntegrationState: IntegrationStates["FIGSHARE"]) => void;
};

/*
 * Figshare uses OAuth based authentication, as implemented by this form.
 *
 * The process of connection is:
 *   1. This form POSTs to /apps/figshare/connect in a new window, which
 *      redirects to the Figshare authentication page.
 *   2. Once the user has authenticated, Figshare redirects the window
 *      to RSpace's redirect URL with a temporary code in the search params.
 *   2. The RSpace backend records the passed code, converts it into an
 *      authentication token which is stored, and redirects the window again to
 *      ../../../../../WEB-INF/pages/connect/figshare/connected.jsp.
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
 * ../useFigshare.
 */
function Figshare({ integrationState, update }: FigshareArgs): React.ReactNode {
  const { addAlert } = useContext(AlertContext);
  const { disconnect } = useFigshareEndpoint();
  const [connected, setConnected] = useState(
    integrationState.credentials.ACCESS_TOKEN.isPresent()
  );

  useEffect(() => {
    const f = () => {
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully connected to Figshare.",
        })
      );
    };
    window.addEventListener("FIGSHARE_CONNECTED", f);
    return () => {
      window.removeEventListener("FIGSHARE_CONNECTED", f);
    };
  }, []);

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Figshare"
        integrationState={integrationState}
        explanatoryText="Easily manage all your research outputs and make them available in a citable, shareable and discoverable manner."
        image={FigshareIcon}
        color={LOGO_COLOR}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        usageText="You can export your files and data directly from RSpace to Figshare. You are able to specify various metadata, and associate a DMP from DMPTool with the deposit."
        helpLinkText="Figshare integration docs"
        website="figshare.com"
        docLink="figshare"
        setupSection={
          <>
            <ol>
              <li>
                Click on Connect to authorise RSpace to access your Figshare
                account.
              </li>
              <li>Enable the integration.</li>
              <li>
                Figshare will now be available as an option in the export
                dialog.
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
                action="/apps/figshare/connect"
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

export default React.memo(Figshare);
