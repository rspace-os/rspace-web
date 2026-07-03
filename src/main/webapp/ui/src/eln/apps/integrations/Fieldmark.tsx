import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/fieldmark";
import FieldmarktIcon from "../../../assets/branding/fieldmark/logo.svg";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type FieldmarkArgs = {
  integrationState: IntegrationStates["FIELDMARK"];
  update: (newIntegrationState: IntegrationStates["FIELDMARK"]) => void;
};

/*
 * Fieldmark uses an API-key-like authentication mechanism, wherein the user
 * copies a string from the Fieldmark website into the text field provided
 * below..
 */
function Fieldmark({ integrationState, update }: FieldmarkArgs): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const [apiKey, setApiKey] = React.useState(integrationState.credentials.FIELDMARK_USER_TOKEN.orElse(""));

  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.fieldmark.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.fieldmark.description")}
        image={FieldmarktIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: integrationState.credentials })}
        helpLinkText={t("integrations.fieldmark.helpLink")}
        website="fieldnote.au/fieldmark"
        docLink="fieldmark"
        usageText={<TransRichText i18nKey="apps:integrations.fieldmark.usage" />}
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.fieldmark.setup.instructions" />
            <Card variant="outlined" sx={{ mt: 2 }}>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void update({
                    mode: integrationState.mode,
                    credentials: {
                      FIELDMARK_USER_TOKEN: Optional.present(apiKey),
                    },
                  });
                }}
              >
                <CardContent>
                  <TextField
                    fullWidth
                    variant="outlined"
                    label={t("integrations.fieldmark.fields.apiKey")}
                    type="password"
                    size="small"
                    value={apiKey}
                    onChange={({ target: { value } }) => {
                      setApiKey(value);
                    }}
                  />
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

export default React.memo(Fieldmark);
