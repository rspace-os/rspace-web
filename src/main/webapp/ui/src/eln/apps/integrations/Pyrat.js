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
import PyratIcon from "../icons/pyrat.svg";

type PyratArgs = {|
  integrationState: IntegrationStates["PYRAT"],
  update: (IntegrationStates["PYRAT"]) => void,
|};

/*
 * Pyrat uses API-key based authentication, as implemeted by the form below.
 */
function Pyrat({ integrationState, update }: PyratArgs): Node {
  const [apiKey, setApiKey] = useState(
    integrationState.credentials.PYRAT_USER_TOKEN.orElse("")
  );

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="PyRAT"
        integrationState={integrationState}
        explanatoryText="Increase efficiency, access, and ensure compliance through a lab animal colony management software."
        image={PyratIcon}
        color={{
          hue: 196,
          saturation: 79,
          lightness: 45,
        }}
        usageText="You can browse and link to animals in a PyRAT database directly from RSpace."
        helpLinkText="PyRAT integration docs"
        website="scionics.com/pyrat"
        docLink="pyrat"
        setupSection={
          <>
            <ol>
              <li>
                Request a user access token by going to Administration → API →
                Request access in PyRAT.
              </li>
              <li>Enter the access token into the field below, and save.</li>
              <li>Enable the integration.</li>
              <li>
                When editing a document, click on the PyRAT icon in the text
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
                      PYRAT_USER_TOKEN: Optional.present(apiKey),
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
          return update({
            mode: newMode,
            credentials: integrationState.credentials,
          });
        }}
      />
    </Grid>
  );
}

export default (React.memo(observer(Pyrat)): AbstractComponent<PyratArgs>);
