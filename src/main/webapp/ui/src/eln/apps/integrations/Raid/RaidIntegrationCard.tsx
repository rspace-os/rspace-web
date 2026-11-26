import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";
import Link from "@mui/material/Link";
import ListItemText from "@mui/material/ListItemText";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import React, { type FormEventHandler, type MouseEventHandler, useState } from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "@/assets/branding/raid";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { Optional } from "@/util/optional";
import RaidIcon from "../../../../assets/branding/raid/logo.svg";
import AlertContext, { mkAlert } from "../../../../stores/contexts/Alert";
import IntegrationCard from "../../IntegrationCard";
import {
  useDeleteAppOptionsMutation,
  useIntegrationModeUpdate,
  useIntegrationState,
  useRecordIntegrationChange,
  useSaveAppOptionsMutation,
} from "../../queries";

export interface RaidConnectedMessage extends Record<string, unknown> {
  type: "RAID_CONNECTED";
  alias: string;
  error?: string;
}

export const RAID_CONNECTION_CHANNEL = "rspace.apps.raid.connection";

function RaidIntegrationCard(): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const { addAlert } = React.useContext(AlertContext);
  const integrationState = useIntegrationState("RAID");
  const updateMode = useIntegrationModeUpdate("RAID");
  const { mutateAsync: saveAppOptions } = useSaveAppOptionsMutation("RAID");
  const { mutateAsync: deleteAppOptions } = useDeleteAppOptionsMutation("RAID");
  const recordChange = useRecordIntegrationChange("RAID");
  const { authenticatedServers } = integrationState.credentials;
  const setAuthenticated = (alias: string, authenticated: boolean) => {
    recordChange((current) => ({
      ...current,
      credentials: {
        ...current.credentials,
        authenticatedServers: current.credentials.authenticatedServers.map((s) =>
          s.alias === alias ? { ...s, authenticated } : s,
        ),
      },
    }));
  };
  const [addMenuAnchorEl, setAddMenuAnchorEl] = useState<null | HTMLElement>(null);
  /* Aliases whose add request is in flight, so a second click cannot create a duplicate. */
  const [pendingAliases, setPendingAliases] = useState<ReadonlySet<string>>(() => new Set());
  const setAliasPending = (alias: string, pending: boolean) => {
    setPendingAliases((current) => {
      const next = new Set(current);
      if (pending) next.add(alias);
      else next.delete(alias);
      return next;
    });
  };

  useBroadcastChannel<RaidConnectedMessage>(RAID_CONNECTION_CHANNEL, (e: MessageEvent<RaidConnectedMessage>) => {
    if (e.data?.type === "RAID_CONNECTED" && e.data.error) {
      addAlert(
        mkAlert({
          variant: "error",
          title: e.data.alias
            ? t("integrations.raid.alerts.connectAliasError", { alias: e.data.alias })
            : t("integrations.raid.alerts.connectError"),
          message: e.data.error,
        }),
      );
      return;
    }
    if (e.data?.type !== "RAID_CONNECTED" || !e.data.alias) {
      return;
    }

    if (!authenticatedServers.some((s) => s.alias === e.data.alias)) {
      return;
    }

    setAuthenticated(e.data.alias, true);

    addAlert(
      mkAlert({
        variant: "success",
        message: t("integrations.raid.alerts.connectSuccess", { alias: e.data.alias }),
      }),
    );
  });

  const unauthenticatedServers = integrationState.credentials.configuredServers.filter(
    ({ alias }) => !authenticatedServers.find((s) => s.alias === alias),
  );

  const handleDisconnect =
    (alias: string): FormEventHandler =>
    async (event) => {
      event.preventDefault();
      try {
        const response = await fetch(`/apps/raid/connect/${alias}`, {
          method: "DELETE",
          headers: {
            "X-Requested-With": "XMLHttpRequest",
          },
        });
        if (!response.ok) {
          addAlert(
            mkAlert({
              variant: "error",
              title: t("integrations.raid.alerts.disconnectError", { alias }),
              message: t("integrations.raid.alerts.serverStatus", {
                status: response.status,
                statusText: response.statusText,
              }),
            }),
          );

          return;
        }
        setAuthenticated(alias, false);
        addAlert(
          mkAlert({
            variant: "success",
            message: t("integrations.raid.alerts.disconnectSuccess"),
          }),
        );
      } catch (e) {
        addAlert(
          mkAlert({
            variant: "error",
            title: t("integrations.raid.alerts.disconnectError", { alias }),
            message: e instanceof Error ? e.message : JSON.stringify(e),
          }),
        );
      }
    };

  const handleDeleteConnection =
    (optionsId: string, alias: string): FormEventHandler =>
    async (event) => {
      event.preventDefault();
      try {
        await deleteAppOptions(optionsId);
        addAlert(
          mkAlert({
            variant: "success",
            message: t("integrations.raid.alerts.deleteSuccess"),
          }),
        );
      } catch (e) {
        addAlert(
          mkAlert({
            variant: "error",
            title: t("integrations.raid.alerts.deleteError", { alias }),
            message: e instanceof Error ? e.message : JSON.stringify(e),
          }),
        );
      }
    };

  const handleAddServer =
    (alias: string, url: string): MouseEventHandler =>
    async () => {
      setAliasPending(alias, true);
      try {
        const newConfigs = await saveAppOptions({
          optionsId: Optional.empty(),
          option: {
            RAID_ALIAS: alias,
            RAID_URL: url,
            RAID_OAUTH_CONNECTED: false,
          },
        });
        setAddMenuAnchorEl(null);
        if (!newConfigs.credentials.authenticatedServers.some((server) => server.alias === alias))
          throw new Error("Save completed but cannot show results.");
        addAlert(
          mkAlert({
            variant: "success",
            message: t("integrations.raid.alerts.addSuccess"),
          }),
        );
      } catch (e) {
        addAlert(
          mkAlert({
            variant: "error",
            title: t("integrations.raid.alerts.addError", { alias }),
            message: e instanceof Error ? e.message : JSON.stringify(e),
          }),
        );
      } finally {
        setAliasPending(alias, false);
      }
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
        name={t("integrations.raid.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.raid.description")}
        image={RaidIcon}
        color={LOGO_COLOR}
        usageText={t("integrations.raid.usage")}
        helpLinkText={t("integrations.raid.helpLink")}
        website="https://raid.org"
        docLink="raid"
        setupSection={
          <>
            <Typography variant="body1">{t("integrations.raid.setup.configure")}</Typography>
            <TransRichText i18nKey="apps:integrations.raid.setup.instructions" />
            <Typography variant="body1">{t("integrations.raid.multipleConfig")}</Typography>
            <Card variant="outlined" sx={{ mt: 2 }}>
              <CardContent>
                <Stack sx={{ gap: 2 }}>
                  {authenticatedServers.length === 0 && (
                    <Typography variant="body2">{t("integrations.raid.noServers")}</Typography>
                  )}
                  {authenticatedServers.map((server) => (
                    <Stack direction="row" spacing={1} key={server.optionsId} sx={{ justifyItems: "center" }}>
                      <Stack direction="column" sx={{ flexGrow: 1, gap: 1 }}>
                        <Typography variant="body1" sx={{ fontWeight: "bold", lineHeight: 1 }}>
                          {server.alias}
                        </Typography>
                        <Typography variant="caption" sx={{ lineHeight: 1 }}>
                          <Link href={server.url}>{server.url}</Link>
                        </Typography>
                      </Stack>
                      {!server.authenticated ? (
                        <form
                          action={`/apps/raid/connect/${server.alias}`}
                          method="POST"
                          target="_blank"
                          rel="noopener opener"
                        >
                          <Button type="submit">{t("actions.connect")}</Button>
                        </form>
                      ) : (
                        <form
                          onSubmit={handleDisconnect(server.alias)}
                          action={`/apps/raid/connect/${server.alias}`}
                          method="DELETE"
                          target="_blank"
                          rel="noopener opener"
                        >
                          <Button type="submit">{t("actions.disconnect")}</Button>
                        </form>
                      )}
                      <form onSubmit={handleDeleteConnection(server.optionsId, server.alias)}>
                        <Button type="submit">{t("common:actions.delete")}</Button>
                      </form>
                    </Stack>
                  ))}
                </Stack>
              </CardContent>
              <CardActions>
                <Button
                  onClick={(e) => {
                    setAddMenuAnchorEl(e.currentTarget);
                  }}
                  disabled={unauthenticatedServers.length === 0}
                >
                  {t("common:actions.add")}
                </Button>
                <Menu
                  open={Boolean(addMenuAnchorEl)}
                  anchorEl={addMenuAnchorEl}
                  onClose={() => setAddMenuAnchorEl(null)}
                >
                  {unauthenticatedServers.map(({ alias, url }) => (
                    <MenuItem key={alias} disabled={pendingAliases.has(alias)} onClick={handleAddServer(alias, url)}>
                      <ListItemText primary={alias} secondary={url} />
                    </MenuItem>
                  ))}
                </Menu>
              </CardActions>
            </Card>
          </>
        }
        update={updateMode}
      />
    </Grid>
  );
}

/**
 * The card and dialog for configuring the RAiD integration
 */
export default React.memo(RaidIntegrationCard);
