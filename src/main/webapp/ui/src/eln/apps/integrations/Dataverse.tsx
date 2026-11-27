import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { useForm } from "@tanstack/react-form";
import React, { useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { lazyT } from "@/modules/common/i18n/lazyT";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/dataverse";
import DataverseIcon from "../../../assets/branding/dataverse/logo.svg";
import { ConfirmationDialog } from "../../../components/ConfirmationDialog";
import SecretField, { type Secret } from "../../../components/Inputs/SecretField";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as ArrayUtils from "../../../util/ArrayUtils";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import {
  useDeleteAppOptionsMutation,
  useIntegrationModeUpdate,
  useIntegrationState,
  useSaveAppOptionsMutation,
} from "../queries";
import { useDataverseTestEndpoint } from "../useDataverseTestEndpoint";
import type { IntegrationStates } from "../useIntegrationsEndpoint";
import { fieldErrorText } from "./fieldErrors";

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
type Configuration = Configurations[number];

/*
 * Every field is required; a stored API key (null) counts as provided.
 */
const configurationSchema = v.object({
  DATAVERSE_ALIAS: v.pipe(v.string(), v.nonEmpty(lazyT("apps:integrations.dataverse.fields.aliasRequired"))),
  DATAVERSE_URL: v.pipe(v.string(), v.nonEmpty(lazyT("apps:integrations.dataverse.fields.urlRequired"))),
  DATAVERSE_APIKEY: v.nullable(
    v.pipe(v.string(), v.nonEmpty(lazyT("apps:integrations.dataverse.fields.apiKeyRequired"))),
  ),
});

type ConfigurationValues = {
  DATAVERSE_ALIAS: string;
  DATAVERSE_URL: string;
  DATAVERSE_APIKEY: Secret;
};

function valuesOf(config: Configuration): ConfigurationValues {
  return {
    DATAVERSE_ALIAS: config.DATAVERSE_ALIAS,
    DATAVERSE_URL: config.DATAVERSE_URL,
    DATAVERSE_APIKEY: config.DATAVERSE_APIKEY,
  };
}

/**
 * The form for one configuration, new or saved. It shows which fields are
 * missing from the start, as an empty field cannot be saved.
 */
function useConfigurationForm(
  defaultValues: ConfigurationValues,
  save: (values: ConfigurationValues) => Promise<ConfigurationValues | null>,
) {
  const form = useForm({
    defaultValues,
    validators: { onChange: configurationSchema },
    onSubmit: async ({ value, formApi }) => {
      const saved = await save(value);
      if (saved) formApi.reset(saved);
    },
  });
  useEffect(() => {
    void form.validate("change");
  }, [form]);
  return form;
}

function ConfigurationFields({
  form,
  apiKeyClearable,
}: {
  form: ReturnType<typeof useConfigurationForm>;
  apiKeyClearable: boolean;
}) {
  const { t } = useTranslation(["apps", "common"]);
  return (
    <Stack spacing={2}>
      <form.Field name="DATAVERSE_ALIAS">
        {(field) => (
          <TextField
            fullWidth
            value={field.state.value}
            onChange={({ target: { value } }) => {
              field.handleChange(value);
            }}
            label={t("integrations.dataverse.fields.alias")}
            error={field.state.meta.errors.length > 0}
            helperText={field.state.meta.errors.length > 0 && fieldErrorText(field.state.meta.errors)}
          />
        )}
      </form.Field>
      <form.Field name="DATAVERSE_URL">
        {(field) => (
          <TextField
            fullWidth
            value={field.state.value}
            onChange={({ target: { value } }) => {
              field.handleChange(value);
            }}
            label={t("integrations.dataverse.fields.serverUrl")}
            error={field.state.meta.errors.length > 0}
            helperText={field.state.meta.errors.length > 0 && fieldErrorText(field.state.meta.errors)}
          />
        )}
      </form.Field>
      <form.Field name="DATAVERSE_APIKEY">
        {(field) => (
          <SecretField
            fullWidth
            clearable={apiKeyClearable}
            value={field.state.value}
            onChange={field.handleChange}
            autoComplete="new-password"
            label={t("integrations.dataverse.fields.apiKey")}
            error={field.state.meta.errors.length > 0}
            helperText={field.state.meta.errors.length > 0 && fieldErrorText(field.state.meta.errors)}
          />
        )}
      </form.Field>
    </Stack>
  );
}

function SavedConfiguration({
  config,
  deleteDisabled,
  onDelete,
}: {
  config: Configuration;
  deleteDisabled: boolean;
  onDelete: () => void;
}) {
  const { t } = useTranslation(["apps", "common"]);
  const { addAlert } = useContext(AlertContext);
  const { test } = useDataverseTestEndpoint();
  const { mutateAsync: saveAppOptions } = useSaveAppOptionsMutation("DATAVERSE");
  const form = useConfigurationForm(valuesOf(config), async (values) => {
    try {
      const newState = await saveAppOptions({ optionsId: Optional.present(config.optionsId), option: values });
      const savedConfig = ArrayUtils.all(newState.credentials)
        .map((newCreds) => newCreds.find((c) => c.optionsId === config.optionsId))
        .orElse(undefined);
      if (!savedConfig) throw new Error("Save completed but cannot show results.");
      addAlert(
        mkAlert({
          variant: "success",
          message: t("integrations.dataverse.alerts.saveExistingSuccess"),
        }),
      );
      return valuesOf(savedConfig);
    } catch (e) {
      if (e instanceof Error)
        addAlert(
          mkAlert({
            variant: "error",
            title: t("integrations.dataverse.alerts.saveExistingError"),
            message: e.message,
          }),
        );
      return null;
    }
  });

  return (
    <Card variant="outlined">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          event.stopPropagation();
          void form.handleSubmit();
        }}
        aria-label={t("integrations.dataverse.configuredLabel", { name: config.DATAVERSE_ALIAS })}
      >
        <CardContent>
          <ConfigurationFields form={form} apiKeyClearable={false} />
        </CardContent>
        <CardActions>
          <Button disabled={deleteDisabled} onClick={onDelete}>
            {t("common:actions.delete")}
          </Button>
          <form.Subscribe selector={(state) => [state.isDirty, state.isValid] as const}>
            {([dirty, valid]) => (
              <>
                <Button
                  disabled={dirty}
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
                <Button type="submit" disabled={!valid}>
                  {t("common:actions.save")}
                </Button>
              </>
            )}
          </form.Subscribe>
        </CardActions>
      </form>
    </Card>
  );
}

