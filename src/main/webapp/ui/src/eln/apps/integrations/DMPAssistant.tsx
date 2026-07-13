import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/dmpassistant";
import DMPAssistantIcon from "../../../assets/branding/dmpassistant/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import IntegrationCard from "../IntegrationCard";
import { useDmpAssistantEndpoint } from "../useDmpAssistantEndpoint";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type DMPAssistantArgs = {
  integrationState: IntegrationStates["DMPASSISTANT"];
  update: (newIntegrationState: IntegrationStates["DMPASSISTANT"]) => void;
};

export interface DMPAssistantConnectedMessage extends Record<string, unknown> {
  type: "DMPASSISTANT_CONNECTED";
  error?: string;
}
export const DMPASSISTANT_CONNECTION_CHANNEL = "rspace.apps.dmpassistant.connection";

function DMPAssistant({ integrationState, update }: DMPAssistantArgs): React.ReactNode {
  const { t } = useTranslation("apps");
  const { addAlert } = React.useContext(AlertContext);
  const { disconnect } = useDmpAssistantEndpoint();
  const [connected, setConnected] = React.useState(integrationState.credentials.ACCESS_TOKEN.isPresent());

  useBroadcastChannel<DMPAssistantConnectedMessage>(
    DMPASSISTANT_CONNECTION_CHANNEL,
    (e: MessageEvent<DMPAssistantConnectedMessage>) => {
      if (e.data?.type !== "DMPASSISTANT_CONNECTED") return;
      if (e.data.error) {
        addAlert(
          mkAlert({
            variant: "error",
            title: t("integrations.dmpAssistant.alerts.connectError"),
            message: e.data.error,
          }),
        );
        return;
      }
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: t("integrations.dmpAssistant.alerts.connectSuccess"),
        }),
      );
    },
  );

  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.dmpAssistant.name")}
        explanatoryText={t("integrations.dmpAssistant.description")}
        image={DMPAssistantIcon}
        color={LOGO_COLOR}
        usageText={t("integrations.dmpAssistant.usage")}
        helpLinkText={t("integrations.dmpAssistant.helpLink")}
        website="https://dmp-pgd.ca"
        docLink="dmpassistant"
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.dmpAssistant.setup.instructions" />
            {connected ? (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  void (async () => {
                    await disconnect();
                    setConnected(false);
                  })();
                }}
              >
                <Button type="submit" sx={{ mt: 1 }}>
                  {t("actions.disconnect")}
                </Button>
              </form>
            ) : (
              <form action="/apps/dmpassistant/connect" method="POST" target="_blank" rel="noopener opener">
                <Button type="submit" sx={{ mt: 1 }} value="Connect">
                  {t("actions.connect")}
                </Button>
              </form>
            )}
          </>
        }
        update={(newMode) => update({ mode: newMode, credentials: integrationState.credentials })}
        integrationState={integrationState}
      />
    </Grid>
  );
}

export default React.memo(observer(DMPAssistant));
