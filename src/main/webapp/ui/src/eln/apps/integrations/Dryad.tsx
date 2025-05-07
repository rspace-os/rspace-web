import Grid from "@mui/material/Grid";
import React, { useEffect, useState, useContext } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import Button from "@mui/material/Button";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import DryadIcon from "../../../assets/branding/dryad/logo.svg";
import Link from "@mui/material/Link";
import { useDryadEndpoint } from "../useDryad";
import { LOGO_COLOR } from "../../../assets/branding/dryad";

type DryadArgs = {
  integrationState: IntegrationStates["DRYAD"];
  update: (newIntegrationState: IntegrationStates["DRYAD"]) => void;
};

/*
 * Dryad uses OAuth based authentication, as implemented by this form.
 *
 * The process of connection is:
 *   1. This form POSTs to /apps/dryad/connect in a new window, which
 *      redirects to the Dryad authentication page.
 *   2. Once the user has authenticated, Dryad redirects the window
 *      to RSpace's redirect URL with a temporary code in the search params.
 *   2. The RSpace backend records the passed code, converts it into an
 *      authentication token which is stored, and redirects the window again to
 *      ../../../../../WEB-INF/pages/connect/dryad/connected.jsp.
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
 * ../useDryad.
 */
function Dryad({ integrationState, update }: DryadArgs): React.ReactNode {
  const { addAlert } = useContext(AlertContext);
  const { disconnect } = useDryadEndpoint();
  const [connected, setConnected] = useState(
    integrationState.credentials.ACCESS_TOKEN.isPresent()
  );

  useEffect(() => {
    const f = () => {
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully connected to Dryad.",
        })
      );
    };
    window.addEventListener("DRYAD_CONNECTED", f);
    return () => {
      window.removeEventListener("DRYAD_CONNECTED", f);
    };
  }, []);

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Dryad"
        integrationState={integrationState}
        explanatoryText="Deposit, discover, and cite research data through a curated open-access repository."
        image={DryadIcon}
        color={LOGO_COLOR}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        usageText="You can export your files and data directly from RSpace to Dryad, and provide metadata for the deposit."
        helpLinkText="Dryad integration docs"
        website="datadryad.org"
        docLink="dryad"
        setupSection={
          <>
            <ol>
              <li>
                Dryad uses ORCID iD for authentication; if you don’t have an
                ORCID iD, you can create one at{" "}
                <Link
                  href="https://orcid.org/register"
                  target="_blank"
                  rel="noreferrer"
                >
                  orcid.org/register
                </Link>
              </li>
              <li>
                Click on Connect to authorise RSpace to access your Dryad
                account.
              </li>
              <li>
                Dryad will now be available as an option in the export dialog.
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
                action="/apps/dryad/connect"
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

export default React.memo(Dryad);
