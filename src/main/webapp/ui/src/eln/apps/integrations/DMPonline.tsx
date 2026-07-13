import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/dmponline";
import DMPonlineIcon from "../../../assets/branding/dmponline/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import IntegrationCard from "../IntegrationCard";
import { useDmpOnlineEndpoint } from "../useDmpOnlineEndpoint";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type DMPOnlineArgs = {
  integrationState: IntegrationStates["DMPONLINE"];
  update: (newIntegrationState: IntegrationStates["DMPONLINE"]) => void;
};

export interface DMPOnlineConnectedMessage extends Record<string, unknown> {
  type: "DMPONLINE_CONNECTED";
  error?: string;
}
export const DMPONLINE_CONNECTION_CHANNEL = "rspace.apps.dmponline.connection";

/*
 * There is no authentication mechanism with DMPonline. All DMPs are public and by
 * simply enabling the integration users can import those DMPs into the Gallery
 */
function DMPOnline({ integrationState, update }: DMPOnlineArgs): React.ReactNode {
  const { t } = useTranslation("apps");
  const { addAlert } = React.useContext(AlertContext);
  const { disconnect } = useDmpOnlineEndpoint();
  const [connected, setConnected] = React.useState(integrationState.credentials.ACCESS_TOKEN.isPresent());

  useBroadcastChannel<DMPOnlineConnectedMessage>(
    DMPONLINE_CONNECTION_CHANNEL,
    (e: MessageEvent<DMPOnlineConnectedMessage>) => {
      if (e.data?.type !== "DMPONLINE_CONNECTED") return;
      if (e.data.error) {
        addAlert(
          mkAlert({
            variant: "error",
            title: t("integrations.dmponline.alerts.connectError"),
            message: e.data.error,
          }),
        );
        return;
      }
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: t("integrations.dmponline.alerts.connectSuccess"),
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
        name={t("integrations.dmponline.name")}
        explanatoryText={t("integrations.dmponline.description")}
        image={DMPonlineIcon}
        color={LOGO_COLOR}
        usageText={t("integrations.dmponline.usage")}
        helpLinkText={t("integrations.dmponline.helpLink")}
        website="https://dmponline.dcc.ac.uk"
        docLink="dmponline"
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.dmponline.setup.instructions" />
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
              <form action="/apps/dmponline/connect" method="POST" target="_blank" rel="noopener opener">
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

export default React.memo(observer(DMPOnline));
