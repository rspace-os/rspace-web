//@flow strict

import Grid from "@mui/material/Grid";
import React, {
  type Node,
  useContext,
  useState,
  type AbstractComponent,
} from "react";
import IntegrationCard from "../IntegrationCard";
import {
  useIntegrationsEndpoint,
  type IntegrationStates,
  type IntegrationState,
  type OptionsId,
} from "../useIntegrationsEndpoint";
import Button from "@mui/material/Button";
import { Optional } from "../../../util/optional";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import TextField from "@mui/material/TextField";
import CardActions from "@mui/material/CardActions";
import { useLocalObservable, observer } from "mobx-react-lite";
import { runInAction, observable } from "mobx";
import { doNotAwait } from "../../../util/Util";
import Alert from "../../../stores/models/Alert";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import { useDataverseTestEndpoint } from "../useDataverseTestEndpoint";
import RsSet from "../../../util/set";
import DataverseIcon from "../icons/dataverse.svg";
import Typography from "@mui/material/Typography";
import * as ArrayUtils from "../../../util/ArrayUtils";

const COLOR = {
  hue: 19,
  saturation: 66,
  lightness: 46,
};

/*
 * This is the type of the configurations, as passed into this component. If
 * any of the credentials cannot be parsed from the API call made by
 * useIntegrationsEndpoint then this component should not be rendered. As
 * such, we can discard the Optional wrapper around each Dataverse
 * configuration.
 */
type Configurations = $TupleMap<
  IntegrationStates["DATAVERSE"]["credentials"],
  <A>(Optional<A>) => A
>;

/*
 * Within this component we model the state of the configurations slightly
 * differently, storing state information specific to the fields of the form.
 */

type ExistingConfig = {|
  DATAVERSE_APIKEY: string,
  DATAVERSE_URL: string,
  DATAVERSE_ALIAS: string,
  _label: string,
  optionsId: OptionsId,
  dirty: boolean,
|};

type NewConfig = {|
  DATAVERSE_APIKEY: string,
  DATAVERSE_URL: string,
  DATAVERSE_ALIAS: string,
|};

const AddButton = ({
  newConfig,
  setNewConfig,
}: {|
  newConfig: NewConfig | null,
  setNewConfig: (NewConfig) => void,
|}) => {
  return (
    <Button
      disabled={Boolean(newConfig)}
      onClick={() => {
        setNewConfig(
          observable({
            DATAVERSE_ALIAS: "",
            DATAVERSE_APIKEY: "",
            DATAVERSE_URL: "",
          })
        );
      }}
    >
      Add
    </Button>
  );
};

