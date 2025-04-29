//@flow strict

import Grid from "@mui/material/Grid";
import React, { useState } from "react";
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
import PyratIcon from "../../../assets/branding/pyrat/logo.svg";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import ListItemText from "@mui/material/ListItemText";
import Stack from "@mui/material/Stack";
import { useLocalObservable, observer } from "mobx-react-lite";
import { runInAction } from "mobx";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import Typography from "@mui/material/Typography";
import RsSet from "../../../util/set";
import { LOGO_COLOR } from "../../../assets/branding/pyrat";

type PyratArgs = {
  integrationState: IntegrationStates["PYRAT"];
  update: (newIntegrationState: IntegrationStates["PYRAT"]) => void;
};

/*
 * Pyrat uses API-key based authentication, as implemeted by the form below.
 */
function Pyrat({ integrationState, update }: PyratArgs): React.ReactNode {
  const { saveAppOptions, deleteAppOptions } = useIntegrationsEndpoint();
  const { addAlert } = React.useContext(AlertContext);
  const authenticatedServers = useLocalObservable(() => [
    ...integrationState.credentials.authenticatedServers,
  ]);
  const [addMenuAnchorEl, setAddMenuAnchorEl] = useState<null | HTMLElement>(
    null
  );

  const unauthenticatedServers =
    integrationState.credentials.configuredServers.filter(
      ({ alias }) => !authenticatedServers.find((s) => s.alias === alias)
    );

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="PyRAT"
        integrationState={integrationState}
        explanatoryText="Increase efficiency, access, and ensure compliance through a lab animal colony management software."
        image={PyratIcon}
        color={LOGO_COLOR}
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
              <li>Chose the corresponding server from the add menu below.</li>
              <li>
                Enter the access token into the field that appears, and save.
              </li>
              <li>Enable the integration.</li>
              <li>
                When editing a document, click on the PyRAT icon in the text
                editor toolbar.
              </li>
            </ol>
            <Card variant="outlined" sx={{ mt: 2 }}>
              <CardContent>
                <Stack spacing={1}>
                  {authenticatedServers.length === 0 && (
                    <Typography variant="body2">
                      No authenticated servers.
                    </Typography>
                  )}
                  {authenticatedServers.map((server) => (
                    <form
                      key={server.alias}
                      onSubmit={(event) => {
                        event.preventDefault();
                        void saveAppOptions(
                          "PYRAT",
                          Optional.present(server.optionsId),
                          {
                            PYRAT_ALIAS: server.alias,
                            PYRAT_URL: server.url,
                            PYRAT_APIKEY: server.apiKey,
                          }
                        )
                          .then(() => {
                            addAlert(
                              mkAlert({
                                variant: "success",
                                message: "Successfully saved API key.",
                              })
                            );
                          })
                          .catch((e) => {
                            if (e instanceof Error)
                              addAlert(
                                mkAlert({
                                  variant: "error",
                                  title: "Error saving API key.",
                                  message: e.message,
                                })
                              );
                          });
                      }}
                    >
                      <Stack direction="row" spacing={1}>
                        <TextField
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
                        <Button type="submit">Save</Button>
                        <Button
                          onClick={() => {
                            void deleteAppOptions("PYRAT", server.optionsId)
                              .then(() => {
                                runInAction(() => {
                                  const indexOfRemovedServer =
                                    authenticatedServers.findIndex(
                                      (s) => s.alias === server.alias
                                    );
                                  authenticatedServers.splice(
                                    indexOfRemovedServer,
                                    1
                                  );
                                });
                                addAlert(
                                  mkAlert({
                                    variant: "success",
                                    message: "Successfully deleted API key.",
                                  })
                                );
                              })
                              .catch((e) => {
                                if (e instanceof Error)
                                  addAlert(
                                    mkAlert({
                                      variant: "error",
                                      title: "Could not delete API key.",
                                      message: e.message,
                                    })
                                  );
                              });
                          }}
                        >
                          Delete
                        </Button>
                      </Stack>
                    </form>
                  ))}
                </Stack>
              </CardContent>
              <CardActions>
                <Button
                  onClick={(e) => {
                    setAddMenuAnchorEl(e.currentTarget);
                  }}
                  disabled={unauthenticatedServers.length === 0}
                >
                  Add
                </Button>
                <Menu
                  open={Boolean(addMenuAnchorEl)}
                  anchorEl={addMenuAnchorEl}
                  onClose={() => setAddMenuAnchorEl(null)}
                >
                  {unauthenticatedServers.map(({ alias, url }) => (
                    <MenuItem
                      key={alias}
                      onClick={() => {
                        void saveAppOptions("PYRAT", Optional.empty(), {
                          PYRAT_ALIAS: alias,
                          PYRAT_URL: url,
                          PYRAT_APIKEY: "",
                        })
                          .then((newConfigs) => {
                            setAddMenuAnchorEl(null);
                            const optionIdsOfExistingServers = new RsSet(
                              authenticatedServers.map(
                                ({ optionsId }) => optionsId
                              )
                            );
                            const optionIdsOfNewServers = new RsSet(
                              newConfigs.credentials.authenticatedServers.map(
                                ({ optionsId }) => optionsId
                              )
                            );
                            const newOptionId = optionIdsOfNewServers.subtract(
                              optionIdsOfExistingServers
                            ).first;
                            runInAction(() => {
                              authenticatedServers.push({
                                alias,
                                url,
                                apiKey: "",
                                optionsId: newOptionId,
                              });
                              addAlert(
                                mkAlert({
                                  variant: "success",
                                  message:
                                    "Successfully added new PyRAT server.",
                                })
                              );
                            });
                          })
                          .catch((e) => {
                            if (e instanceof Error)
                              addAlert(
                                mkAlert({
                                  variant: "error",
                                  title: "Error added new PyRAT server.",
                                  message: e.message,
                                })
                              );
                          });
                      }}
                    >
                      <ListItemText primary={alias} secondary={url} />
                    </MenuItem>
                  ))}
                </Menu>
              </CardActions>
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

/**
 * The card and dialog for configuring the PyRAT integration
 */
export default React.memo(observer(Pyrat));
