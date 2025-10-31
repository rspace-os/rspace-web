import Grid from "@mui/material/Grid";
import React, { useState } from "react";
import IntegrationCard from "../../IntegrationCard";
import {
  useIntegrationsEndpoint,
  type IntegrationStates,
} from "../../useIntegrationsEndpoint";
import { Optional } from "@/util/optional";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import Button from "@mui/material/Button";
import RaIDIcon from "../../../../assets/branding/raid/logo.svg";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import ListItemText from "@mui/material/ListItemText";
import Stack from "@mui/material/Stack";
import { useLocalObservable, observer } from "mobx-react-lite";
import { runInAction } from "mobx";
import AlertContext, { mkAlert } from "../../../../stores/contexts/Alert";
import Typography from "@mui/material/Typography";
import RsSet from "../../../../util/set";
import { LOGO_COLOR } from "@/assets/branding/raid";

type RaIDArgs = {
  integrationState: IntegrationStates["RAID"];
  update: (newIntegrationState: IntegrationStates["RAID"]) => void;
};

/*
 * RaID uses preconfigured servers. Users select
 */
const RaIDIntegrationCard = ({ integrationState, update }: RaIDArgs) => {
  const { saveAppOptions, deleteAppOptions } = useIntegrationsEndpoint();
  const { addAlert } = React.useContext(AlertContext);
  const authenticatedServers = useLocalObservable(() => [
    ...integrationState.credentials.authenticatedServers,
  ]);
  const [addMenuAnchorEl, setAddMenuAnchorEl] = useState<null | HTMLElement>(
    null
  );

  const unauthenticatedServers =
    integrationState.credentials.configuredServers.filter(
      ({ alias }) => !authenticatedServers.find((s) => s.alias === alias)
    );

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="RaID"
        integrationState={integrationState}
        explanatoryText="RaID"
        image={RaIDIcon}
        color={LOGO_COLOR}
        usageText="RaID"
        helpLinkText="RaID integration docs"
        website="example.com/raid"
        docLink="raid"
        setupSection={
          <>
            <p>Setup instructions here</p>
            <Card variant="outlined" sx={{ mt: 2 }}>
              <CardContent>
                <Stack spacing={1}>
                  {authenticatedServers.length === 0 && (
                    <Typography variant="body2">
                      No authenticated servers.
                    </Typography>
                  )}
                  {authenticatedServers.map((server) => (
                    <Stack direction="row" spacing={1} key={server.alias}>
                      <div>{server.alias} ({server.url}) (auth: {server.authenticated})</div>
                      {!server.authenticated ? (
                        <form
                          action={`/apps/raid/connect/${server.alias}`}
                          method="POST"
                          target="_blank"
                          rel="opener"
                        >
                          <Button type="submit" sx={{ mt: 1 }}>
                            Connect
                          </Button>
                        </form>
                      ) : (
                        <form
                          action={`/apps/raid/connect/${server.alias}`}
                          method="DELETE"
                          target="_blank"
                          rel="opener"
                        >
                          <Button type="submit" sx={{ mt: 1 }}>
                            Disconnect
                          </Button>
                        </form>
                      )}
                      <form
                        onSubmit={(event) => {
                          event.preventDefault();
                          void deleteAppOptions("RAID", server.optionsId)
                            .then(() => {
                              runInAction(() => {
                                const indexOfRemovedServer =
                                  authenticatedServers.findIndex(
                                    (s) => s.alias === server.alias
                                  );
                                authenticatedServers.splice(
                                  indexOfRemovedServer,
                                  1
                                );
                              });
                              addAlert(
                                mkAlert({
                                  variant: "success",
                                  message: "Successfully deleted connection.",
                                })
                              );
                            })
                            .catch((e) => {
                              if (e instanceof Error)
                                addAlert(
                                  mkAlert({
                                    variant: "error",
                                    title: "Could not delete connection.",
                                    message: e.message,
                                  })
                                );
                            });
                        }}
                      >
                        <Button type="submit">
                          Delete
                        </Button>
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
                    <MenuItem
                      key={alias}
                      onClick={() => {
                        void saveAppOptions("RAID", Optional.empty(), {
                          RAID_ALIAS: alias,
                          RAID_URL: url,
                          RAID_OAUTH_CONNECTED: false,
                        })
                          .then((newConfigs) => {
                            setAddMenuAnchorEl(null);
                            const optionIdsOfExistingServers = new RsSet(
                              authenticatedServers.map(
                                ({ optionsId }) => optionsId
                              )
                            );
                            const optionIdsOfNewServers = new RsSet(
                              newConfigs.credentials.authenticatedServers.map(
                                ({ optionsId }) => optionsId
                              )
                            );
                            const newOptionId = optionIdsOfNewServers.subtract(
                              optionIdsOfExistingServers
                            ).first;
                            runInAction(() => {
                              authenticatedServers.push({
                                alias,
                                url,
                                optionsId: newOptionId,
                              });
                              addAlert(
                                mkAlert({
                                  variant: "success",
                                  message:
                                    "Successfully added new RaID server.",
                                })
                              );
                            });
                          })
                          .catch((e) => {
                            if (e instanceof Error)
                              addAlert(
                                mkAlert({
                                  variant: "error",
                                  title: "Error added new RaID server.",
                                  message: e.message,
                                })
                              );
                          });
                      }}
                    >
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
}

/**
 * The card and dialog for configuring the RaID integration
 */
export default React.memo(observer(RaIDIntegrationCard));
