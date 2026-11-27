import Grid from "@mui/material/Grid";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/fieldmark";
import FieldmarktIcon from "../../../assets/branding/fieldmark/logo.svg";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import { useIntegrationModeUpdate, useIntegrationState, useUpdateIntegrationMutation } from "../queries";
import ApiKeyForm from "./ApiKeyForm";

/*
 * Fieldmark uses an API-key-like authentication mechanism, wherein the user
 * copies a string from the Fieldmark website into the text field provided
 * below..
 */
function Fieldmark(): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const integrationState = useIntegrationState("FIELDMARK");
  const updateMode = useIntegrationModeUpdate("FIELDMARK");
  const { mutateAsync: update } = useUpdateIntegrationMutation("FIELDMARK");

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
        update={updateMode}
        helpLinkText={t("integrations.fieldmark.helpLink")}
        website="https://fieldnote.au/fieldmark"
        docLink="fieldmark"
        usageText={<TransRichText i18nKey="apps:integrations.fieldmark.usage" />}
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.fieldmark.setup.instructions" />
            <ApiKeyForm
              label={t("integrations.fieldmark.fields.apiKey")}
              storedKey={integrationState.credentials.FIELDMARK_USER_TOKEN}
              save={async (apiKey) => {
                const saved = await update({
                  mode: integrationState.mode,
                  credentials: { FIELDMARK_USER_TOKEN: Optional.present(apiKey) },
                });
                return saved.credentials.FIELDMARK_USER_TOKEN;
              }}
            />
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(Fieldmark);
