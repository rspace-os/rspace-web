import Grid from "@mui/material/Grid";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/googledrive";
import GoogleDriveIcon from "../../../assets/branding/googledrive/logo.svg";
import IntegrationCard from "../IntegrationCard";
import { useIntegrationModeUpdate, useIntegrationState } from "../queries";

/*
 * Authentication with Google happens when the user uses the dialog on the document editor page.
 */
function GoogleDrive(): React.ReactNode {
  const integrationState = useIntegrationState("GOOGLEDRIVE");
  const updateMode = useIntegrationModeUpdate("GOOGLEDRIVE");
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
        name={t("integrations.googleDrive.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.googleDrive.description")}
        image={GoogleDriveIcon}
        color={LOGO_COLOR}
        update={updateMode}
        usageText={t("integrations.googleDrive.usage")}
        helpLinkText={t("integrations.googleDrive.helpLink")}
        website="https://drive.google.com"
        docLink="cloudstorage"
        setupSection={<TransRichText i18nKey="apps:integrations.googleDrive.setup.instructions" />}
      />
    </Grid>
  );
}

export default React.memo(GoogleDrive);
