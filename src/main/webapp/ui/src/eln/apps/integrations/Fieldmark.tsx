import Grid from "@mui/material/Grid";
import React from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import Button from "@mui/material/Button";
import FieldmarktIcon from "../../../assets/branding/fieldmark/logo.svg";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import TextField from "@mui/material/TextField";
import { Optional } from "../../../util/optional";
import { LOGO_COLOR } from "../../../assets/branding/fieldmark";

type FieldmarkArgs = {
  integrationState: IntegrationStates["FIELDMARK"];
  update: (newIntegrationState: IntegrationStates["FIELDMARK"]) => void;
};

/*
 * Fieldmark uses an API-key-like authentication mechanism, wherein the user
 * copies a string from the Fieldmark website into the text field provided
 * below..
 */
function Fieldmark({
  integrationState,
  update,
}: FieldmarkArgs): React.ReactNode {
  const [apiKey, setApiKey] = React.useState(
    integrationState.credentials.FIELDMARK_USER_TOKEN.orElse("")
  );

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Fieldmark"
        integrationState={integrationState}
        explanatoryText="Collect structured, geospatial sample and fieldwork data while offline, for easy importing into Inventory."
        image={FieldmarktIcon}
        color={LOGO_COLOR}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        helpLinkText="Fieldmark integration docs"
        website="fieldnote.au/fieldmark"
        docLink="fieldmark"
        usageText={
          <>
            You can import your <strong>Fieldmark</strong> notebooks into
            Inventory
          </>
        }
        setupSection={
          <>
            <ol>
              <li>Obtain an API Token from Fieldmark.</li>
              <li>Copy the API Token into the field below, and Save.</li>
              <li>Enable the integration.</li>
              <li>
                Use the import button in Inventory, and select Fieldmark to
                browse notebooks for import.
              </li>
            </ol>
            <Card variant="outlined" sx={{ mt: 2 }}>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void update({
                    mode: integrationState.mode,
                    credentials: {
                      FIELDMARK_USER_TOKEN: Optional.present(apiKey),
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
      />
    </Grid>
  );
}

export default React.memo(Fieldmark);
