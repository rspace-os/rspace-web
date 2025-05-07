import Grid from "@mui/material/Grid";
import React from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import Button from "@mui/material/Button";
import DcdIcon from "../../../assets/branding/digitalcommonsdata/logo.svg";
import { useDigitalCommonsDataEndpoint } from "../useDigitalCommonsDataEndpoint";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import { LOGO_COLOR } from "../../../assets/branding/digitalcommonsdata";

type DigitalCommonsDataArgs = {
  integrationState: IntegrationStates["DIGITALCOMMONSDATA"];
  update: (
    newIntegrationState: IntegrationStates["DIGITALCOMMONSDATA"]
  ) => void;
};

/*
 * Digital Commons Data uses OAuth based authentication, as implemeted by the form below.
 */
function DigitalCommonsData({
  integrationState,
  update,
}: DigitalCommonsDataArgs): React.ReactNode {
  const { addAlert } = React.useContext(AlertContext);
  const { disconnect } = useDigitalCommonsDataEndpoint();
  const [connected, setConnected] = React.useState(
    integrationState.credentials.ACCESS_TOKEN.isPresent()
  );

  React.useEffect(() => {
    const f = () => {
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully connected to Digital Commons Data.",
        })
      );
    };
    window.addEventListener("DIGITALCOMMONSDATA_CONNECTED", f);
    return () => {
      window.removeEventListener("DIGITALCOMMONSDATA_CONNECTED", f);
    };
  }, []);

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Digital Commons Data / Mendeley Data"
        integrationState={integrationState}
        explanatoryText="Export datasets to the data repository, with persistent unique identifiers to enable referencing and citation."
        image={DcdIcon}
        color={LOGO_COLOR}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        helpLinkText="Digital Commons Data / Mendeley Data integration docs"
        website="elsevier.digitalcommonsdata.com"
        docLink="dcd"
        usageText="You can export your files and data directly from RSpace to Digital Commons Data or Mendeley Data."
        setupSection={
          <>
            <ol>
              <li>
                Click on Connect to authorise RSpace to access your Digital
                Commons Data and Mendeley Data account.
              </li>
              <li>Enable the integration.</li>
              <li>
                Digital Commons Data / Mendeley Data will now be available as an
                option in the export dialog.
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
                action="/apps/digitalcommonsdata/connect"
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

export default React.memo(DigitalCommonsData);
