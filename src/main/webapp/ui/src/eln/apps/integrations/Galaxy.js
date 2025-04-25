//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, useState, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import TextField from "@mui/material/TextField";
import { Optional } from "../../../util/optional";
import { observer } from "mobx-react-lite";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import Button from "@mui/material/Button";

type GalaxyArgs = {|
  integrationState: IntegrationStates["GALAXY"],
  update: (IntegrationStates["GALAXY"]) => void,
|};

/*
 * Galaxy uses API-key based authentication, as implemented by the form below.
 */
function Galaxy({ integrationState, update }: GalaxyArgs): Node {
  const [apiKey, setApiKey] = useState(
    integrationState.credentials.GALAXY_API_KEY.orElse("")
  );

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Galaxy"
        integrationState={integrationState}
        explanatoryText="Open, web-based platform for data intensive biomedical research."
        image={null}
        color={{ hue: 240, saturation: 100, lightness: 60 }} // Using blue as a placeholder
        usageText="You can connect your Galaxy workflows and data to RSpace. This allows you to send data from RSpace to Galaxy and import results back into your notebooks."
        helpLinkText="Galaxy integration docs"
        website="usegalaxy.org"
        docLink="galaxy"
        setupSection={
          <>
            <ol>
              <li>
                Obtain an API Key from Galaxy by going into User → Preferences → Manage API key.
              </li>
              <li>Copy the API Key into the field below, and Save.</li>
              <li>
                Galaxy will now be available as an integration option.
              </li>
            </ol>
            <Card variant="outlined" sx={{ mt: 2 }}>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void update({
                    mode: integrationState.mode,
                    credentials: {
                      GALAXY_API_KEY: Optional.present(apiKey),
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

export default (React.memo(observer(Galaxy)): AbstractComponent<GalaxyArgs>);