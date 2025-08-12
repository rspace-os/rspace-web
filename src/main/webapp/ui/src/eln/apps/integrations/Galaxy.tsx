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
import {IntegrationStates, useIntegrationsEndpoint} from "@/eln/apps/useIntegrationsEndpoint";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import AlertContext, {mkAlert} from "@/stores/contexts/Alert";
import {runInAction} from "mobx";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import RsSet from "@/util/set";
import ListItemText from "@mui/material/ListItemText";
import {observer, useLocalObservable} from "mobx-react";

type GalaxyArgs = {
  integrationState: IntegrationStates["GALAXY"];
  update: (newIntegrationState: IntegrationStates["GALAXY"]) => void;
};

/*
 * Galaxy uses API-key based authentication, as implemented by the form below.
 */
function Galaxy({ integrationState, update }: GalaxyArgs): React.ReactNode {
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
                Request a user access token by going to username → Preferences →
                'Manage API key' in Galaxy.
              </li>
              <li>Chose the corresponding server from the add menu below.</li>
              <li>
                Enter the access token into the field that appears, and save.
              </li>
              <li>Enable the integration.</li>
              <li>
                When editing a document, click on the Galaxy icon in the text
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
                                "GALAXY",
                                Optional.present(server.optionsId),
                                {
                                  GALAXY_ALIAS: server.alias,
                                  GALAXY_URL: server.url,
                                  GALAXY_APIKEY: server.apiKey,
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
                                void deleteAppOptions("GALAXY", server.optionsId)
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
                            void saveAppOptions("GALAXY", Optional.empty(), {
                              GALAXY_ALIAS: alias,
                              GALAXY_URL: url,
                              GALAXY_APIKEY: "",
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
                                          "Successfully added new Galaxy server.",
                                    })
                                );
                              });
                            })
                            .catch((e) => {
                              if (e instanceof Error)
                                addAlert(
                                    mkAlert({
                                      variant: "error",
                                      title: "Error added new Galaxy server.",
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

export default React.memo(observer(Galaxy));
