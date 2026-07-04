import Grid from "@mui/material/Grid";
import { ChatCodeBlock } from "@mui/x-chat";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "@/assets/branding/Jupyter";
import TransRichText from "@/modules/common/i18n/TransRichText";
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
        website="https://docs.jupyter.org/en/latest/"
        docLink="jupyter"
        setupSection={
          <TransRichText
            i18nKey="apps:integrations.jupyter.setup.instructions"
            components={{
              installCommand: <ChatCodeBlock language="bash">{"%pip install rspace-client==2.6.2"}</ChatCodeBlock>,
              notebookCommand: (
                <ChatCodeBlock language="python">
                  {"from rspace_client.notebook_sync import sync_notebook"}
                </ChatCodeBlock>
              ),
              syncCommand: (
                <ChatCodeBlock language="python">
                  {`await sync_notebook.sync_notebook_to_rspace(
rspace_url="https://researchspace2.eu.ngrok.io/",
rspace_username="user1a")`}
                </ChatCodeBlock>
              ),
            }}
          />
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
