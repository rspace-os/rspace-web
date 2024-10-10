//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import Button from "@mui/material/Button";
import FieldmarktIcon from "../icons/fieldmark.svg";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import TextField from "@mui/material/TextField";
import { Optional } from "../../../util/optional";

type FieldmarkArgs = {|
  integrationState: IntegrationStates["FIELDMARK"],
  update: (IntegrationStates["FIELDMARK"]) => void,
|};

export const COLOR = {
  hue: 82,
  saturation: 80,
  lightness: 33,
};

/*
 * Fieldmark uses OAuth based authentication, as implemeted by the form below.
 */
function Fieldmark({ integrationState, update }: FieldmarkArgs): Node {
  const [apiKey, setApiKey] = React.useState(
    integrationState.credentials.FIELDMARK_USER_TOKEN.orElse("")
  );

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Fieldmark"
        integrationState={integrationState}
        explanatoryText="Available soon."
        //explanatoryText="Collect structured, geospatial, instrument and multimedia data into notebooks for easy importing into Inventory."
        image={FieldmarktIcon}
        color={COLOR}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        helpLinkText="Fieldmark integration docs"
        website="fieldnote.au/fieldmark"
        docLink="fieldmark"
        usageText="You can import you notebooks of data into Inventory."
        setupSection={
          <>
            <ol>
              <li>
                Obtain an API Token from Fieldmark by [how to get API key].
              </li>
              <li>Copy the API Token into the field below, and Save.</li>
              <li>Enable the integration.</li>
              <li>Use the import button in Inventory to choose a notebook.</li>
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

export default (React.memo(Fieldmark): AbstractComponent<FieldmarkArgs>);
