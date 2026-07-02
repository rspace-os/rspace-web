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
import { LOGO_COLOR } from "../../../assets/branding/pyrat";
import PyratIcon from "../../../assets/branding/pyrat/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates, useIntegrationsEndpoint } from "../useIntegrationsEndpoint";

type PyratArgs = {
  integrationState: IntegrationStates["PYRAT"];
  update: (newIntegrationState: IntegrationStates["PYRAT"]) => void;
};

/*
 * Pyrat uses API-key based authentication, as implemeted by the form below.
 */
function Pyrat({ integrationState, update }: PyratArgs): React.ReactNode {
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
        name={t("integrations.pyrat.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.pyrat.description")}
        image={PyratIcon}
        color={LOGO_COLOR}
        usageText={t("integrations.pyrat.usage")}
        helpLinkText={t("integrations.pyrat.helpLink")}
        website="scionics.com/pyrat"
        docLink="pyrat"
        setupSection={
          <>
            <ol>
              <li>{t("integrations.pyrat.setup.requestToken")}</li>
              <li>{t("integrations.pyrat.setup.chooseServer")}</li>
              <li>{t("integrations.pyrat.setup.enterToken")}</li>
              <li>{t("integrations.pyrat.setup.enable")}</li>
              <li>{t("integrations.pyrat.setup.toolbar")}</li>
            </ol>
            <Card variant="outlined" sx={{ mt: 2 }}>
              <CardContent>
                <Stack spacing={1}>
                  {authenticatedServers.length === 0 && (
                    <Typography variant="body2">{t("integrations.pyrat.noServers")}</Typography>
                  )}
                  {authenticatedServers.map((server) => (
                    <form
                      key={server.alias}
                      onSubmit={(event) => {
                        event.preventDefault();
                        void saveAppOptions("PYRAT", Optional.present(server.optionsId), {
                          PYRAT_ALIAS: server.alias,
                          PYRAT_URL: server.url,
                          PYRAT_APIKEY: server.apiKey,
                        })
                          .then(() => {
                            addAlert(
                              mkAlert({
                                variant: "success",
                                message: t("integrations.pyrat.alerts.saveSuccess"),
                              }),
                            );
                          })
                          .catch((e) => {
                            if (e instanceof Error)
                              addAlert(
                                mkAlert({
                                  variant: "error",
                                  title: t("integrations.pyrat.alerts.saveError"),
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
                          label={t("integrations.pyrat.apiKeyLabel", { alias: server.alias })}
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
                            void deleteAppOptions("PYRAT", server.optionsId)
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
                                    message: t("integrations.pyrat.alerts.deleteSuccess"),
                                  }),
                                );
                              })
                              .catch((e) => {
                                if (e instanceof Error)
                                  addAlert(
                                    mkAlert({
                                      variant: "error",
                                      title: t("integrations.pyrat.alerts.deleteError"),
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
                        void saveAppOptions("PYRAT", Optional.empty(), {
                          PYRAT_ALIAS: alias,
                          PYRAT_URL: url,
                          PYRAT_APIKEY: "",
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
                                  message: t("integrations.pyrat.alerts.addSuccess"),
                                }),
                              );
                            });
                          })
                          .catch((e) => {
                            if (e instanceof Error)
                              addAlert(
                                mkAlert({
                                  variant: "error",
                                  title: t("integrations.pyrat.alerts.addError"),
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

/**
 * The card and dialog for configuring the PyRAT integration
 */
export default React.memo(observer(Pyrat));
