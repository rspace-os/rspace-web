//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, useState, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import {
  useIntegrationsEndpoint,
  type IntegrationStates,
} from "../useIntegrationsEndpoint";
import TextField from "@mui/material/TextField";
import { Optional } from "../../../util/optional";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import Button from "@mui/material/Button";
import PyratIcon from "../icons/pyrat.svg";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import ListItemText from "@mui/material/ListItemText";
import Stack from "@mui/material/Stack";
import { useLocalObservable, observer } from "mobx-react-lite";
import { runInAction } from "mobx";

type PyratArgs = {|
  integrationState: IntegrationStates["PYRAT"],
  update: (IntegrationStates["PYRAT"]) => void,
|};

/*
 * Pyrat uses API-key based authentication, as implemeted by the form below.
 */
function Pyrat({ integrationState, update }: PyratArgs): Node {
  const { saveAppOptions } = useIntegrationsEndpoint();
  const authenticatedServers = useLocalObservable(
    () => integrationState.credentials.authenticatedServers
  );
  const [addMenuAnchorEl, setAddMenuAnchorEl] = useState<null | EventTarget>(
    null
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
              <div>
                TODO: list already configured servers, with a text field for
                changing the API key
              </div>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  // TODO
                }}
              >
                <CardContent>
                  <Stack spacing={1}>
                    {authenticatedServers.map((server) => (
                      <TextField
                        key={server.alias}
                        fullWidth
                        variant="outlined"
                        label={`API Key for ${server.alias}`}
                        type="password"
                        size="small"
                        value={server.apiKey}
                        onChange={({ target: { value } }) => {
                          runInAction(() => {
                            server.apiKey = value;
                          });
                        }}
                      />
                    ))}
                  </Stack>
                </CardContent>
                <CardActions>
                  <Button type="submit">Save</Button>
                  <Button
                    onClick={(e) => {
                      setAddMenuAnchorEl(e.currentTarget);
                    }}
                  >
                    Add
                  </Button>
                  <Menu
                    open={Boolean(addMenuAnchorEl)}
                    anchorEl={addMenuAnchorEl}
                  >
                    {integrationState.credentials.configuredServers.map(
                      ({ alias, url }) => (
                        <MenuItem
                          key={alias}
                          onClick={() => {
                            void saveAppOptions("PYRAT", Optional.empty(), {
                              PYRAT_ALIAS: alias,
                              PYRAT_URL: url,
                              PYRAT_APIKEY: "",
                            }).then(() => {
                              setAddMenuAnchorEl(null);
                            });
                          }}
                        >
                          <ListItemText primary={alias} secondary={url} />
                        </MenuItem>
                      )
                    )}
                  </Menu>
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
