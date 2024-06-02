//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, useState, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import TextField from "@mui/material/TextField";
import { Optional } from "../../../util/optional";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import Button from "@mui/material/Button";
import EgnyteIcon from "../icons/egnyte.svg";

type EgnyteArgs = {|
  integrationState: IntegrationStates["EGNYTE"],
  update: (IntegrationStates["EGNYTE"]) => void,
|};

/*
 * Egnyte has a domain URL that is configured by the this text field.
 */
function Egnyte({ integrationState, update }: EgnyteArgs): Node {
  const [url, setUrl] = useState(integrationState.credentials.EGNYTE_DOMAIN);

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Egnyte"
        integrationState={integrationState}
        explanatoryText="Collaborate, sync and share your files with a security-focused enterprise solution."
        image={EgnyteIcon}
        color={{
          hue: 176,
          saturation: 89,
          lightness: 41,
        }}
        update={(newMode) => {
          return update({
            mode: newMode,
            credentials: integrationState.credentials,
          });
        }}
        usageText="You can include files from Egnyte in your RSpace documents. Files are embedded as links to the Egnyte location of that file."
        helpLinkText="Cloud Storage integrations docs"
        website="egnyte.com"
        docLink="cloudstorage"
        setupSection={
          <>
            <ol>
              <li>Provide your Egnyte domain URL and Save.</li>
              <li>Enable the integration.</li>
              <li>
                When editing a document, click on the Egnyte icon in the text
                editor toolbar.
              </li>
            </ol>
            <Card variant="outlined" sx={{ mt: 2 }}>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void update({
                    mode: integrationState.mode,
                    credentials: {
                      EGNYTE_DOMAIN: url,
                    },
                  });
                }}
              >
                <CardContent>
                  <TextField
                    fullWidth
                    variant="outlined"
                    label="Egnyte Domain URL"
                    size="small"
                    value={url.orElse("")}
                    onChange={({ target: { value } }) => {
                      setUrl(Optional.present(value));
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

export default (React.memo(Egnyte): AbstractComponent<EgnyteArgs>);
