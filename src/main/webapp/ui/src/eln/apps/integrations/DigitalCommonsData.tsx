import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import React from "react";
import { useTranslation } from "react-i18next";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/digitalcommonsdata";
import DcdIcon from "../../../assets/branding/digitalcommonsdata/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import IntegrationCard from "../IntegrationCard";
import { useDigitalCommonsDataEndpoint } from "../useDigitalCommonsDataEndpoint";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type DigitalCommonsDataArgs = {
  integrationState: IntegrationStates["DIGITALCOMMONSDATA"];
  update: (newIntegrationState: IntegrationStates["DIGITALCOMMONSDATA"]) => void;
};

export interface DigitalCommonsDataConnectedMessage extends Record<string, unknown> {
  type: "DIGITALCOMMONSDATA_CONNECTED";
  error?: string;
}
export const DIGITALCOMMONSDATA_CONNECTION_CHANNEL = "rspace.apps.digitalcommonsdata.connection";

/*
 * Digital Commons Data uses OAuth based authentication, as implemeted by the form below.
 */
function DigitalCommonsData({ integrationState, update }: DigitalCommonsDataArgs): React.ReactNode {
  const { t } = useTranslation("apps");
  const { addAlert } = React.useContext(AlertContext);
  const { disconnect } = useDigitalCommonsDataEndpoint();
  const [connected, setConnected] = React.useState(integrationState.credentials.ACCESS_TOKEN.isPresent());

  useBroadcastChannel<DigitalCommonsDataConnectedMessage>(
    DIGITALCOMMONSDATA_CONNECTION_CHANNEL,
    (e: MessageEvent<DigitalCommonsDataConnectedMessage>) => {
      if (e.data?.type !== "DIGITALCOMMONSDATA_CONNECTED") return;
      if (e.data.error) {
        addAlert(
          mkAlert({
            variant: "error",
            title: t("integrations.digitalCommonsData.alerts.connectError"),
            message: e.data.error,
          }),
        );
        return;
      }
      setConnected(true);
      addAlert(
        mkAlert({
          variant: "success",
          message: t("integrations.digitalCommonsData.alerts.connectSuccess"),
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
        name={t("integrations.digitalCommonsData.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.digitalCommonsData.description")}
        image={DcdIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: integrationState.credentials })}
        helpLinkText={t("integrations.digitalCommonsData.helpLink")}
        website="elsevier.digitalcommonsdata.com"
        docLink="dcd"
        usageText={t("integrations.digitalCommonsData.usage")}
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.digitalCommonsData.setup.instructions" />
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
              <form action="/apps/digitalcommonsdata/connect" method="POST" target="_blank" rel="noopener opener">
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

export default React.memo(DigitalCommonsData);
