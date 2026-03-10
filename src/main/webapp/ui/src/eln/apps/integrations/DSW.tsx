import Grid from "@mui/material/Grid";
import React, { useContext, useState } from "react";
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
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import { useDSWTestEndpoint } from "../useDSWTestEndpoint";
import RsSet from "../../../util/set";
import DSWIcon from "../../../assets/branding/dsw/logo.svg";
import Typography from "@mui/material/Typography";
import * as ArrayUtils from "../../../util/ArrayUtils";
import { LOGO_COLOR } from "../../../assets/branding/dsw";

type UnwrapOptional<T> = T extends Optional<infer U> ? U : T;

type UnwrapArray<T extends Array<unknown>> = {
  [K in keyof T]: UnwrapOptional<T[K]>;
};

/*
 * This is the type of the configurations, as passed into this component. If
 * any of the credentials cannot be parsed from the API call made by
 * useIntegrationsEndpoint then this component should not be rendered. As
 * such, we can discard the Optional wrapper around each DSW
 * configuration.
 */
type Configurations = UnwrapArray<
    IntegrationStates["DSW"]["credentials"]
>;

/*
 * Within this component we model the state of the configurations slightly
 * differently, storing state information specific to the fields of the form.
 */

type ExistingConfig = {
  DSW_APIKEY: string;
  DSW_URL: string;
  DSW_ALIAS: string;
  optionsId: OptionsId;
  dirty: boolean;
};

type NewConfig = {
  DSW_APIKEY: string;
  DSW_URL: string;
  DSW_ALIAS: string;
};

