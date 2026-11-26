import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import React from "react";
import { useTranslation } from "react-i18next";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/dmptool";
import DMPToolIcon from "../../../assets/branding/dmptool/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import IntegrationCard from "../IntegrationCard";
import { useIntegrationModeUpdate, useIntegrationState, useTokenConnection } from "../queries";
import { useDmptoolEndpoint } from "../useDmptoolEndpoint";

export interface DMPToolConnectedMessage extends Record<string, unknown> {
  type: "DMPTOOL_CONNECTED";
  error?: string;
}
export const DMPTOOL_CONNECTION_CHANNEL = "rspace.apps.dmptool.connection";

/*
 * DMPTool uses OAuth based authentication, as implemeted by the form below.
 */
function DMPTool(): React.ReactNode {
  const integrationState = useIntegrationState("DMPTOOL");
  const updateMode = useIntegrationModeUpdate("DMPTOOL");
  const { t } = useTranslation("apps");
  const { addAlert } = React.useContext(AlertContext);
  const { disconnect } = useDmptoolEndpoint();
  const { connected, setConnected } = useTokenConnection("DMPTOOL");

  useBroadcastChannel<DMPToolConnectedMessage>(
    DMPTOOL_CONNECTION_CHANNEL,
    (e: MessageEvent<DMPToolConnectedMessage>) => {
      if (e.data?.type !== "DMPTOOL_CONNECTED") return;
      if (e.data.error) {
        addAlert(
          mkAlert({
            variant: "error",
            title: t("integrations.dmptool.alerts.connectError"),
            message: e.data.error,
          }),
        );
        return;
      }
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: t("integrations.dmptool.alerts.connectSuccess"),
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
        name={t("integrations.dmptool.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.dmptool.description")}
        image={DMPToolIcon}
        color={LOGO_COLOR}
        update={updateMode}
        helpLinkText={t("integrations.dmptool.helpLink")}
        website="https://dmptool.org"
        docLink="dmptool"
        usageText={t("integrations.dmptool.usage")}
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.dmptool.setup.instructions" />
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
              <form action="/apps/dmptool/connect" method="POST" target="_blank" rel="noopener opener">
                <Button type="submit" sx={{ mt: 1 }} value="Connect">
                  {t("actions.connect")}
                </Button>
              </form>
            )}
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(DMPTool);
