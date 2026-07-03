import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/jove";
import JoveIcon from "../../../assets/branding/jove/logo.svg";
import IntegrationCard from "../IntegrationCard";

function Jove(): React.ReactNode {
  const { t } = useTranslation("apps");
  const exampleDomain = "jove.com";
  const exampleUrl = "https://www.jove.com/v/60908/...";
  const appExampleUrl = "https://app.jove.com/v/60908/...";
  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.jove.name")}
        integrationState={{
          mode: "EXTERNAL",
          credentials: null,
        }}
        explanatoryText={t("integrations.jove.description")}
        image={JoveIcon}
        color={LOGO_COLOR}
        update={() => {}}
        usageText={t("integrations.jove.usage")}
        helpLinkText={t("integrations.jove.helpLink")}
        website="jove.com"
        docLink="videoIntegration"
        setupSection={
          <Stack direction="column" sx={{ gap: 2 }}>
            <TransRichText
              i18nKey="apps:integrations.jove.setup.instructions"
              values={{ domain: exampleDomain, exampleUrl, appExampleUrl }}
            />
            <Typography variant="body2">
              <TransRichText i18nKey="apps:integrations.jove.setup.noteFull" />
            </Typography>
          </Stack>
        }
      />
    </Grid>
  );
}

export default React.memo(Jove);
