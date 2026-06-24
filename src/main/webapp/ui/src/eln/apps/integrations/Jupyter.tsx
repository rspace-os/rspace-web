import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "@/assets/branding/Jupyter";
import JupyterIcon from "../../../assets/branding/Jupyter/logo.svg";
import IntegrationCard from "../IntegrationCard";

function Jupyter(): React.ReactNode {
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
        name={t("integrations.jupyter.name")}
        explanatoryText={t("integrations.jupyter.description")}
        image={JupyterIcon}
        color={LOGO_COLOR}
        usageText={t("integrations.jupyter.usage")}
        helpLinkText={t("integrations.jupyter.helpLink")}
        website="docs.jupyter.org/en/latest/"
        docLink="jupyter"
        setupSection={
          <ol>
            <li>
              <strong>{t("integrations.jupyter.setup.enableApi")}</strong>{" "}
              {t("integrations.jupyter.setup.enableApiDesc")}
            </li>
            <li>
              <strong>{t("integrations.jupyter.setup.configureJupyter")}</strong>{" "}
              {t("integrations.jupyter.setup.configureJupyterDesc")}
              <Typography variant="body2" component="div" sx={{ mt: 2, mb: 1 }}>
                <strong>{t("integrations.jupyter.setup.installStep")}</strong>
                <Box
                  component="pre"
                  sx={{
                    background: "#f5f5f5",
                    padding: "8px",
                    overflowX: "auto",
                  }}
                >
                  {`%pip install rspace-client==2.6.2`}
                </Box>
              </Typography>
              {t("integrations.jupyter.setup.runCellFirst")}{" "}
              <strong>{t("integrations.jupyter.setup.restartKernel")}</strong>{" "}
              <strong>{t("integrations.jupyter.setup.refreshTab")}</strong> {t("integrations.jupyter.setup.tab")}
            </li>
            <li>
              <strong>{t("integrations.jupyter.setup.configureNotebook")}</strong>{" "}
              {t("integrations.jupyter.setup.configureNotebookDesc")}
              <Typography variant="body2" component="div" sx={{ mt: 2, mb: 1 }}>
                <strong>{t("integrations.jupyter.setup.doOncePerNotebook")}</strong>
                <Box
                  component="pre"
                  sx={{
                    background: "#f5f5f5",
                    padding: "8px",
                    overflowX: "auto",
                  }}
                >
                  {`from rspace_client.notebook_sync import sync_notebook`}
                </Box>
              </Typography>
              {t("integrations.jupyter.setup.runCellSecond")}{" "}
              <strong>{t("integrations.jupyter.setup.restartKernel")}</strong>{" "}
              {t("integrations.jupyter.setup.thenRunAgain")}{" "}
              <strong>{t("integrations.jupyter.setup.runCellAgain")}</strong>
            </li>
            <li>
              <strong>{t("integrations.jupyter.setup.runCode")}</strong>
              <Typography variant="body2" component="div" sx={{ mt: 2, mb: 1 }}>
                {t("integrations.jupyter.setup.pasteCode")} <strong>{t("integrations.jupyter.setup.lastCell")}</strong>
                <Box
                  component="pre"
                  sx={{
                    background: "#f5f5f5",
                    padding: "8px",
                    overflowX: "auto",
                  }}
                >
                  {`await sync_notebook.sync_notebook_to_rspace(
rspace_url="https://researchspace2.eu.ngrok.io/",
rspace_username="user1a")`}
                </Box>
              </Typography>
            </li>
          </ol>
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

export default React.memo(observer(Jupyter));
