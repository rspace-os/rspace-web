import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import ListItemText from "@mui/material/ListItemText";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { observable, runInAction } from "mobx";
import { observer, useLocalObservable } from "mobx-react-lite";
import React, { useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/github";
import GitHubIcon from "../../../assets/branding/github/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as ArrayUtils from "../../../util/ArrayUtils";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import { type Repository, useGitHubEndpoint } from "../useGitHubEndpoint";
import { type IntegrationStates, useIntegrationsEndpoint } from "../useIntegrationsEndpoint";

type UnwrapOptional<T> = T extends Optional<infer U> ? U : T;

type UnwrapArray<T extends Array<unknown>> = {
  [K in keyof T]: UnwrapOptional<T[K]>;
};

export interface GitHubConnectedMessage extends Record<string, unknown> {
  type: "GITHUB_CONNECTED";
  authToken: string;
  error?: string;
}

export const GITHUB_CONNECTION_CHANNEL = "rspace.apps.github.connection";

const DialogContent = observer(
  ({
    linkedRepos,
    integrationState,
  }: {
    linkedRepos: UnwrapArray<IntegrationStates["GITHUB"]["credentials"]>;
    integrationState: IntegrationStates["GITHUB"];
  }) => {
    const { t } = useTranslation(["apps", "common"]);
    const { addAlert } = useContext(AlertContext);
    const { deleteAppOptions, saveAppOptions } = useIntegrationsEndpoint();
    const { getAllRepositories, oauthUrl } = useGitHubEndpoint();
    const copyOfRepos = useLocalObservable(() => linkedRepos.map((c) => observable(c)));
    const [allRepositories, setAllRepositories] = useState<Optional<Array<Repository>>>(Optional.empty());
    const [accessToken, setAccessToken] = useState<string | null>(null);
    const [loadingAllRepositories, setLoadingAllRepositories] = useState(false);

    useBroadcastChannel<GitHubConnectedMessage>(
      GITHUB_CONNECTION_CHANNEL,
      (e: MessageEvent<GitHubConnectedMessage>) => {
        if (e.data?.type === "GITHUB_CONNECTED" && e.data.error) {
          setLoadingAllRepositories(false);
          addAlert(
            mkAlert({
              variant: "error",
              title: t("integrations.github.alerts.connectError"),
              message: e.data.error,
            }),
          );
          return;
        }
        if (
          e.data?.type !== "GITHUB_CONNECTED" ||
          typeof e.data.authToken !== "string" ||
          e.data.authToken.length === 0
        ) {
          console.log("GitHub: Ignoring unknown message", e.data);
          return;
        }

        void (async () => {
          try {
            setAccessToken(e.data.authToken);
            const response = await getAllRepositories(e.data.authToken);
            setAllRepositories(Optional.present(response));
          } catch (error) {
            if (error instanceof Error) {
              addAlert(
                mkAlert({
                  variant: "error",
                  title: t("integrations.github.alerts.fetchError"),
                  message: error.message,
                }),
              );
            }
          } finally {
            setLoadingAllRepositories(false);
          }
        })();
      },
    );

    const addHandler = async () => {
      setLoadingAllRepositories(true);
      try {
        const authWindow = window.open(await oauthUrl());
        if (!authWindow) {
          throw new Error("Failed to open GitHub authentication window");
        }
      } catch (e) {
        if (e instanceof Error) {
          addAlert(
            mkAlert({
              variant: "error",
              title: t("integrations.github.alerts.fetchError"),
              message: e.message,
            }),
          );
        }
        setLoadingAllRepositories(false);
      }
    };

    return (
      <Stack spacing={3} sx={{ mt: 1 }}>
        <Box>
          <Typography component="h5" variant="subtitle1">
            {t("integrations.github.repositories.linkedHeading")}
          </Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell colSpan={2}>{t("integrations.github.repositories.nameHeader")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {copyOfRepos.length === 0 && (
                <TableRow>
                  <TableCell colSpan={2}>{t("integrations.github.repositories.noLinked")}</TableCell>
                </TableRow>
              )}
              {copyOfRepos.map((config, i) => (
                <TableRow key={i}>
                  <TableCell>
                    <ListItemText
                      primary={config.GITHUB_REPOSITORY_FULL_NAME}
                      secondary={config.GITHUB_ACCESS_TOKEN.map(() => null).orElse(
                        t("integrations.github.repositories.invalidState"),
                      )}
                    />
                  </TableCell>
                  <TableCell>
                    <Button
                      onClick={() => {
                        void (async () => {
                          try {
                            await deleteAppOptions("GITHUB", config.optionsId);
                            const indexOfDeleted = copyOfRepos.findIndex((c) => c.optionsId === config.optionsId);
                            runInAction(() => {
                              copyOfRepos.splice(indexOfDeleted, 1);
                              integrationState.credentials.splice(indexOfDeleted, 1);
                            });
                            addAlert(
                              mkAlert({
                                variant: "success",
                                message: t("integrations.github.alerts.removeSuccess"),
                              }),
                            );
                          } catch (e) {
                            if (e instanceof Error) {
                              addAlert(
                                mkAlert({
                                  variant: "error",
                                  title: t("integrations.github.alerts.removeError"),
                                  message: e.message,
                                }),
                              );
                            }
                          }
                        })();
                      }}
                    >
                      {t("common:actions.remove")}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
        {allRepositories
          .map((repos) => (
            <Box key={null}>
              <Typography component="h5" variant="subtitle1">
                {t("integrations.github.repositories.additionalHeading")}
              </Typography>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell colSpan={2}>{t("integrations.github.repositories.nameHeader")}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {repos.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={2}>{t("integrations.github.repositories.noAvailable")}</TableCell>
                    </TableRow>
                  )}
                  {repos.map((repo, i) => (
                    <TableRow key={i}>
                      <TableCell>
                        <ListItemText primary={repo.full_name} secondary={repo.description} />
                      </TableCell>
                      <TableCell>
                        <Button
                          onClick={() => {
                            void (async () => {
                              try {
                                const newState = await saveAppOptions("GITHUB", Optional.empty(), {
                                  GITHUB_ACCESS_TOKEN: accessToken,
                                  GITHUB_REPOSITORY_FULL_NAME: repo.full_name,
                                });
                                const optionIdsOfExistingRepos = new Set(copyOfRepos.map(({ optionsId }) => optionsId));
                                runInAction(() => {
                                  integrationState.credentials = newState.credentials;
                                  try {
                                    const newlySavedRepo = newState.credentials
                                      .find((credential) =>
                                        credential
                                          .map(({ optionsId }) => !optionIdsOfExistingRepos.has(optionsId))
                                          .orElse(false),
                                      )
                                      ?.orElseGet(() => {
                                        throw new Error("Save completed but cannot show results.");
                                      });
                                    if (!newlySavedRepo) throw new Error("Save completed but cannot show results.");
                                    copyOfRepos.push(observable(newlySavedRepo));
                                  } catch {
                                    throw new Error("Save completed but cannot show results.");
                                  }
                                  setAllRepositories(
                                    allRepositories.map((allRepos) =>
                                      allRepos.filter((r) => r.full_name !== repo.full_name),
                                    ),
                                  );
                                });
                                addAlert(
                                  mkAlert({
                                    variant: "success",
                                    message: t("integrations.github.alerts.addSuccess"),
                                  }),
                                );
                              } catch (e) {
                                if (e instanceof Error) {
                                  addAlert(
                                    mkAlert({
                                      variant: "error",
                                      title: t("integrations.github.alerts.addError"),
                                      message: e.message,
                                    }),
                                  );
                                }
                              }
                            })();
                          }}
                        >
                          {t("common:actions.add")}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          ))
          .orElse(null)}
        {allRepositories.isEmpty() && (
          <Box>
            <Button
              disabled={loadingAllRepositories}
              onClick={() => {
                void addHandler();
              }}
            >
              {loadingAllRepositories ? t("integrations.github.repositories.loading") : t("common:actions.add")}
            </Button>
          </Box>
        )}
      </Stack>
    );
  },
);

type GitHubArgs = {
  /*
   * This is the current state of the GitHub integration. It must be a mobx
   * observable because this component will mutate it when a new repository is
   * added or an existing one is removed from the configured credentials.
   */
  integrationState: IntegrationStates["GITHUB"];

  /*
   * Event handler that is called when the user enables or disabled the
   * integration. Not called at any other time.
   */
  update: (newIntegrationState: IntegrationStates["GITHUB"]) => void;
};

/*
 * GitHub uses OAuth authentication, but the credential is stored on a
 * per-repository basis so the user has to reauthenticate whenever they wish
 * to view the listing of all of their repositories to link more. For this
 * reason, the current implementation is a little bit of hack and should be
 * further refined once the old apps page has been deprecated.
 */
function GitHub({ integrationState, update }: GitHubArgs): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.github.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.github.description")}
        image={GitHubIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: integrationState.credentials })}
        usageText={t("integrations.github.usage")}
        helpLinkText={t("integrations.github.helpLink")}
        website="https://github.com"
        docLink="y2080yw30x-github-integration"
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.github.setup.instructions" />
            {ArrayUtils.all(integrationState.credentials)
              .map((linkedRepos) => (
                <DialogContent key={null} linkedRepos={linkedRepos} integrationState={integrationState} />
              ))
              .orElse(t("integrations.github.orElse"))}
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(observer(GitHub));
