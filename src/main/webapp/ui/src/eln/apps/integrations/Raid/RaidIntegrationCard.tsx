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
import { runInAction } from "mobx";
import { observer, useLocalObservable } from "mobx-react-lite";
import React, { type FormEventHandler, type MouseEventHandler, useState } from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "@/assets/branding/raid";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import { Optional } from "@/util/optional";
import RaidIcon from "../../../../assets/branding/raid/logo.svg";
import AlertContext, { mkAlert } from "../../../../stores/contexts/Alert";
import IntegrationCard from "../../IntegrationCard";
import { type IntegrationStates, useIntegrationsEndpoint } from "../../useIntegrationsEndpoint";

type RaidArgs = {
  integrationState: IntegrationStates["RAID"];
  update: (newIntegrationState: IntegrationStates["RAID"]) => void;
};

export interface RaidConnectedMessage extends Record<string, unknown> {
  type: "RAID_CONNECTED";
  alias: string;
  error?: string;
}

export const RAID_CONNECTION_CHANNEL = "rspace.apps.raid.connection";

const RaidIntegrationCard = ({ integrationState, update }: RaidArgs) => {
  const { t } = useTranslation("apps");
  const { saveAppOptions, deleteAppOptions } = useIntegrationsEndpoint();
  const { addAlert } = React.useContext(AlertContext);
  const authenticatedServers = useLocalObservable(() => [...integrationState.credentials.authenticatedServers]);
  const [addMenuAnchorEl, setAddMenuAnchorEl] = useState<null | HTMLElement>(null);

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
      console.log("RaidIntegrationCard: Ignoring unknown message", e.data);
      return;
    }

    runInAction(() => {
      const index = authenticatedServers.findIndex((s) => s.alias === e.data.alias);
      if (index === -1) {
        console.log("RaidIntegrationCard: Could not find server with alias", e.data.alias);
        return;
      }

      authenticatedServers[index].authenticated = true;

      addAlert(
        mkAlert({
          variant: "success",
          message: t("integrations.raid.alerts.connectSuccess", { alias: e.data.alias }),
        }),
      );
    });
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
        runInAction(() => {
          const index = authenticatedServers.findIndex((s) => s.alias === alias);
          authenticatedServers[index].authenticated = false;
        });
        addAlert(
          mkAlert({
            variant: "success",
            message: "Successfully disconnected.",
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
        await deleteAppOptions("RAID", optionsId);
        runInAction(() => {
          const index = authenticatedServers.findIndex((s) => s.alias === alias);
          authenticatedServers.splice(index, 1);
        });
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
      try {
        const newConfigs = await saveAppOptions("RAID", Optional.empty(), {
          RAID_ALIAS: alias,
          RAID_URL: url,
          RAID_OAUTH_CONNECTED: false,
        });
        setAddMenuAnchorEl(null);
        const optionIdsOfExistingServers = new Set(authenticatedServers.map(({ optionsId }) => optionsId));
        const newServer = newConfigs.credentials.authenticatedServers.find(
          ({ optionsId }) => !optionIdsOfExistingServers.has(optionsId),
        );
        if (!newServer) throw new Error("Save completed but cannot show results.");
        runInAction(() => {
          authenticatedServers.push({
            alias,
            url,
            authenticated: false,
            optionsId: newServer.optionsId,
          });
        });
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
        website="raid.org"
        docLink="raid"
        setupSection={
          <>
            <Typography variant="body1">{t("integrations.raid.setup.configure")}</Typography>
            <ol>
              <li>
                {t("integrations.raid.setup.seeAdminDocs")}{" "}
                <Link href="https://documentation.researchspace.com/article/zb4c2c8a4b-raid-integration">
                  {t("integrations.raid.setup.docLink")}
                </Link>{" "}
                {t("integrations.raid.setup.forMoreInfo")}
              </li>
              <li>
                {t("integrations.raid.setup.addServer")} <strong>{t("integrations.raid.setup.addButton")}</strong>{" "}
                {t("integrations.raid.setup.addServerSuffix")}
              </li>
              <li>
                {t("integrations.raid.setup.connectStep")} <strong>{t("integrations.raid.setup.connectButton")}</strong>{" "}
                {t("integrations.raid.setup.connectStepSuffix")}
              </li>
              <li>{t("integrations.raid.setup.startAssociating")}</li>
            </ol>
            <Typography variant="body1">{t("integrations.raid.multipleConfig")}</Typography>
            <Card variant="outlined" sx={{ mt: 2 }}>
              <CardContent>
                <Stack sx={{ gap: 2 }}>
                  {authenticatedServers.length === 0 && (
                    <Typography variant="body2">{t("integrations.raid.noServers")}</Typography>
                  )}
                  {authenticatedServers.map((server) => (
                    <Stack direction="row" spacing={1} key={server.alias} sx={{ justifyItems: "center" }}>
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
                        <Button type="submit">{t("actions.delete")}</Button>
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
                  {t("actions.add")}
                </Button>
                <Menu
                  open={Boolean(addMenuAnchorEl)}
                  anchorEl={addMenuAnchorEl}
                  onClose={() => setAddMenuAnchorEl(null)}
                >
                  {unauthenticatedServers.map(({ alias, url }) => (
                    <MenuItem key={alias} onClick={handleAddServer(alias, url)}>
                      <ListItemText primary={alias} secondary={url} />
                    </MenuItem>
                  ))}
                </Menu>
              </CardActions>
            </Card>
          </>
        }
        update={(newMode) => {
          return update({
            mode: newMode,
            credentials: integrationState.credentials,
          });
        }}
      />
    </Grid>
  );
};

/**
 * The card and dialog for configuring the RAiD integration
 */
export default observer(RaidIntegrationCard);
