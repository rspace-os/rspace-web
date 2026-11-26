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
import AnalyticsContext from "@/stores/contexts/Analytics";
import { LOGO_COLOR } from "../../../assets/branding/dsw";
import DSWIcon from "../../../assets/branding/dsw/logo.svg";
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
import { useDSWTestEndpoint } from "../useDSWTestEndpoint";
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
 * such, we can discard the Optional wrapper around each DSW
 * configuration.
 */
type Configurations = UnwrapArray<IntegrationStates["DSW"]["credentials"]>;
type Configuration = Configurations[number];

/*
 * Every field is required; a stored API key (null) counts as provided.
 */
const configurationSchema = v.object({
  DSW_ALIAS: v.pipe(v.string(), v.nonEmpty(lazyT("apps:integrations.dsw.fields.labelRequired"))),
  DSW_URL: v.pipe(v.string(), v.nonEmpty(lazyT("apps:integrations.dsw.fields.urlRequired"))),
  DSW_APIKEY: v.nullable(v.pipe(v.string(), v.nonEmpty(lazyT("apps:integrations.dsw.fields.apiKeyRequired")))),
});

type ConfigurationValues = {
  DSW_ALIAS: string;
  DSW_URL: string;
  DSW_APIKEY: Secret;
};

function valuesOf(config: Configuration): ConfigurationValues {
  return {
    DSW_ALIAS: config.DSW_ALIAS,
    DSW_URL: config.DSW_URL,
    DSW_APIKEY: config.DSW_APIKEY,
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
      <form.Field name="DSW_ALIAS">
        {(field) => (
          <TextField
            fullWidth
            value={field.state.value}
            onChange={({ target: { value } }) => {
              field.handleChange(value);
            }}
            label={t("integrations.dsw.fields.label")}
            error={field.state.meta.errors.length > 0}
            helperText={field.state.meta.errors.length > 0 && fieldErrorText(field.state.meta.errors)}
          />
        )}
      </form.Field>
      <form.Field name="DSW_URL">
        {(field) => (
          <TextField
            fullWidth
            value={field.state.value}
            onChange={({ target: { value } }) => {
              field.handleChange(value);
            }}
            label={t("integrations.dsw.fields.serverUrl")}
            error={field.state.meta.errors.length > 0}
            helperText={field.state.meta.errors.length > 0 && fieldErrorText(field.state.meta.errors)}
          />
        )}
      </form.Field>
      <form.Field name="DSW_APIKEY">
        {(field) => (
          <SecretField
            fullWidth
            clearable={apiKeyClearable}
            value={field.state.value}
            onChange={field.handleChange}
            autoComplete="new-password"
            label={t("integrations.dsw.fields.apiKey")}
            error={field.state.meta.errors.length > 0}
            helperText={field.state.meta.errors.length > 0 && fieldErrorText(field.state.meta.errors)}
          />
        )}
      </form.Field>
    </Stack>
  );
}

function SavedConfiguration({ config, onDelete }: { config: Configuration; onDelete: () => void }) {
  const { t } = useTranslation(["apps", "common"]);
  const { addAlert } = useContext(AlertContext);
  const { test } = useDSWTestEndpoint();
  const { mutateAsync: saveAppOptions } = useSaveAppOptionsMutation("DSW");
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
          message: t("integrations.dsw.alerts.saveExistingSuccess"),
        }),
      );
      return valuesOf(savedConfig);
    } catch (e) {
      if (e instanceof Error)
        addAlert(
          mkAlert({
            variant: "error",
            title: t("integrations.dsw.alerts.saveExistingError"),
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
        aria-label={t("integrations.dsw.configuredLabel", { label: config.DSW_ALIAS })}
      >
        <CardContent>
          <ConfigurationFields form={form} apiKeyClearable={false} />
        </CardContent>
        <CardActions>
          <Button onClick={onDelete}>{t("common:actions.delete")}</Button>
          <form.Subscribe selector={(state) => [state.isDirty, state.isValid] as const}>
            {([dirty, valid]) => (
              <>
                <Button
                  disabled={dirty}
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
  const analytics = useContext(AnalyticsContext);
  const { addAlert } = useContext(AlertContext);
  const { mutateAsync: saveAppOptions } = useSaveAppOptionsMutation("DSW");
  const form = useConfigurationForm({ DSW_ALIAS: "", DSW_URL: "", DSW_APIKEY: "" }, async (values) => {
    try {
      await saveAppOptions({ optionsId: Optional.empty(), option: values });
      onClose();
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
    return null;
  });

  return (
    <Card variant="outlined">
      <form
        onSubmit={(event) => {
          analytics.trackEvent("user:create:dsw_connection:apps");
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
  const analytics = useContext(AnalyticsContext);
  const { mutateAsync: deleteAppOptions } = useDeleteAppOptionsMutation("DSW");
  const [addingConfig, setAddingConfig] = useState(false);

  async function deleteConfig(config: Configuration) {
    analytics.trackEvent("user:delete:dsw_connection:apps");
    try {
      await deleteAppOptions(config.optionsId);
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
  }

  return (
    <Stack spacing={1} sx={{ mt: 1 }}>
      <Stack spacing={1}>
        {configs.map((config) => (
          <SavedConfiguration key={config.optionsId} config={config} onDelete={() => void deleteConfig(config)} />
        ))}
        {addingConfig && <NewConfiguration onClose={() => setAddingConfig(false)} />}
      </Stack>
      <Button
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

function DSW(): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const integrationState = useIntegrationState("DSW");
  const updateMode = useIntegrationModeUpdate("DSW");
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
        update={updateMode}
        helpLinkText={t("integrations.dsw.helpLink")}
        website="https://guide.ds-wizard.org/en/latest/"
        docLink="dsw"
        usageText={t("integrations.dsw.usage")}
        setupSection={
          <>
            <Typography variant="body2">{t("integrations.dsw.multipleConfig")}</Typography>
            <TransRichText i18nKey="apps:integrations.dsw.setup.instructions" />
            {ArrayUtils.all(integrationState.credentials)
              .map((configs) => <DialogContent key={null} configs={configs} />)
              .orElse(t("integrations.dsw.errorGettingConfigs"))}
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(DSW);
