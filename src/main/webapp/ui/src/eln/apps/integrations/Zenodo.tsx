import Grid from "@mui/material/Grid";
import React, { useState } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import TextField from "@mui/material/TextField";
import { Optional } from "../../../util/optional";
import { observer } from "mobx-react-lite";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import Button from "@mui/material/Button";
import ZenodoIcon from "../../../assets/branding/zenodo/logo.svg";
import { LOGO_COLOR } from "../../../assets/branding/zenodo";

type ZenodoArgs = {
  integrationState: IntegrationStates["ZENODO"];
  update: (newIntegrationState: IntegrationStates["ZENODO"]) => void;
};

/*
 * Zenodo uses API-key based authentication, as implemeted by the form below.
 */
function Zenodo({ integrationState, update }: ZenodoArgs): React.ReactNode {
  const [apiKey, setApiKey] = useState(
    integrationState.credentials.ZENODO_USER_TOKEN.orElse(""),
  );

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Zenodo"
        integrationState={integrationState}
        explanatoryText="Deposit research papers and datasets into a general-purpose open repository with PIDs."
        image={ZenodoIcon}
        color={LOGO_COLOR}
        usageText="You can export your files and data directly from RSpace to Zenodo. You can specify various metadata and controlled vocabulary terms for the deposit, and reference a DMP from Argos."
        helpLinkText="Zenodo integration docs"
        website="zenodo.org"
        docLink="zenodo"
        setupSection={
          <>
            <ol>
              <li>
                Obtain an API Token from Zenodo by going into Settings →
                Applications, and name the token “RSpace”.
              </li>
              <li>Copy the API Token into the field below, and Save.</li>
              <li>
                Zenodo will now be available as an option in the export dialog.
              </li>
            </ol>
            <Card variant="outlined" sx={{ mt: 2 }}>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void update({
                    mode: integrationState.mode,
                    credentials: {
                      ZENODO_USER_TOKEN: Optional.present(apiKey),
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

export default React.memo(observer(Zenodo));
