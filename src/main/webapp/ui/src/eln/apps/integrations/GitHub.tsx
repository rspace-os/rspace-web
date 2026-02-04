import Grid from "@mui/material/Grid";
import React, { useContext, useState } from "react";
import IntegrationCard from "../IntegrationCard";
import {
  type IntegrationStates,
  useIntegrationsEndpoint,
} from "../useIntegrationsEndpoint";
import Table from "@mui/material/Table";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableBody from "@mui/material/TableBody";
import Button from "@mui/material/Button";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import { runInAction, observable } from "mobx";
import { observer, useLocalObservable } from "mobx-react-lite";
import { Optional } from "../../../util/optional";
import { useGitHubEndpoint, type Repository } from "../useGitHubEndpoint";
import ListItemText from "@mui/material/ListItemText";
import Typography from "@mui/material/Typography";
import RsSet from "../../../util/set";
import GitHubIcon from "../../../assets/branding/github/logo.svg";
import * as ArrayUtils from "../../../util/ArrayUtils";
import { LOGO_COLOR } from "../../../assets/branding/github";
import { useBroadcastChannel } from "use-broadcast-channel";

type UnwrapOptional<T> = T extends Optional<infer U> ? U : T;

type UnwrapArray<T extends Array<unknown>> = {
  [K in keyof T]: UnwrapOptional<T[K]>;
};

export interface GitHubConnectedMessage extends Record<string, unknown> {
  type: "GITHUB_CONNECTED";
  authToken: string;
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
    const { addAlert } = useContext(AlertContext);
    const { deleteAppOptions, saveAppOptions } = useIntegrationsEndpoint();
    const { getAllRepositories, oauthUrl } = useGitHubEndpoint();
    const copyOfRepos = useLocalObservable(() =>
      linkedRepos.map((c) => observable(c))
    );
    const [allRepositories, setAllRepositories] = useState<
      Optional<Array<Repository>>
    >(Optional.empty());
    const [accessToken, setAccessToken] = useState<string | null>(null);
    const [loadingAllRepositories, setLoadingAllRepositories] = useState(false);

