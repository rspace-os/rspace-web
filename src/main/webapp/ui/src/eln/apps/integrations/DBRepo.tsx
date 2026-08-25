import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import React, { useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/dbrepo";
import DBRepoIcon from "../../../assets/branding/dbrepo/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type DBRepoArgs = {
  integrationState: IntegrationStates["DBREPO"];
  update: (newIntegrationState: IntegrationStates["DBREPO"]) => void;
};

export interface DBRepoConnectedMessage extends Record<string, unknown> {
  type: "DBREPO_CONNECTED";
  error?: string;
}

export const DBREPO_CONNECTION_CHANNEL = "rspace.apps.dbrepo.connection";

function DBRepo({ integrationState, update }: DBRepoArgs): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const { addAlert } = useContext(AlertContext);
  const [url, setUrl] = useState(integrationState.credentials.DBREPO_URL.orElse(""));
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [connected, setConnected] = useState(integrationState.credentials.DBREPO_CONNECTED);

  useBroadcastChannel<DBRepoConnectedMessage>(DBREPO_CONNECTION_CHANNEL, (e: MessageEvent<DBRepoConnectedMessage>) => {
    if (e.data?.type !== "DBREPO_CONNECTED") return;
    if (e.data.error) {
      addAlert(
        mkAlert({
          variant: "error",
          title: t("integrations.dbrepo.alerts.connectError"),
          message: e.data.error,
        }),
      );
      return;
    }
    setConnected(true);
    setPassword("");
    addAlert(
      mkAlert({
        variant: "success",
        message: t("integrations.dbrepo.alerts.connectSuccess"),
      }),
    );
  });

  const handleDisconnect = async () => {
    const response = await fetch("/apps/dbrepo/connect", {
      method: "DELETE",
      headers: {
        "X-Requested-With": "XMLHttpRequest",
      },
    });
    if (!response.ok) {
      addAlert(
        mkAlert({
          variant: "error",
          title: t("integrations.dbrepo.alerts.disconnectError"),
          message: `${response.status} ${response.statusText}`,
        }),
      );
      return;
    }
    setConnected(false);
    addAlert(
      mkAlert({
        variant: "success",
        message: t("integrations.dbrepo.alerts.disconnectSuccess"),
      }),
    );
  };

  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.dbrepo.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.dbrepo.description")}
        image={DBRepoIcon}
        color={LOGO_COLOR}
        update={(newMode) =>
          update({
            mode: newMode,
            credentials: {
              ...integrationState.credentials,
              DBREPO_URL: url ? Optional.present(url) : Optional.empty(),
              DBREPO_CONNECTED: connected,
            },
          })
        }
        usageText={t("integrations.dbrepo.usage")}
        helpLinkText={t("integrations.dbrepo.helpLink")}
        website="https://dbrepo-project.github.io/dbrepo-docs/latest/"
        docLink="dbrepo"
        setupSection={
          <Stack spacing={1.5}>
            <TransRichText i18nKey="apps:integrations.dbrepo.setup.instructions" />
            {url.startsWith("http://") && (
              <Alert severity="warning">{t("integrations.dbrepo.alerts.httpWarning")}</Alert>
            )}
            <form
              aria-label={t("integrations.dbrepo.credentialsFormLabel")}
              action="/apps/dbrepo/connect"
              method="POST"
              target="_blank"
              rel="noopener"
            >
              <Stack spacing={1}>
                <TextField
                  fullWidth
                  value={url}
                  onChange={({ target: { value } }) => setUrl(value)}
                  label={t("integrations.dbrepo.fields.url")}
                  size="small"
                  slotProps={{
                    htmlInput: {
                      name: "dbrepoUrl",
                      autoComplete: "url",
                    },
                  }}
                />
                <TextField
                  fullWidth
                  value={username}
                  onChange={({ target: { value } }) => setUsername(value)}
                  label={t("integrations.dbrepo.fields.username")}
                  size="small"
                  slotProps={{
                    htmlInput: {
                      name: "dbrepoUsername",
                      autoComplete: "username",
                    },
                  }}
                />
                <TextField
                  fullWidth
                  value={password}
                  onChange={({ target: { value } }) => setPassword(value)}
                  label={t("integrations.dbrepo.fields.password")}
                  size="small"
                  slotProps={{
                    htmlInput: {
                      name: "dbrepoPassword",
                      type: "password",
                      autoComplete: "new-password",
                    },
                  }}
                />
                <Stack direction="row" spacing={1}>
                  <Button type="submit" variant="contained">
                    {connected ? t("actions.reconnect") : t("actions.connect")}
                  </Button>
                  {connected && (
                    <Button type="button" onClick={() => void handleDisconnect()}>
                      {t("actions.disconnect")}
                    </Button>
                  )}
                </Stack>
              </Stack>
            </form>
          </Stack>
        }
      />
    </Grid>
  );
}

export default React.memo(DBRepo);
