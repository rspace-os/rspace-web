import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import { useForm } from "@tanstack/react-form";
import type React from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import SecretField from "../../../components/Inputs/SecretField";
import type { Optional } from "../../../util/optional";
import type { Secret } from "../../../util/secret";

const apiKeyFormSchema = v.object({
  apiKey: v.nullable(v.string()),
});

type ApiKeyFormArgs = {
  /** The field's label. */
  label: string;

  /** The key as the server holds it: null when one is stored, "" when there is none. */
  storedKey: Optional<Secret>;

  /**
   * Saves the key, resolving with the key as the server then holds it. It
   * rejects, having already shown the error, when the save fails.
   */
  save: (apiKey: Secret) => Promise<Optional<Secret>>;
};

/**
 * A form for an integration whose only setting is a single API key, saved
 * with the rest of the integration's state.
 */
function ApiKeyForm({ label, storedKey, save }: ApiKeyFormArgs): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const form = useForm({
    defaultValues: { apiKey: storedKey.orElse("") as Secret },
    validators: { onChange: apiKeyFormSchema },
    onSubmit: async ({ value, formApi }) => {
      try {
        const saved = await save(value.apiKey);
        formApi.reset({ apiKey: saved.orElse("") });
      } catch {
        // the save has already shown the error; keep the typed key so it can be retried
      }
    },
  });

  return (
    <Card variant="outlined" sx={{ mt: 2 }}>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          event.stopPropagation();
          void form.handleSubmit();
        }}
      >
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(saving) => (
            <>
              <CardContent>
                <form.Field name="apiKey">
                  {(field) => (
                    <SecretField
                      fullWidth
                      variant="outlined"
                      label={label}
                      size="small"
                      autoComplete="new-password"
                      value={field.state.value}
                      storedSecretExists={storedKey.map((key) => key !== "").orElse(false)}
                      disabled={saving}
                      onChange={field.handleChange}
                    />
                  )}
                </form.Field>
              </CardContent>
              <CardActions>
                <Button type="submit" disabled={saving}>
                  {t("common:actions.save")}
                </Button>
              </CardActions>
            </>
          )}
        </form.Subscribe>
      </form>
    </Card>
  );
}

export default ApiKeyForm;
