import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";
import { useForm } from "@tanstack/react-form";
import React from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/egnyte";
import EgnyteIcon from "../../../assets/branding/egnyte/logo.svg";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import { useIntegrationModeUpdate, useIntegrationState, useUpdateIntegrationMutation } from "../queries";

const egnyteFormSchema = v.object({
  domainUrl: v.string(),
});

/*
 * Egnyte has a domain URL that is configured by the this text field.
 */
function Egnyte(): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const integrationState = useIntegrationState("EGNYTE");
  const updateMode = useIntegrationModeUpdate("EGNYTE");
  const { mutateAsync: update } = useUpdateIntegrationMutation("EGNYTE");
  const form = useForm({
    defaultValues: { domainUrl: integrationState.credentials.EGNYTE_DOMAIN.orElse("") },
    validators: { onChange: egnyteFormSchema },
    onSubmit: async ({ value, formApi }) => {
      try {
        const saved = await update({
          mode: integrationState.mode,
          credentials: {
            // an untouched field keeps the domain as stored, even when there is none
            EGNYTE_DOMAIN: formApi.state.isDirty
              ? Optional.present(value.domainUrl)
              : integrationState.credentials.EGNYTE_DOMAIN,
          },
        });
        formApi.reset({ domainUrl: saved.credentials.EGNYTE_DOMAIN.orElse("") });
      } catch {
        // update() has already shown the error; keep the typed domain so it can be retried
      }
    },
  });

  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.egnyte.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.egnyte.description")}
        image={EgnyteIcon}
        color={LOGO_COLOR}
        update={updateMode}
        usageText={t("integrations.egnyte.usage")}
        helpLinkText={t("integrations.egnyte.helpLink")}
        website="https://egnyte.com"
        docLink="cloudstorage"
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.egnyte.setup.instructions" />
            <Card variant="outlined" sx={{ mt: 2 }}>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  void form.handleSubmit();
                }}
              >
                <CardContent>
                  <form.Field name="domainUrl">
                    {(field) => (
                      <TextField
                        fullWidth
                        variant="outlined"
                        label={t("integrations.egnyte.fields.domainUrl")}
                        size="small"
                        value={field.state.value}
                        onChange={({ target: { value } }) => {
                          field.handleChange(value);
                        }}
                      />
                    )}
                  </form.Field>
                </CardContent>
                <CardActions>
                  <Button type="submit">{t("common:actions.save")}</Button>
                </CardActions>
              </form>
            </Card>
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(Egnyte);