const DialogContent = observer(
  ({
    configs,
    integrationState,
  }: {|
    configs: Configurations,
    integrationState: IntegrationStates["DATAVERSE"],
  |}) => {
    const { addAlert } = useContext(AlertContext);
    const { test } = useDataverseTestEndpoint();
    const { saveAppOptions, deleteAppOptions } = useIntegrationsEndpoint();

    /*
     * We take a copy of the current state for the user to edit in the UI. When
     * they save it, it gets replaced with the new state returned from the API
     * call in a careful manner so that each configuration can be
     * independently edited and saved.
     */
    const copyOfState = useLocalObservable<
      IntegrationState<Array<ExistingConfig>>
    >(() => ({
      mode: integrationState.mode,
      credentials: configs.map((c) => observable({ ...c, dirty: false })),
    }));
    const observableConfigs = copyOfState.credentials;

    const [newConfig, setNewConfig] = useState<NewConfig | null>(null);

    async function saveExistingConfig(config: ExistingConfig, index: number) {
      try {
        const newState = await saveAppOptions(
          "DATAVERSE",
          Optional.present(config.optionsId),
          {
            DATAVERSE_ALIAS: config.DATAVERSE_ALIAS,
            DATAVERSE_URL: config.DATAVERSE_URL,
            DATAVERSE_APIKEY: config.DATAVERSE_APIKEY,
          }
        );
        runInAction(() => {
          integrationState.credentials = newState.credentials;
          ArrayUtils.all(newState.credentials)
            .map((newCreds) => {
              const indexOfNewConfig = newCreds.findIndex(
                (c) => c.optionsId === config.optionsId
              );
              if (indexOfNewConfig === -1)
                throw new Error("Save completed but cannot show results.");

              copyOfState.credentials.splice(
                index,
                1,
                observable({
                  ...newCreds[indexOfNewConfig],
                  dirty: false,
                })
              );
            })
            .orElseGet(() => {
              throw new Error("Save completed but cannot show results.");
            });
        });
        addAlert(
          mkAlert({
            variant: "success",
            message: "Successfully saved Dataverse details.",
          })
        );
      } catch (e) {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Error saving Dataverse configuration.",
            message: e.message,
          })
        );
      }
    }

    async function saveNewConfig(config: NewConfig) {
      try {
        const newState = await saveAppOptions("DATAVERSE", Optional.empty(), {
          DATAVERSE_ALIAS: config.DATAVERSE_ALIAS,
          DATAVERSE_URL: config.DATAVERSE_URL,
          DATAVERSE_APIKEY: config.DATAVERSE_APIKEY,
        });
        runInAction(() => {
          integrationState.credentials = newState.credentials;
          const optionIdsOfExistingConfigs = new RsSet(
            copyOfState.credentials.map(({ optionsId }) => optionsId)
          );
          try {
            const newlySavedConfig = new RsSet(newState.credentials)
              .mapOptional((x) => x)
              .subtractMap(
                ({ optionsId }) => optionsId,
                optionIdsOfExistingConfigs
              ).first;
            copyOfState.credentials.push(
              observable({ ...newlySavedConfig, dirty: false })
            );
          } catch (e) {
            throw new Error("Save completed but cannot show results.");
          }
        });
        setNewConfig(null);
        addAlert(
          mkAlert({
            variant: "success",
            message: "Successfully saved Dataverse details",
          })
        );
      } catch (e) {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Could not save Dataverse details",
            message: e.message,
          })
        );
      }
    }

    return (
      <>
        <Grid container direction="column" spacing={1} sx={{ mt: 1 }}>
          <Grid item container direction="column" spacing={1}>
            {observableConfigs.map((config, i) => (
              <Grid item key={i}>
                <Card variant="outlined">
                  <form
                    onSubmit={(event) => {
                      event.preventDefault();
                      void saveExistingConfig(config, i);
                    }}
                    aria-label={`Configured Dataverse with name ${config.DATAVERSE_ALIAS}`}
                  >
                    <CardContent>
                      <Grid container direction="column" spacing={2}>
                        <Grid item>
                          <TextField
                            fullWidth
                            value={config.DATAVERSE_ALIAS}
                            onChange={({ target: { value } }) => {
                              runInAction(() => {
                                config.DATAVERSE_ALIAS = value;
                                config.dirty = true;
                              });
                            }}
                            label="Dataverse Name"
                            error={config.DATAVERSE_ALIAS === ""}
                            helperText={
                              config.DATAVERSE_ALIAS === "" &&
                              "Name is required."
                            }
                          />
                        </Grid>
                        <Grid item>
                          <TextField
                            fullWidth
                            value={config.DATAVERSE_URL}
                            onChange={({ target: { value } }) => {
                              runInAction(() => {
                                config.DATAVERSE_URL = value;
                                config.dirty = true;
                              });
                            }}
                            label="Server URL"
                            error={config.DATAVERSE_URL === ""}
                            helperText={
                              config.DATAVERSE_URL === "" && "URL is required."
                            }
                          />
                        </Grid>
                        <Grid item>
                          <TextField
                            fullWidth
                            value={config.DATAVERSE_APIKEY}
                            onChange={({ target: { value } }) => {
                              runInAction(() => {
                                config.DATAVERSE_APIKEY = value;
                                config.dirty = true;
                              });
                            }}
                            type="password"
                            label="API key"
                            error={config.DATAVERSE_APIKEY === ""}
                            helperText={
                              config.DATAVERSE_APIKEY === "" &&
                              "API key is required."
                            }
                          />
                        </Grid>
                      </Grid>
                    </CardContent>
                    <CardActions>
                      <Button
                        onClick={doNotAwait(async () => {
                          try {
                            await deleteAppOptions(
                              "DATAVERSE",
                              config.optionsId
                            );
                            runInAction(() => {
                              const deletedIndex = observableConfigs.findIndex(
                                (c) => c === config
                              );
                              observableConfigs.splice(deletedIndex, 1);
                              integrationState.credentials.splice(
                                deletedIndex,
                                1
                              );
                            });
                            addAlert(
                              mkAlert({
                                variant: "success",
                                message: "Successfully deleted configuration.",
                              })
                            );
                          } catch (e) {
                            addAlert(
                              mkAlert({
                                variant: "error",
                                title: "Could not delete configuration.",
                                message: e.message,
                              })
                            );
                          }
                        })}
                      >
                        Delete
                      </Button>
                      <Button
                        disabled={config.dirty}
                        onClick={doNotAwait(async () => {
                          try {
                            await test(config.optionsId);
                            addAlert(
                              mkAlert({
                                variant: "success",
                                message: "Connection details are valid.",
                              })
                            );
                          } catch (e) {
                            addAlert(
                              mkAlert({
                                variant: "error",
                                title: "Connection details are not valid.",
                                message: e.message,
                              })
                            );
                          }
                        })}
                      >
                        Test
                      </Button>
                      <Button
                        type="submit"
                        disabled={
                          config.DATAVERSE_ALIAS === "" ||
                          config.DATAVERSE_URL === "" ||
                          config.DATAVERSE_APIKEY === ""
                        }
                      >
                        Save
                      </Button>
                    </CardActions>
                  </form>
                </Card>
              </Grid>
            ))}
            {newConfig && (
              <Grid item key={null}>
                <Card variant="outlined">
                  <form
                    onSubmit={(event) => {
                      event.preventDefault();
                      void saveNewConfig(newConfig);
                    }}
                  >
                    <CardContent>
                      <Grid container direction="column" spacing={2}>
                        <Grid item>
                          <TextField
                            fullWidth
                            value={newConfig.DATAVERSE_ALIAS}
                            onChange={({ target: { value } }) => {
                              runInAction(() => {
                                newConfig.DATAVERSE_ALIAS = value;
                              });
                            }}
                            label="Dataverse Name"
                            error={newConfig.DATAVERSE_ALIAS === ""}
                            helperText={
                              newConfig.DATAVERSE_ALIAS === "" &&
                              "Name is required."
                            }
                          />
                        </Grid>
                        <Grid item>
                          <TextField
                            fullWidth
                            value={newConfig.DATAVERSE_URL}
                            onChange={({ target: { value } }) => {
                              runInAction(() => {
                                newConfig.DATAVERSE_URL = value;
                              });
                            }}
                            label="Server URL"
                            error={newConfig.DATAVERSE_URL === ""}
                            helperText={
                              newConfig.DATAVERSE_URL === "" &&
                              "URL is required."
                            }
                          />
                        </Grid>
                        <Grid item>
                          <TextField
                            fullWidth
                            value={newConfig.DATAVERSE_APIKEY}
                            onChange={({ target: { value } }) => {
                              runInAction(() => {
                                newConfig.DATAVERSE_APIKEY = value;
                              });
                            }}
                            type="password"
                            label="API key"
                            error={newConfig.DATAVERSE_APIKEY === ""}
                            helperText={
                              newConfig.DATAVERSE_APIKEY === "" &&
                              "API key is required."
                            }
                          />
                        </Grid>
                      </Grid>
                    </CardContent>
                    <CardActions>
                      <Button
                        variant="outlined"
                        onClick={() => {
                          setNewConfig(null);
                        }}
                      >
                        Delete
                      </Button>
                      <Button
                        variant="outlined"
                        // always disabled because it cannot be tested until saved
                        disabled
                        onClick={() => {}}
                      >
                        Test
                      </Button>
                      <Button
                        variant="outlined"
                        type="submit"
                        disabled={
                          newConfig.DATAVERSE_ALIAS === "" ||
                          newConfig.DATAVERSE_URL === "" ||
                          newConfig.DATAVERSE_APIKEY === ""
                        }
                      >
                        Save
                      </Button>
                    </CardActions>
                  </form>
                </Card>
              </Grid>
            )}
          </Grid>
          <Grid item>
            <Grid container direction="row" spacing={1}>
              <Grid item>
                <AddButton newConfig={newConfig} setNewConfig={setNewConfig} />
              </Grid>
              <Grid item></Grid>
            </Grid>
          </Grid>
        </Grid>
      </>
    );
  }
);

