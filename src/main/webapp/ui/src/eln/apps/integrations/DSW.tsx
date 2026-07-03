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
import TransRichText from "@/modules/common/i18n/TransRichText";
import AnalyticsContext from "@/stores/contexts/Analytics";
import { LOGO_COLOR } from "../../../assets/branding/dsw";
import DSWIcon from "../../../assets/branding/dsw/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as ArrayUtils from "../../../util/ArrayUtils";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import { useDSWTestEndpoint } from "../useDSWTestEndpoint";
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
 * such, we can discard the Optional wrapper around each DSW
 * configuration.
 */
type Configurations = UnwrapArray<IntegrationStates["DSW"]["credentials"]>;

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

const DialogContent = observer(
  ({ configs, integrationState }: { configs: Configurations; integrationState: IntegrationStates["DSW"] }) => {
    const { t } = useTranslation(["apps", "common"]);
    const { addAlert } = useContext(AlertContext);
    const { test } = useDSWTestEndpoint();
    const { saveAppOptions, deleteAppOptions } = useIntegrationsEndpoint();
    const { trackEvent } = React.useContext(AnalyticsContext);

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
        const newState = await saveAppOptions("DSW", Optional.present(config.optionsId), {
          DSW_ALIAS: config.DSW_ALIAS,
          DSW_URL: config.DSW_URL,
          DSW_APIKEY: config.DSW_APIKEY,
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
            message: t("integrations.dsw.alerts.saveExistingSuccess"),
          }),
        );
      } catch (e) {
        if (e instanceof Error)
          addAlert(
            mkAlert({
              variant: "error",
              title: t("integrations.dsw.alerts.saveExistingError"),
              message: e.message,
            }),
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
          const optionIdsOfExistingConfigs = new Set(copyOfState.credentials.map(({ optionsId }) => optionsId));
          try {
            const newlySavedConfig = ArrayUtils.mapOptional((credential) => credential, newState.credentials).find(
              ({ optionsId }) => !optionIdsOfExistingConfigs.has(optionsId),
            );
            copyOfState.credentials.push(
              observable({ ...(newlySavedConfig ?? { ...config, optionsId: "" }), dirty: false }),
            );
          } catch {
            throw new Error("Save completed but cannot show results.");
          }
        });
        setNewConfig(null);
        addAlert(
          mkAlert({
            variant: "success",
            message: t("integrations.dsw.alerts.saveNewSuccess"),
          }),
        );
      } catch (e) {
        if (e instanceof Error)
          addAlert(
            mkAlert({
              variant: "error",
              title: t("integrations.dsw.alerts.saveNewError"),
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
                aria-label={t("integrations.dsw.configuredLabel", { label: config.DSW_ALIAS })}
              >
                <CardContent>
                  <Stack spacing={2}>
                    <TextField
                      fullWidth
                      value={config.DSW_ALIAS}
                      onChange={({ target: { value } }) => {
                        runInAction(() => {
                          config.DSW_ALIAS = value;
                          config.dirty = true;
                        });
                      }}
                      label={t("integrations.dsw.fields.label")}
                      error={config.DSW_ALIAS === ""}
                      helperText={config.DSW_ALIAS === "" && t("integrations.dsw.fields.labelRequired")}
                    />
                    <TextField
                      fullWidth
                      value={config.DSW_URL}
                      onChange={({ target: { value } }) => {
                        runInAction(() => {
                          config.DSW_URL = value;
                          config.dirty = true;
                        });
                      }}
                      label={t("integrations.dsw.fields.serverUrl")}
                      error={config.DSW_URL === ""}
                      helperText={config.DSW_URL === "" && t("integrations.dsw.fields.urlRequired")}
                    />
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
                      label={t("integrations.dsw.fields.apiKey")}
                      error={config.DSW_APIKEY === ""}
                      helperText={config.DSW_APIKEY === "" && t("integrations.dsw.fields.apiKeyRequired")}
                    />
                  </Stack>
                </CardContent>
                <CardActions>
                  <Button
                    onClick={() => {
                      void (async () => {
                        trackEvent("user:delete:dsw_connection:apps");
                        try {
                          await deleteAppOptions("DSW", config.optionsId);
                          runInAction(() => {
                            const deletedIndex = observableConfigs.indexOf(config);
                            observableConfigs.splice(deletedIndex, 1);
                            integrationState.credentials.splice(deletedIndex, 1);
                          });
                          addAlert(
                            mkAlert({
                              variant: "success",
                              message: t("integrations.dsw.alerts.deleteConfigSuccess"),
                            }),
                          );
                        } catch (e) {
                          if (e instanceof Error)
                            addAlert(
                              mkAlert({
                                variant: "error",
                                title: t("integrations.dsw.alerts.deleteConfigError"),
                                message: e.message,
                              }),
                            );
                        }
                      })();
                    }}
                  >
                    {t("common:actions.delete")}
                  </Button>
                  <Button
                    disabled={config.dirty}
                    onClick={() => {
                      void (async () => {
                        try {
                          await test(config.DSW_ALIAS);
                          addAlert(
                            mkAlert({
                              variant: "success",
                              message: t("integrations.dsw.alerts.testValid"),
                            }),
                          );
                        } catch (e) {
                          if (e instanceof Error)
                            addAlert(
                              mkAlert({
                                variant: "error",
                                title: t("integrations.dsw.alerts.testInvalid"),
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
                    disabled={config.DSW_ALIAS === "" || config.DSW_URL === "" || config.DSW_APIKEY === ""}
                  >
                    {t("common:actions.save")}
                  </Button>
                </CardActions>
              </form>
            </Card>
          ))}
          {newConfig && (
            <Card variant="outlined">
              <form
                onSubmit={(event) => {
                  trackEvent("user:create:dsw_connection:apps");
                  event.preventDefault();
                  void saveNewConfig(newConfig);
                }}
              >
                <CardContent>
                  <Stack spacing={2}>
                    <TextField
                      fullWidth
                      value={newConfig.DSW_ALIAS}
                      onChange={({ target: { value } }) => {
                        runInAction(() => {
                          newConfig.DSW_ALIAS = value;
                        });
                      }}
                      label={t("integrations.dsw.fields.label")}
                      error={newConfig.DSW_ALIAS === ""}
                      helperText={newConfig.DSW_ALIAS === "" && t("integrations.dsw.fields.labelRequired")}
                    />
                    <TextField
                      fullWidth
                      value={newConfig.DSW_URL}
                      onChange={({ target: { value } }) => {
                        runInAction(() => {
                          newConfig.DSW_URL = value;
                        });
                      }}
                      label={t("integrations.dsw.fields.serverUrl")}
                      error={newConfig.DSW_URL === ""}
                      helperText={newConfig.DSW_URL === "" && t("integrations.dsw.fields.urlRequired")}
                    />
                    <TextField
                      fullWidth
                      value={newConfig.DSW_APIKEY}
                      onChange={({ target: { value } }) => {
                        runInAction(() => {
                          newConfig.DSW_APIKEY = value;
                        });
                      }}
                      type="password"
                      label={t("integrations.dsw.fields.apiKey")}
                      error={newConfig.DSW_APIKEY === ""}
                      helperText={newConfig.DSW_APIKEY === "" && t("integrations.dsw.fields.apiKeyRequired")}
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
                    {t("common:actions.delete")}
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
                    disabled={newConfig.DSW_ALIAS === "" || newConfig.DSW_URL === "" || newConfig.DSW_APIKEY === ""}
                  >
                    {t("common:actions.save")}
                  </Button>
                </CardActions>
              </form>
            </Card>
          )}
        </Stack>
        <Button
          disabled={Boolean(newConfig)}
          onClick={() => {
            setNewConfig(
              observable({
                DSW_ALIAS: "",
                DSW_APIKEY: "",
                DSW_URL: "",
              }),
            );
          }}
        >
          {t("common:actions.add")}
        </Button>
      </Stack>
    );
  },
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

function DSW({ integrationState, update }: DSWArgs): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.dsw.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.dsw.description")}
        image={DSWIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: integrationState.credentials })}
        helpLinkText={t("integrations.dsw.helpLink")}
        website="guide.ds-wizard.org/en/latest/"
        docLink="dsw"
        usageText={t("integrations.dsw.usage")}
        setupSection={
          <>
            <Typography variant="body2">{t("integrations.dsw.multipleConfig")}</Typography>
            <TransRichText i18nKey="apps:integrations.dsw.setup.instructions" />
            {ArrayUtils.all(integrationState.credentials)
              .map((configs) => <DialogContent key={null} configs={configs} integrationState={integrationState} />)
              .orElse(t("integrations.dsw.errorGettingConfigs"))}
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(observer(DSW));
