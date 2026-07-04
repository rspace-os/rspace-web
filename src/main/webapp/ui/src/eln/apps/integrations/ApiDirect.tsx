import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/rspace/api";
import ApiIcon from "../../../assets/branding/rspace/api/logo.svg";
import IntegrationCard from "../IntegrationCard";

function ApiDirect(): React.ReactNode {
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
        name={t("integrations.apiDirect.name")}
        explanatoryText={t("integrations.apiDirect.description")}
        image={ApiIcon}
        color={LOGO_COLOR}
        usageText={t("integrations.apiDirect.usage")}
        helpLinkText={t("integrations.apiDirect.helpLink")}
        website="/public/apiDocs"
        docLink="v0dxtfvj7u-api-direct-access"
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.apiDirect.setup.instructions" />
            <Typography variant="body2" sx={{ mt: 2, mb: 1 }}>
              <strong>{t("integrations.apiDirect.curlExample")}</strong>
            </Typography>
            <Box
              component="pre"
              sx={{
                background: "#f5f5f5",
                padding: "8px",
                overflowX: "auto",
              }}
            >
              {`curl -H "apiKey: YOUR_API_KEY" \\
     -H "Accept: application/json" \\
     https://your-rspace-instance.com/api/v1/userDetails/whoami`}
            </Box>
            <Typography variant="body2" component="div" sx={{ mt: 2, mb: 1 }}>
              <strong>{t("integrations.apiDirect.pythonSdk")}</strong>
              <Box
                component="pre"
                sx={{
                  background: "#f5f5f5",
                  padding: "8px",
                  overflowX: "auto",
                }}
              >
                {`# Install with: pip install rspace-client
import os
from rspace_client.eln import eln

client = eln.ELNClient("https://your-rspace-instance.com", "YOUR_API_KEY")
print(client.get_status())`}
              </Box>
            </Typography>
          </>
        }
        update={() => {}}
        integrationState={{
          mode: "EXTERNAL",
          credentials: null,
        }}
      />
    </Grid>
  );
}

export default React.memo(observer(ApiDirect));