    useBroadcastChannel<GitHubConnectedMessage>(
      GITHUB_CONNECTION_CHANNEL,
      (e: MessageEvent<GitHubConnectedMessage>) => {
        if (
          !e.data ||
          e.data.type !== "GITHUB_CONNECTED" ||
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
                  title: "Could not fetch listing of repositories",
                  message: error.message,
                })
              );
            }
          } finally {
            setLoadingAllRepositories(false);
          }
        })();
      }
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
              title: "Could not fetch listing of repositories",
              message: e.message,
            })
          );
        }
        setLoadingAllRepositories(false);
      }
    };

    return (
      <Grid container spacing={3} direction="column" sx={{ mt: 1 }}>
        <Grid item>
          <Typography component="h5" variant="subtitle1">
            Linked Repositories
          </Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell colSpan={2}>Repository Name</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {copyOfRepos.length === 0 && (
                <TableRow>
                  <TableCell colSpan={2}>
                    There are no linked repositories.
                  </TableCell>
                </TableRow>
              )}
              {copyOfRepos.map((config, i) => (
                <TableRow key={i}>
                  <TableCell>
                    <ListItemText
                      primary={config.GITHUB_REPOSITORY_FULL_NAME}
                      secondary={config.GITHUB_ACCESS_TOKEN.map(
                        () => null
                      ).orElse(
                        "Repository is in an invalid state. Please remove and re-add."
                      )}
                    />
                  </TableCell>
                  <TableCell>
                    <Button
                      onClick={() => {
                        void (async () => {
                          try {
                            await deleteAppOptions("GITHUB", config.optionsId);
                            const indexOfDeleted = copyOfRepos.findIndex(
                              (c) => c.optionsId === config.optionsId
                            );
                            runInAction(() => {
                              copyOfRepos.splice(indexOfDeleted, 1);
                              integrationState.credentials.splice(
                                indexOfDeleted,
                                1
                              );
                            });
                            addAlert(
                              mkAlert({
                                variant: "success",
                                message: "Successfully removed repository.",
                              })
                            );
                          } catch (e) {
                            if (e instanceof Error) {
                              addAlert(
                                mkAlert({
                                  variant: "error",
                                  title: "Failed to remove repository.",
                                  message: e.message,
                                })
                              );
                            }
                          }
                        })();
                      }}
                    >
                      Remove
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Grid>
        {allRepositories
          .map((repos) => (
            <Grid item key={null}>
              <Typography component="h5" variant="subtitle1">
                Link Additional Repositories
              </Typography>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell colSpan={2}>Repository Name</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {repos.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={2}>
                        There are no available repositories.
                      </TableCell>
                    </TableRow>
                  )}
                  {repos.map((repo, i) => (
                    <TableRow key={i}>
                      <TableCell>
                        <ListItemText
                          primary={repo.full_name}
                          secondary={repo.description}
                        />
                      </TableCell>
                      <TableCell>
                    <Button
                      onClick={() => {
                        void (async () => {
                          try {
                            const newState = await saveAppOptions(
                              "GITHUB",
                              Optional.empty(),
                              {
                                GITHUB_ACCESS_TOKEN: accessToken,
                                GITHUB_REPOSITORY_FULL_NAME: repo.full_name,
                              }
                            );
                            const optionIdsOfExistingRepos = new RsSet(
                              copyOfRepos.map(({ optionsId }) => optionsId)
                            );
                            runInAction(() => {
                              integrationState.credentials =
                                newState.credentials;
                              try {
                                const newlySavedRepo = new RsSet(
                                  newState.credentials
                                )
                                  .mapOptional((x) => x)
                                  .subtractMap(
                                    ({ optionsId }) => optionsId,
                                    optionIdsOfExistingRepos
                                  ).first;
                                copyOfRepos.push(observable(newlySavedRepo));
                              } catch {
                                throw new Error(
                                  "Save completed but cannot show results."
                                );
                              }
                              setAllRepositories(
                                allRepositories.map((allRepos) =>
                                  allRepos.filter(
                                    (r) => r.full_name !== repo.full_name
                                  )
                                )
                              );
                            });
                            addAlert(
                              mkAlert({
                                variant: "success",
                                message: "Successfully added repository.",
                              })
                            );
                          } catch (e) {
                            if (e instanceof Error) {
                              addAlert(
                                mkAlert({
                                  variant: "error",
                                  title: "Failed to add repository.",
                                  message: e.message,
                                })
                              );
                            }
                          }
                        })();
                      }}
                    >
                      Add
                    </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Grid>
          ))
          .orElse(null)}
        {allRepositories.isEmpty() && (
          <Grid item>
            <Button
              disabled={loadingAllRepositories}
              onClick={() => {
                void addHandler();
              }}
            >
              {loadingAllRepositories
                ? "Loading available repositories"
                : "Add"}
            </Button>
          </Grid>
        )}
      </Grid>
    );
  }
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
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="GitHub"
        integrationState={integrationState}
        explanatoryText="Store and manage your code through a software development and Git version control platform."
        image={GitHubIcon}
        color={LOGO_COLOR}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        usageText="You can link to projects, folders, or files stored in GitHub repositories directly from RSpace."
        helpLinkText="GitHub integration docs"
        website="github.com"
        docLink="github"
        setupSection={
          <>
            <ol>
              <li>
                Click on Connect to authorise RSpace to access your GitHub
                account.
              </li>
              <li>Select repositories you want to give RSpace access to.</li>
              <li>Enable the integration.</li>
              <li>
                When editing a document, click on the GitHub icon in the text
                editor toolbar.
              </li>
            </ol>
            {ArrayUtils.all(integrationState.credentials)
              .map((linkedRepos) => (
                <DialogContent
                  key={null}
                  linkedRepos={linkedRepos}
                  integrationState={integrationState}
                />
              ))
              .orElse("Error getting configured repositories")}
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(observer(GitHub));
