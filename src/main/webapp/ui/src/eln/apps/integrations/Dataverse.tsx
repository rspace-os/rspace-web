import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { observable, runInAction } from "mobx";
import { observer, useLocalObservable } from "mobx-react-lite";
import React, { useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "../../../assets/branding/dataverse";
import DataverseIcon from "../../../assets/branding/dataverse/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as ArrayUtils from "../../../util/ArrayUtils";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import { useDataverseTestEndpoint } from "../useDataverseTestEndpoint";
import {
  type IntegrationState,
  type IntegrationStates,
  type OptionsId,
  useIntegrationsEndpoint,
} from "../useIntegrationsEndpoint";

type UnwrapOptional<T> = T extends Optional<infer U> ? U : T;

type UnwrapArray<T extends Array<unknown>> = {
  [K in keyof T]: UnwrapOptional<T[K]>;
};

/*
 * This is the type of the configurations, as passed into this component. If
 * any of the credentials cannot be parsed from the API call made by
 * useIntegrationsEndpoint then this component should not be rendered. As
 * such, we can discard the Optional wrapper around each Dataverse
 * configuration.
 */
type Configurations = UnwrapArray<IntegrationStates["DATAVERSE"]["credentials"]>;

/*
 * Within this component we model the state of the configurations slightly
 * differently, storing state information specific to the fields of the form.
 */

type ExistingConfig = {
  DATAVERSE_APIKEY: string;
  DATAVERSE_URL: string;
  DATAVERSE_ALIAS: string;
  _label: string;
  optionsId: OptionsId;
  dirty: boolean;
};

type NewConfig = {
  DATAVERSE_APIKEY: string;
  DATAVERSE_URL: string;
  DATAVERSE_ALIAS: string;
};

const DialogContent = observer(
  ({ configs, integrationState }: { configs: Configurations; integrationState: IntegrationStates["DATAVERSE"] }) => {
    const { t } = useTranslation("apps");
    const { addAlert } = useContext(AlertContext);
    const { test } = useDataverseTestEndpoint();
    const { saveAppOptions, deleteAppOptions } = useIntegrationsEndpoint();

    /*
     * We take a copy of the current state for the user to edit in the UI. When
     * they save it, it gets replaced with the new state returned from the API
     * call in a careful manner so that each configuration can be
     * independently edited and saved.
     */
    const copyOfState = useLocalObservable<IntegrationState<Array<ExistingConfig>>>(() => ({
      mode: integrationState.mode,
      credentials: configs.map((c) => observable({ ...c, dirty: false })),
    }));
    const observableConfigs = copyOfState.credentials;

    const [newConfig, setNewConfig] = useState<NewConfig | null>(null);

    async function saveExistingConfig(config: ExistingConfig, index: number) {
      try {
        const newState = await saveAppOptions("DATAVERSE", Optional.present(config.optionsId), {
          DATAVERSE_ALIAS: config.DATAVERSE_ALIAS,
          DATAVERSE_URL: config.DATAVERSE_URL,
          DATAVERSE_APIKEY: config.DATAVERSE_APIKEY,
        });
        runInAction(() => {
          integrationState.credentials = newState.credentials;
          ArrayUtils.all(newState.credentials)
            // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
            .map((newCreds) => {
              const indexOfNewConfig = newCreds.findIndex((c) => c.optionsId === config.optionsId);
              if (indexOfNewConfig === -1) throw new Error("Save completed but cannot show results.");

              copyOfState.credentials.splice(
                index,
                1,
                observable({
                  ...newCreds[indexOfNewConfig],
                  dirty: false,
                }),
              );
            })
            .orElseGet(() => {
              throw new Error("Save completed but cannot show results.");
            });
        });
        addAlert(
          mkAlert({
            variant: "success",
            message: t("integrations.dataverse.alerts.saveExistingSuccess"),
          }),
        );
      } catch (e) {
        if (e instanceof Error)
          addAlert(
            mkAlert({
              variant: "error",
              title: t("integrations.dataverse.alerts.saveExistingError"),
              message: e.message,
            }),
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
          const optionIdsOfExistingConfigs = new Set(copyOfState.credentials.map(({ optionsId }) => optionsId));
          try {
            const newlySavedConfig = ArrayUtils.mapOptional((credential) => credential, newState.credentials).find(
              ({ optionsId }) => !optionIdsOfExistingConfigs.has(optionsId),
            );
            copyOfState.credentials.push(
              observable({
                ...(newlySavedConfig ?? { ...config, _label: config.DATAVERSE_ALIAS, optionsId: "" }),
                dirty: false,
              }),
            );
          } catch {
            throw new Error("Save completed but cannot show results.");
          }
        });
        setNewConfig(null);
        addAlert(
          mkAlert({
            variant: "success",
            message: t("integrations.dataverse.alerts.saveNewSuccess"),
          }),
        );
      } catch (e) {
        if (e instanceof Error)
          addAlert(
            mkAlert({
              variant: "error",
              title: t("integrations.dataverse.alerts.saveNewError"),
              message: e.message,
            }),
          );
      }
    }

    return (
      <Stack spacing={1} sx={{ mt: 1 }}>
        <Stack spacing={1}>
          {observableConfigs.map((config, i) => (
            <Card key={i} variant="outlined">
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void saveExistingConfig(config, i);
                }}
                aria-label={t("integrations.dataverse.configuredAriaLabel", { name: config.DATAVERSE_ALIAS })}
              >
                <CardContent>
                  <Stack spacing={2}>
                    <TextField
                      fullWidth
                      value={config.DATAVERSE_ALIAS}
                      onChange={({ target: { value } }) => {
                        runInAction(() => {
                          config.DATAVERSE_ALIAS = value;
                          config.dirty = true;
                        });
                      }}
                      label={t("integrations.dataverse.fields.alias")}
                      error={config.DATAVERSE_ALIAS === ""}
                      helperText={config.DATAVERSE_ALIAS === "" && t("integrations.dataverse.fields.aliasRequired")}
                    />
                    <TextField
                      fullWidth
                      value={config.DATAVERSE_URL}
                      onChange={({ target: { value } }) => {
                        runInAction(() => {
                          config.DATAVERSE_URL = value;
                          config.dirty = true;
                        });
                      }}
                      label={t("integrations.dataverse.fields.serverUrl")}
                      error={config.DATAVERSE_URL === ""}
                      helperText={config.DATAVERSE_URL === "" && t("integrations.dataverse.fields.urlRequired")}
                    />
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
                      label={t("integrations.dataverse.fields.apiKey")}
                      error={config.DATAVERSE_APIKEY === ""}
                      helperText={config.DATAVERSE_APIKEY === "" && t("integrations.dataverse.fields.apiKeyRequired")}
                    />
                  </Stack>
                </CardContent>
                <CardActions>
                  <Button
                    onClick={() => {
                      void (async () => {
                        try {
                          await deleteAppOptions("DATAVERSE", config.optionsId);
                          runInAction(() => {
                            const deletedIndex = observableConfigs.indexOf(config);
                            observableConfigs.splice(deletedIndex, 1);
                            integrationState.credentials.splice(deletedIndex, 1);
                          });
                          addAlert(
                            mkAlert({
                              variant: "success",
                              message: t("integrations.dataverse.alerts.deleteConfigSuccess"),
                            }),
                          );
                        } catch (e) {
                          if (e instanceof Error)
                            addAlert(
                              mkAlert({
                                variant: "error",
                                title: t("integrations.dataverse.alerts.deleteConfigError"),
                                message: e.message,
                              }),
                            );
                        }
                      })();
                    }}
                  >
                    {t("actions.delete")}
                  </Button>
                  <Button
                    disabled={config.dirty}
                    onClick={() => {
                      void (async () => {
                        try {
                          await test(config.optionsId);
                          addAlert(
                            mkAlert({
                              variant: "success",
                              message: t("integrations.dataverse.alerts.testValid"),
                            }),
                          );
                        } catch (e) {
                          if (e instanceof Error)
                            addAlert(
                              mkAlert({
                                variant: "error",
                                title: t("integrations.dataverse.alerts.testInvalid"),
                                message: e.message,
                              }),
                            );
                        }
                      })();
                    }}
                  >
                    {t("actions.test")}
                  </Button>
                  <Button
                    type="submit"
                    disabled={
                      config.DATAVERSE_ALIAS === "" || config.DATAVERSE_URL === "" || config.DATAVERSE_APIKEY === ""
                    }
                  >
                    {t("actions.save")}
                  </Button>
                </CardActions>
              </form>
            </Card>
          ))}
          {newConfig && (
            <Card variant="outlined">
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void saveNewConfig(newConfig);
                }}
              >
                <CardContent>
                  <Stack spacing={2}>
                    <TextField
                      fullWidth
                      value={newConfig.DATAVERSE_ALIAS}
                      onChange={({ target: { value } }) => {
                        runInAction(() => {
                          newConfig.DATAVERSE_ALIAS = value;
                        });
                      }}
                      label={t("integrations.dataverse.fields.alias")}
                      error={newConfig.DATAVERSE_ALIAS === ""}
                      helperText={newConfig.DATAVERSE_ALIAS === "" && t("integrations.dataverse.fields.aliasRequired")}
                    />
                    <TextField
                      fullWidth
                      value={newConfig.DATAVERSE_URL}
                      onChange={({ target: { value } }) => {
                        runInAction(() => {
                          newConfig.DATAVERSE_URL = value;
                        });
                      }}
                      label={t("integrations.dataverse.fields.serverUrl")}
                      error={newConfig.DATAVERSE_URL === ""}
                      helperText={newConfig.DATAVERSE_URL === "" && t("integrations.dataverse.fields.urlRequired")}
                    />
                    <TextField
                      fullWidth
                      value={newConfig.DATAVERSE_APIKEY}
                      onChange={({ target: { value } }) => {
                        runInAction(() => {
                          newConfig.DATAVERSE_APIKEY = value;
                        });
                      }}
                      type="password"
                      label={t("integrations.dataverse.fields.apiKey")}
                      error={newConfig.DATAVERSE_APIKEY === ""}
                      helperText={
                        newConfig.DATAVERSE_APIKEY === "" && t("integrations.dataverse.fields.apiKeyRequired")
                      }
                    />
                  </Stack>
                </CardContent>
                <CardActions>
                  <Button
                    variant="outlined"
                    onClick={() => {
                      setNewConfig(null);
                    }}
                  >
                    {t("actions.delete")}
                  </Button>
                  <Button
                    variant="outlined"
                    // always disabled because it cannot be tested until saved
                    disabled
                    onClick={() => {}}
                  >
                    {t("actions.test")}
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
                    {t("actions.save")}
                  </Button>
                </CardActions>
              </form>
            </Card>
          )}
        </Stack>
        <Button
          sx={{ width: "fit-content" }}
          disabled={Boolean(newConfig)}
          onClick={() => {
            setNewConfig(
              observable({
                DATAVERSE_ALIAS: "",
                DATAVERSE_APIKEY: "",
                DATAVERSE_URL: "",
              }),
            );
          }}
        >
          {t("actions.add")}
        </Button>
      </Stack>
    );
  },
);

