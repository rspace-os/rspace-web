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
import { useBroadcastChannel } from "use-broadcast-channel";
import { LOGO_COLOR } from "@/assets/branding/raid";
import { Optional } from "@/util/optional";
import RaIDIcon from "../../../../assets/branding/raid/logo.svg";
import AlertContext, { mkAlert } from "../../../../stores/contexts/Alert";
import RsSet from "../../../../util/set";
import IntegrationCard from "../../IntegrationCard";
import { type IntegrationStates, useIntegrationsEndpoint } from "../../useIntegrationsEndpoint";

type RaIDArgs = {
    integrationState: IntegrationStates["RAID"];
    update: (newIntegrationState: IntegrationStates["RAID"]) => void;
};

export interface RaIDConnectedMessage extends Record<string, unknown> {
    type: "RAID_CONNECTED";
    alias: string;
}

export const RAID_CONNECTION_CHANNEL = "rspace.apps.raid.connection";

/*
 * RaID uses preconfigured servers. Users select
 */
const RaIDIntegrationCard = ({ integrationState, update }: RaIDArgs) => {
    const { saveAppOptions, deleteAppOptions } = useIntegrationsEndpoint();
    const { addAlert } = React.useContext(AlertContext);
    const authenticatedServers = useLocalObservable(() => [...integrationState.credentials.authenticatedServers]);
    const [addMenuAnchorEl, setAddMenuAnchorEl] = useState<null | HTMLElement>(null);

    useBroadcastChannel<RaIDConnectedMessage>(RAID_CONNECTION_CHANNEL, (e: MessageEvent<RaIDConnectedMessage>) => {
        if (!e.data || e.data.type !== "RAID_CONNECTED" || !e.data.alias) {
            console.log("RaIDIntegrationCard: Ignoring unknown message", e.data);
            return;
        }

        runInAction(() => {
            const index = authenticatedServers.findIndex((s) => s.alias === e.data.alias);
            if (index === -1) {
                console.log("RaIDIntegrationCard: Could not find server with alias", e.data.alias);
                return;
            }

            authenticatedServers[index].authenticated = true;

            addAlert(
                mkAlert({
                    variant: "success",
                    message: `Successfully connected to ${e.data.alias} RaID server.`,
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
                            title: `Could not disconnect ${alias} RaID connection`,
                            message: `Server responded with status ${response.status}: ${response.statusText}`,
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
                        title: `Could not disconnect ${alias} RaID connection`,
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
                        message: "Successfully deleted connection.",
                    }),
                );
            } catch (e) {
                addAlert(
                    mkAlert({
                        variant: "error",
                        title: `Could not disconnect ${alias} RaID connection.`,
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
                const optionIdsOfExistingServers = new RsSet(authenticatedServers.map(({ optionsId }) => optionsId));
                const optionIdsOfNewServers = new RsSet(
                    newConfigs.credentials.authenticatedServers.map(({ optionsId }) => optionsId),
                );
                const newOptionId = optionIdsOfNewServers.subtract(optionIdsOfExistingServers).first;
                runInAction(() => {
                    authenticatedServers.push({
                        alias,
                        url,
                        authenticated: false,
                        optionsId: newOptionId,
                    });
                });
                addAlert(
                    mkAlert({
                        variant: "success",
                        message: "Successfully added new RaID server.",
                    }),
                );
            } catch (e) {
                addAlert(
                    mkAlert({
                        variant: "error",
                        title: `Could not add ${alias} as a new RaID connection.`,
                        message: e instanceof Error ? e.message : JSON.stringify(e),
                    }),
                );
            }
        };

    return (
        <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
            <IntegrationCard
                name="RaID"
                integrationState={integrationState}
                explanatoryText="Incorporate Research Activity Identifiers (RAiDs) into your projects and report your research activities to your RAiD records."
                image={RaIDIcon}
                color={LOGO_COLOR}
                usageText="Connect your RSpace projects to RAiD (Research Activity Identifiers) to track and report research activities such as repository exports. RAiD provides persistent identifiers for research projects, enabling seamless reporting of research activities to funders and institutions."
                helpLinkText="documentation"
                website="raid.org"
                docLink="raid"
                setupSection={
                    <>
                        <Typography variant="body1">
                            Configure your RAiD service point to enable authentication and project association:
                        </Typography>
                        <ol>
                            <li>
                                Ask your system administrator to set up RaID server connections. See{" "}
                                <Link href="https://documentation.researchspace.com/article/zb4c2c8a4b-raid-integration">
                                    our documentation for system administrators
                                </Link>{" "}
                                for more information.
                            </li>
                            <li>
                                Click the <strong>Add</strong> button below and select the RaID server you would like to
                                connect to.
                            </li>
                            <li>
                                Once the server shows up in the server list, click on the <strong>Connect</strong>{" "}
                                button and log in with your RaID credentials.
                            </li>
                            <li>Start associating RAiDs with your project groups.</li>
                        </ol>
                        <Typography variant="body1">
                            Multiple service points can be added to support different RAiD registries. Each project
                            group owner can authenticate and manage their own RAiD associations.
                        </Typography>
                        <Card variant="outlined" sx={{ mt: 2 }}>
                            <CardContent>
                                <Stack gap={2}>
                                    {authenticatedServers.length === 0 && (
                                        <Typography variant="body2">No authenticated servers.</Typography>
                                    )}
                                    {authenticatedServers.map((server) => (
                                        <Stack direction="row" spacing={1} key={server.alias} justifyItems="center">
                                            <Stack direction="column" flexGrow={1} gap={1}>
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
                                                    rel="opener"
                                                >
                                                    <Button type="submit">Connect</Button>
                                                </form>
                                            ) : (
                                                <form
                                                    onSubmit={handleDisconnect(server.alias)}
                                                    action={`/apps/raid/connect/${server.alias}`}
                                                    method="DELETE"
                                                    target="_blank"
                                                    rel="opener"
                                                >
                                                    <Button type="submit">Disconnect</Button>
                                                </form>
                                            )}
                                            <form onSubmit={handleDeleteConnection(server.optionsId, server.alias)}>
                                                <Button type="submit">Delete</Button>
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
                                    Add
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
 * The card and dialog for configuring the RaID integration
 */
export default observer(RaIDIntegrationCard);
