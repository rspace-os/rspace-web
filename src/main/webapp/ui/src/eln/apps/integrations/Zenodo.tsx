import Grid from "@mui/material/Grid";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/zenodo";
import ZenodoIcon from "../../../assets/branding/zenodo/logo.svg";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import { useIntegrationModeUpdate, useIntegrationState, useUpdateIntegrationMutation } from "../queries";
import ApiKeyForm from "./ApiKeyForm";

/*
 * Zenodo uses API-key based authentication, as implemeted by the form below.
 */
function Zenodo(): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const integrationState = useIntegrationState("ZENODO");
  const updateMode = useIntegrationModeUpdate("ZENODO");
  const { mutateAsync: update } = useUpdateIntegrationMutation("ZENODO");

  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.zenodo.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.zenodo.description")}
        image={ZenodoIcon}
        color={LOGO_COLOR}
        usageText={t("integrations.zenodo.usage")}
        helpLinkText={t("integrations.zenodo.helpLink")}
        website="https://zenodo.org"
        docLink="zenodo"
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.zenodo.setup.instructions" />
            <ApiKeyForm
              label={t("integrations.zenodo.fields.apiKey")}
              storedKey={integrationState.credentials.ZENODO_USER_TOKEN}
              save={async (apiKey) => {
                const saved = await update({
                  mode: integrationState.mode,
                  credentials: { ZENODO_USER_TOKEN: Optional.present(apiKey) },
                });
                return saved.credentials.ZENODO_USER_TOKEN;
              }}
            />
          </>
        }
        update={updateMode}
      />
    </Grid>
  );
}

export default React.memo(Zenodo);
