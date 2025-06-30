import Grid from "@mui/material/Grid";
import React, { useState } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import TextField from "@mui/material/TextField";
import Button from "@mui/material/Button";
import AscensciaIcon from "../../../assets/branding/ascenscia/logo.svg";
import { observer } from "mobx-react-lite";
import { LOGO_COLOR } from "../../../assets/branding/ascenscia";

type AscensciaArgs = {
  integrationState: IntegrationStates["ASCENSCIA"];
  update: (newIntegrationState: IntegrationStates["ASCENSCIA"]) => void;
};

/*
 * The integration is actually on Ascenscia's end; the user passes their RSpace
 * API key to Ascenscia.
 */
function Ascenscia({
  integrationState,
  update,
}: AscensciaArgs): React.ReactNode {
  const [username, setUsername] = useState(
    integrationState.credentials.ASCENSCIA_USERNAME.orElse("")
  );
  const [password, setPassword] = useState(
    integrationState.credentials.ASCENSCIA_PASSWORD.orElse("")
  );
  const [organization, setOrganization] = useState(
    integrationState.credentials.ASCENSCIA_ORGANIZATION.orElse("")
  );
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Ascenscia"
        integrationState={integrationState}
        explanatoryText="A highly specialized voice assistant mobile application for scientific labs."
        image={AscensciaIcon}
        color={LOGO_COLOR}
        usageText="The software solution acts as a personal assistant for scientists in the labs to mediate their interactions with Electronic Lab Notebooks outside of the lab."
        helpLinkText="Ascenscia integration docs"
        website="ascenscia.ai"
        docLink="ascenscia"
        setupSection={
          <>
            <ol>
              <li>Provide your Ascenscia credentials and click on Connect.</li>
              <li>Enable the integration.</li>
              <li>Use Ascenscia with your RSpace documents.</li>
            </ol>
            <form action="/apps/ascenscia/connect" method="POST">
              <Grid container direction="column" spacing={1}>
                <Grid item>
                  <TextField
                    inputProps={{
                      name: "username",
                      autoComplete: "username",
                    }}
                    fullWidth
                    value={username}
                    onChange={({ target: { value } }) => setUsername(value)}
                    label="Username"
                    sx={{ mt: 1 }}
                  />
                </Grid>
                <Grid item>
                  <TextField
                    inputProps={{
                      name: "password",
                      type: "password",
                      autoComplete: "new-password",
                    }}
                    fullWidth
                    value={password}
                    onChange={({ target: { value } }) => setPassword(value)}
                    label="Password"
                    sx={{ mt: 1 }}
                  />
                </Grid>
                <Grid item>
                  <TextField
                    inputProps={{
                      name: "organization",
                    }}
                    fullWidth
                    value={organization}
                    onChange={({ target: { value } }) => setOrganization(value)}
                    label="organization"
                    sx={{ mt: 1 }}
                  />
                </Grid>
                <Grid item>
                  <Button type="submit" value={"Connect"} sx={{ mt: 1 }}>
                    Connect
                  </Button>
                </Grid>
              </Grid>
            </form>
          </>
        }
        update={(newMode) => {
          update({
            mode: newMode,
            credentials: integrationState.credentials,
          });
        }}
      />
    </Grid>
  );
}

export default React.memo(observer(Ascenscia));