function NewConfiguration({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation(["apps", "common"]);
  const { addAlert } = useContext(AlertContext);
  const { mutateAsync: saveAppOptions } = useSaveAppOptionsMutation("DATAVERSE");
  const form = useConfigurationForm(
    { DATAVERSE_ALIAS: "", DATAVERSE_URL: "", DATAVERSE_APIKEY: "" },
    async (values) => {
      try {
        await saveAppOptions({ optionsId: Optional.empty(), option: values });
        onClose();
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
      return null;
    },
  );

  return (
    <Card variant="outlined">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          event.stopPropagation();
          void form.handleSubmit();
        }}
      >
        <CardContent>
          <ConfigurationFields form={form} apiKeyClearable />
        </CardContent>
        <CardActions>
          <Button variant="outlined" onClick={onClose}>
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
          <form.Subscribe selector={(state) => state.isValid}>
            {(valid) => (
              <Button variant="outlined" type="submit" disabled={!valid}>
                {t("common:actions.save")}
              </Button>
            )}
          </form.Subscribe>
        </CardActions>
      </form>
    </Card>
  );
}

function DialogContent({ configs }: { configs: Configurations }) {
  const { t } = useTranslation(["apps", "common"]);
  const { addAlert } = useContext(AlertContext);
  const { mutateAsync: deleteAppOptions } = useDeleteAppOptionsMutation("DATAVERSE");
  const [configToDelete, setConfigToDelete] = useState<Configuration | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [addingConfig, setAddingConfig] = useState(false);

  async function deleteConfig(config: Configuration) {
    setDeletingId(config.optionsId);
    try {
      await deleteAppOptions(config.optionsId);
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
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <Stack spacing={1} sx={{ mt: 1 }}>
      {configToDelete && (
        <ConfirmationDialog
          title={t("integrations.dataverse.deleteConfirm.title")}
          consequences={
            <Typography>
              {t("integrations.dataverse.deleteConfirm.message", {
                name: configToDelete.DATAVERSE_ALIAS,
              })}
            </Typography>
          }
          variant="warning"
          callback={() => void deleteConfig(configToDelete)}
          handleCloseDialog={() => setConfigToDelete(null)}
        />
      )}
      <Stack spacing={1}>
        {configs.map((config) => (
          <SavedConfiguration
            key={config.optionsId}
            config={config}
            deleteDisabled={deletingId !== null}
            onDelete={() => setConfigToDelete(config)}
          />
        ))}
        {addingConfig && <NewConfiguration onClose={() => setAddingConfig(false)} />}
      </Stack>
      <Button
        sx={{ width: "fit-content" }}
        disabled={addingConfig}
        onClick={() => {
          setAddingConfig(true);
        }}
      >
        {t("common:actions.add")}
      </Button>
    </Stack>
  );
}

function Dataverse(): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const integrationState = useIntegrationState("DATAVERSE");
  const updateMode = useIntegrationModeUpdate("DATAVERSE");
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
        update={updateMode}
        integrationState={integrationState}
        helpLinkText={t("integrations.dataverse.helpLink")}
        website="https://dataverse.org"
        docLink="dataverse"
        usageText={t("integrations.dataverse.usage")}
        setupSection={
          <>
            <Typography variant="body2">{t("integrations.dataverse.multipleConfig")}</Typography>
            <TransRichText i18nKey="apps:integrations.dataverse.setup.instructions" />
            {ArrayUtils.all(integrationState.credentials)
              .map((configs) => <DialogContent key={null} configs={configs} />)
              .orElse(t("integrations.dataverse.errorGettingConfigs"))}
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(Dataverse);
