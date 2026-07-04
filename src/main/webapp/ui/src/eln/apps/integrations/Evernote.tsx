import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/evernote";
import EvernoteIcon from "../../../assets/branding/evernote/logo.svg";
import IntegrationCard from "../IntegrationCard";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type EvernoteArgs = {
  integrationState: IntegrationStates["EVERNOTE"];
  update: (newIntegrationState: IntegrationStates["EVERNOTE"]) => void;
};

/*
 * There is no authentication mechanism with Evernote. All users can use it to
 * import documents into RSpace.
 */
function Evernote({ integrationState, update }: EvernoteArgs): React.ReactNode {
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
        name={t("integrations.evernote.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.evernote.description")}
        image={EvernoteIcon}
        color={LOGO_COLOR}
        usageText={t("integrations.evernote.usage")}
        helpLinkText={t("integrations.evernote.helpLink")}
        website="https://evernote.com"
        docLink="9ckpmfdq8m-evernote-integration"
        setupSection={<TransRichText i18nKey="apps:integrations.evernote.setup.instructions" />}
        update={(newMode) => update({ mode: newMode, credentials: {} })}
      />
    </Grid>
  );
}

export default React.memo(observer(Evernote));