const AddButton = ({
                     newConfig,
                     setNewConfig,
                   }: {
  newConfig: NewConfig | null;
  setNewConfig: (newConfig: NewConfig) => void;
}) => {
  return (
      <Button
          disabled={Boolean(newConfig)}
          onClick={() => {
            setNewConfig(
                observable({
                  DSW_ALIAS: "",
                  DSW_APIKEY: "",
                  DSW_URL: "",
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
     }: {
      configs: Configurations;
      integrationState: IntegrationStates["DSW"];
    }) => {
      const { addAlert } = useContext(AlertContext);
      const { test } = useDSWTestEndpoint();
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
              "DSW",
              Optional.present(config.optionsId),
              {
                DSW_ALIAS: config.DSW_ALIAS,
                DSW_URL: config.DSW_URL,
                DSW_APIKEY: config.DSW_APIKEY,
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
                message: "Successfully saved DSW details.",
              })
          );
        } catch (e) {
          if (e instanceof Error)
            addAlert(
                mkAlert({
                  variant: "error",
                  title: "Error saving DSW configuration.",
                  message: e.message,
                })
            );
        }
      }

      async function saveNewConfig(config: NewConfig) {
        try {
          const newState = await saveAppOptions("DSW", Optional.empty(), {
            DSW_ALIAS: config.DSW_ALIAS,
            DSW_URL: config.DSW_URL,
            DSW_APIKEY: config.DSW_APIKEY,
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
                message: "Successfully saved DSW details",
              })
          );
        } catch (e) {
          if (e instanceof Error)
            addAlert(
                mkAlert({
                  variant: "error",
                  title: "Could not save DSW details",
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
                            aria-label={`Configured DSW with connection alias ${config.DSW_ALIAS}`}
                        >
                          <CardContent>
                            <Grid container direction="column" spacing={2}>
                              <Grid item>
                                <TextField
                                    fullWidth
                                    value={config.DSW_ALIAS}
                                    onChange={({ target: { value } }) => {
                                      runInAction(() => {
                                        config.DSW_ALIAS = value;
                                        config.dirty = true;
                                      });
                                    }}
                                    label="DSW Connection Alias"
                                    error={config.DSW_ALIAS === ""}
                                    helperText={
                                        config.DSW_ALIAS === "" &&
                                        "Connection alias is required."
                                    }
                                />
                              </Grid>
                              <Grid item>
                                <TextField
                                    fullWidth
                                    value={config.DSW_URL}
                                    onChange={({ target: { value } }) => {
                                      runInAction(() => {
                                        config.DSW_URL = value;
                                        config.dirty = true;
                                      });
                                    }}
                                    label="Server URL"
                                    error={config.DSW_URL === ""}
                                    helperText={
                                        config.DSW_URL === "" && "URL is required."
                                    }
                                />
                              </Grid>
                              <Grid item>
                                <TextField
                                    fullWidth
                                    value={config.DSW_APIKEY}
                                    onChange={({ target: { value } }) => {
                                      runInAction(() => {
                                        config.DSW_APIKEY = value;
                                        config.dirty = true;
                                      });
                                    }}
                                    type="password"
                                    label="API key"
                                    error={config.DSW_APIKEY === ""}
                                    helperText={
                                        config.DSW_APIKEY === "" &&
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
                                        "DSW",
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
                                    if (e instanceof Error)
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
                                    await test(config.DSW_ALIAS);
                                    addAlert(
                                        mkAlert({
                                          variant: "success",
                                          message: "Connection details are valid.",
                                        })
                                    );
                                  } catch (e) {
                                    if (e instanceof Error)
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
                                    config.DSW_ALIAS === "" ||
                                    config.DSW_URL === "" ||
                                    config.DSW_APIKEY === ""
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
                                    value={newConfig.DSW_ALIAS}
                                    onChange={({ target: { value } }) => {
                                      runInAction(() => {
                                        newConfig.DSW_ALIAS = value;
                                      });
                                    }}
                                    label="DSW Connection Alias"
                                    error={newConfig.DSW_ALIAS === ""}
                                    helperText={
                                        newConfig.DSW_ALIAS === "" &&
                                        "Connection alias is required."
                                    }
                                />
                              </Grid>
                              <Grid item>
                                <TextField
                                    fullWidth
                                    value={newConfig.DSW_URL}
                                    onChange={({ target: { value } }) => {
                                      runInAction(() => {
                                        newConfig.DSW_URL = value;
                                      });
                                    }}
                                    label="Server URL"
                                    error={newConfig.DSW_URL === ""}
                                    helperText={
                                        newConfig.DSW_URL === "" &&
                                        "URL is required."
                                    }
                                />
                              </Grid>
                              <Grid item>
                                <TextField
                                    fullWidth
                                    value={newConfig.DSW_APIKEY}
                                    onChange={({ target: { value } }) => {
                                      runInAction(() => {
                                        newConfig.DSW_APIKEY = value;
                                      });
                                    }}
                                    type="password"
                                    label="API key"
                                    error={newConfig.DSW_APIKEY === ""}
                                    helperText={
                                        newConfig.DSW_APIKEY === "" &&
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
                                    newConfig.DSW_ALIAS === "" ||
                                    newConfig.DSW_URL === "" ||
                                    newConfig.DSW_APIKEY === ""
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

type DSWArgs = {
  /*
   * This is the current state of the DSW integration. It must be a mobx
   * observable because this component will mutate it when a new DSW is
   * added or an existing one is removed from the configured credentials.
   */
  integrationState: IntegrationStates["DSW"];

  /*
   * Event handler that is called when the user enables or disabled the
   * integration. Not called at any other time.
   */
  update: (newIntegrationState: IntegrationStates["DSW"]) => void;
};

function DSW({
                     integrationState,
                     update,
                   }: DSWArgs): React.ReactNode {

  return (
      <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
        <IntegrationCard
            name="DSW / FAIR Wizard"
            integrationState={integrationState}
            explanatoryText="Import Data Management Plans from Data Stewardship Wizard or FAIR Wizard."
            image={DSWIcon}
            color={LOGO_COLOR}
            update={(newMode) =>
                update({ mode: newMode, credentials: integrationState.credentials })
            }
            helpLinkText="DSW integration docs"
            website="researchers.dsw.elixir-europe.org"
            docLink="dmptool"
            usageText="You can import projects from Data Stewardship Wizard or FAIR Wizard into RSpace, and associate them as Data Management Plans with repository exports."
            setupSection={
              <>
                <Typography variant="body2">
                  You can configure multiple DSW or FAIR Wizard instances to connect to.
                </Typography>
                <ol>
                  <li>Enter the required credentials and Save.</li>
                  <li>Click on Test to ensure your credentials are correct.</li>
                  <li>Enable the integration.</li>
                  <li>
                    You can now import a DSW or FAIR Wizard project as a DMP when in the
                    Gallery, and associate that DMP with data when in the export dialog.
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
                        <>There was an error getting the configured DSW instances.</>
                    )}
              </>
            }
        />
      </Grid>
  );
}

export default React.memo(observer(DSW));