type DataverseArgs = {|
  /*
   * This is the current state of the Dataverse integration. It must be a mobx
   * observable because this component will mutate it when a new dataverse is
   * added or an existing one is removed from the configured credentials.
   */
  integrationState: IntegrationStates["DATAVERSE"],

  /*
   * Event handler that is called when the user enables or disabled the
   * integration. Not called at any other time.
   */
  update: (IntegrationStates["DATAVERSE"]) => void,
|};

function Dataverse({ integrationState, update }: DataverseArgs): Node {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Dataverse"
        explanatoryText="Explore, analyse, and share data through an open-source research data repository software."
        image={DataverseIcon}
        color={COLOR}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        integrationState={integrationState}
        helpLinkText="Dataverse integration docs"
        website="dataverse.org"
        docLink="dataverse"
        usageText="You can export your files and data directly from RSpace to
            Dataverse. You are able to specify various metadata and controlled
            vocabulary terms for the deposit."
        setupSection={
          <>
            <Typography variant="body2">
              You can configure multiple Dataverses to connect to.
            </Typography>
            <ol>
              <li>Enter the required credentials and Save.</li>
              <li>Click on Test to ensure your credentials are correct.</li>
              <li>Enable the integration.</li>
              <li>
                Dataverse will now be available as an option in the export
                dialog.
              </li>
            </ol>
            {ArrayUtils.all(integrationState.credentials)
              .map((configs) => (
                <DialogContent
                  key={null}
                  configs={configs}
                  integrationState={integrationState}
                />
              ))
              .orElse(
                <>There was an error getting the configured Dataverses.</>
              )}
          </>
        }
      />
    </Grid>
  );
}

export default (React.memo(
  observer(Dataverse)
): AbstractComponent<DataverseArgs>);
