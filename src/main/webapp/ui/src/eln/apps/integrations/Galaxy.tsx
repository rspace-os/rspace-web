import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";
import ListItemText from "@mui/material/ListItemText";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { runInAction } from "mobx";
import { observer, useLocalObservable } from "mobx-react-lite";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "@/assets/branding/galaxy";
import GalaxyIcon from "../../../assets/branding/galaxy/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates, useIntegrationsEndpoint } from "../useIntegrationsEndpoint";

type GalaxyArgs = {
  integrationState: IntegrationStates["GALAXY"];
  update: (newIntegrationState: IntegrationStates["GALAXY"]) => void;
};

/*
 * Galaxy uses API-key based authentication, as implemented by the form below.
 */
function Galaxy({ integrationState, update }: GalaxyArgs): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const { saveAppOptions, deleteAppOptions } = useIntegrationsEndpoint();
  const { addAlert } = React.useContext(AlertContext);
  const authenticatedServers = useLocalObservable(() => [...integrationState.credentials.authenticatedServers]);
  const [addMenuAnchorEl, setAddMenuAnchorEl] = useState<null | HTMLElement>(null);

  const unauthenticatedServers = integrationState.credentials.configuredServers.filter(
    ({ alias }) => !authenticatedServers.find((s) => s.alias === alias),
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
        name={t("integrations.galaxy.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.galaxy.description")}
        image={GalaxyIcon}
        color={LOGO_COLOR}
        usageText={t("integrations.galaxy.usage")}
        helpLinkText={t("integrations.galaxy.helpLink")}
        website="galaxyproject.org"
        docLink="galaxy"
        setupSection={
          <>
            <ol>
              <li>{t("integrations.galaxy.setup.requestToken")}</li>
              <li>{t("integrations.galaxy.setup.chooseServer")}</li>
              <li>{t("integrations.galaxy.setup.enterToken")}</li>
              <li>{t("integrations.galaxy.setup.enable")}</li>
              <li>{t("integrations.galaxy.setup.toolbar")}</li>
            </ol>
            <Card variant="outlined" sx={{ mt: 2 }}>
              <CardContent>
                <Stack spacing={1}>
                  {authenticatedServers.length === 0 && (
                    <Typography variant="body2">{t("integrations.galaxy.noServers")}</Typography>
                  )}
                  {authenticatedServers.map((server) => (
                    <form
                      key={server.alias}
                      onSubmit={(event) => {
                        event.preventDefault();
                        void saveAppOptions("GALAXY", Optional.present(server.optionsId), {
                          GALAXY_ALIAS: server.alias,
                          GALAXY_URL: server.url,
                          GALAXY_APIKEY: server.apiKey,
                        })
                          .then(() => {
                            addAlert(
                              mkAlert({
                                variant: "success",
                                message: t("integrations.galaxy.alerts.saveSuccess"),
                              }),
                            );
                          })
                          .catch((e) => {
                            if (e instanceof Error)
                              addAlert(
                                mkAlert({
                                  variant: "error",
                                  title: t("integrations.galaxy.alerts.saveError"),
                                  message: e.message,
                                }),
                              );
                          });
                      }}
                    >
                      <Stack direction="row" spacing={1}>
                        <TextField
                          fullWidth
                          variant="outlined"
                          label={t("integrations.galaxy.apiKeyLabel", { alias: server.alias })}
                          type="password"
                          size="small"
                          value={server.apiKey}
                          onChange={({ target: { value } }) => {
                            runInAction(() => {
                              server.apiKey = value;
                            });
                          }}
                        />
                        <Button type="submit">{t("common:actions.save")}</Button>
                        <Button
                          onClick={() => {
                            void deleteAppOptions("GALAXY", server.optionsId)
                              .then(() => {
                                runInAction(() => {
                                  const indexOfRemovedServer = authenticatedServers.findIndex(
                                    (s) => s.alias === server.alias,
                                  );
                                  authenticatedServers.splice(indexOfRemovedServer, 1);
                                });
                                addAlert(
                                  mkAlert({
                                    variant: "success",
                                    message: t("integrations.galaxy.alerts.deleteSuccess"),
                                  }),
                                );
                              })
                              .catch((e) => {
                                if (e instanceof Error)
                                  addAlert(
                                    mkAlert({
                                      variant: "error",
                                      title: t("integrations.galaxy.alerts.deleteError"),
                                      message: e.message,
                                    }),
                                  );
                              });
                          }}
                        >
                          {t("common:actions.delete")}
                        </Button>
                      </Stack>
                    </form>
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
                    <MenuItem
                      key={alias}
                      onClick={() => {
                        void saveAppOptions("GALAXY", Optional.empty(), {
                          GALAXY_ALIAS: alias,
                          GALAXY_URL: url,
                          GALAXY_APIKEY: "",
                        })
                          .then((newConfigs) => {
                            setAddMenuAnchorEl(null);
                            const optionIdsOfExistingServers = new Set(
                              authenticatedServers.map(({ optionsId }) => optionsId),
                            );
                            const newServer = newConfigs.credentials.authenticatedServers.find(
                              ({ optionsId }) => !optionIdsOfExistingServers.has(optionsId),
                            );
                            if (!newServer) throw new Error("Save completed but cannot show results.");
                            runInAction(() => {
                              authenticatedServers.push({
                                alias,
                                url,
                                apiKey: "",
                                optionsId: newServer.optionsId,
                              });
                              addAlert(
                                mkAlert({
                                  variant: "success",
                                  message: t("integrations.galaxy.alerts.addSuccess"),
                                }),
                              );
                            });
                          })
                          .catch((e) => {
                            if (e instanceof Error)
                              addAlert(
                                mkAlert({
                                  variant: "error",
                                  title: t("integrations.galaxy.alerts.addError"),
                                  message: e.message,
                                }),
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

export default React.memo(observer(Galaxy));
