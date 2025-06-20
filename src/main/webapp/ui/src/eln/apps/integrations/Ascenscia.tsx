import Grid from "@mui/material/Grid";
import React, { useState } from "react";
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
import { Optional } from "../../../util/optional";

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
  const [apiKey, setApiKey] = useState(
    integrationState.credentials.ASCENSCIA_USER_TOKEN.orElse("")
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
              <li>
                TODO: Provide instructions for setting up Ascenscia integration.
              </li>
            </ol>
            <Card variant="outlined" sx={{ mt: 2 }}>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void update({
                    mode: integrationState.mode,
                    credentials: {
                      ASCENSCIA_USER_TOKEN: Optional.present(apiKey),
                    },
                  });
                }}
              >
                <CardContent>
                  <TextField
                    fullWidth
                    variant="outlined"
                    label="API Key"
                    type="password"
                    size="small"
                    value={apiKey}
                    onChange={({ target: { value } }) => {
                      setApiKey(value);
                    }}
                  />
                </CardContent>
                <CardActions>
                  <Button type="submit">Save</Button>
                </CardActions>
              </form>
            </Card>
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
