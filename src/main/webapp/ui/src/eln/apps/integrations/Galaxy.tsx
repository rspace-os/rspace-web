import Grid from "@mui/material/Grid";
import IntegrationCard from "../IntegrationCard";
import React, { useState } from "react";
import TextField from "@mui/material/TextField";
import { Optional } from "@/util/optional";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import Button from "@mui/material/Button";
import GalaxyIcon from "../../../assets/branding/galaxy/logo.svg";
import { LOGO_COLOR } from "@/assets/branding/galaxy";
import type {IntegrationStates} from "@/eln/apps/useIntegrationsEndpoint";

type GalaxyArgs = {
  integrationState: IntegrationStates["GALAXY"];
  update: (newIntegrationState: IntegrationStates["GALAXY"]) => void;
};

/*
 * Galaxy uses API-key based authentication, as implemented by the form below.
 */
function Galaxy({ integrationState, update }: GalaxyArgs): React.ReactNode {
  const [apiKey, setApiKey] = useState(
    integrationState.credentials.GALAXY_API_KEY.orElse("")
  );

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Galaxy"
        integrationState={integrationState}
        explanatoryText="Galaxy is a free, open-source system for data analysis, workflows, and more."
        image={GalaxyIcon}
        color={LOGO_COLOR}
        usageText="You can connect your Galaxy workflows and data to RSpace. This allows you to send data from RSpace to Galaxy and import results back into your notebooks."
        helpLinkText="Galaxy integration docs"
        website="galaxyproject.org"
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

export default React.memo(Galaxy);