type DataverseArgs = {
  /*
   * This is the current state of the Dataverse integration. It must be a mobx
   * observable because this component will mutate it when a new dataverse is
   * added or an existing one is removed from the configured credentials.
   */
  integrationState: IntegrationStates["DATAVERSE"];

  /*
   * Event handler that is called when the user enables or disabled the
   * integration. Not called at any other time.
   */
  update: (newIntegrationState: IntegrationStates["DATAVERSE"]) => void;
};

function Dataverse({ integrationState, update }: DataverseArgs): React.ReactNode {
  const { t } = useTranslation("apps");
  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.dataverse.name")}
        explanatoryText={t("integrations.dataverse.description")}
        image={DataverseIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: integrationState.credentials })}
        integrationState={integrationState}
        helpLinkText={t("integrations.dataverse.helpLink")}
        website="dataverse.org"
        docLink="dataverse"
        usageText={t("integrations.dataverse.usage")}
        setupSection={
          <>
            <Typography variant="body2">{t("integrations.dataverse.multipleConfig")}</Typography>
            <ol>
              <li>{t("integrations.dataverse.setup.enterCredentials")}</li>
              <li>{t("integrations.dataverse.setup.test")}</li>
              <li>{t("integrations.dataverse.setup.enable")}</li>
              <li>{t("integrations.dataverse.setup.exportDialog")}</li>
            </ol>
            {ArrayUtils.all(integrationState.credentials)
              .map((configs) => <DialogContent key={null} configs={configs} integrationState={integrationState} />)
              .orElse(t("integrations.dataverse.errorGettingConfigs"))}
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(observer(Dataverse));
