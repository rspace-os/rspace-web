import Grid from "@mui/material/Grid";
import React, { useContext, useState } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import TextField from "@mui/material/TextField";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import Button from "@mui/material/Button";
import AscensciaIcon from "../../../assets/branding/ascenscia/logo.svg";
import { observer } from "mobx-react-lite";
import { LOGO_COLOR } from "../../../assets/branding/ascenscia";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";

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
  const [apiKey, setApiKey] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [organization, setOrganization] = useState("");

  const { addAlert } = useContext(AlertContext);

  const handleConnect = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const form = e.currentTarget;
    const formData = new FormData(form);
    try {
      const response = await fetch("/apps/ascenscia/connect", {
        method: "POST",
        body: formData,
      });
      if (response.ok) {
        update({
          mode: integrationState.mode,
          credentials: integrationState.credentials,
        });
        addAlert(
          mkAlert({
            variant: "success",
            message: "Successfully saved Ascenscia api key",
          })
        );
      } else {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Unable to save Ascenscia API key",
            message: await response.text(),
          }),
        );
      }
    } catch (e) {
      if (e instanceof Error)
        addAlert(
          mkAlert({
            variant: "error",
            title: "Unable to save Ascenscia API key",
            message: e.message,
          })
        );
    }
  };

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
        update={(newMode) => {
          update({
            mode: newMode,
            credentials: {},
          });
        }}
        setupSection={
          <>
            <ol>
              <li>Provide your Ascenscia credentials and click on Save.</li>
              <li>Enable the integration.</li>
              <li>Use Ascenscia with your RSpace documents.</li>
            </ol>
            <Grid container direction="column" spacing={1} sx={{ mt: 2 }}>
              <Grid item>
                <Card variant="outlined">
                  <form
                    onSubmit={handleConnect}
                  >
                    <CardContent>
                      <Grid container direction="column" spacing={2}>
                        <Grid item>
                          <TextField
                            fullWidth
                            name="username"
                            variant="outlined"
                            label="Username"
                            type="text"
                            size="small"
                            value={username}
                            onChange={({ target: { value } }) => {
                              setUsername(value);
                            }}
                          />
                        </Grid>
                        <Grid item>
                          <TextField
                            fullWidth
                            name="password"
                            variant="outlined"
                            label="Password"
                            type="password"
                            size="small"
                            value={password}
                            onChange={({ target: { value } }) => {
                              setPassword(value);
                            }}
                          />
                        </Grid>
                        <Grid item>
                          <TextField
                            fullWidth
                            name="organization"
                            variant="outlined"
                            label="Organization"
                            type="text"
                            size="small"
                            value={organization}
                            onChange={({ target: { value } }) => {
                              setOrganization(value);
                            }}
                          />
                        </Grid>
                      </Grid>
                    </CardContent>
                    <CardActions>
                      <Button type="submit">Save</Button>
                    </CardActions>
                  </form>
                </Card>
              </Grid>
            </Grid>
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(observer(Ascenscia));
