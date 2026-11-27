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
import React, { useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/github";
import GitHubIcon from "../../../assets/branding/github/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as ArrayUtils from "../../../util/ArrayUtils";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import {
  useDeleteAppOptionsMutation,
  useIntegrationModeUpdate,
  useIntegrationState,
  useSaveAppOptionsMutation,
} from "../queries";
import { type Repository, useGitHubEndpoint } from "../useGitHubEndpoint";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type UnwrapOptional<T> = T extends Optional<infer U> ? U : T;

type UnwrapArray<T extends Array<unknown>> = {
  [K in keyof T]: UnwrapOptional<T[K]>;
};

export interface GitHubConnectedMessage extends Record<string, unknown> {
  type: "GITHUB_CONNECTED";
  error?: string;
}

export const GITHUB_CONNECTION_CHANNEL = "rspace.apps.github.connection";

function DialogContent({ linkedRepos }: { linkedRepos: UnwrapArray<IntegrationStates["GITHUB"]["credentials"]> }) {
  const { t } = useTranslation(["apps", "common"]);
  const { addAlert } = useContext(AlertContext);
  const { mutateAsync: deleteAppOptions } = useDeleteAppOptionsMutation("GITHUB");
  const { mutateAsync: saveAppOptions } = useSaveAppOptionsMutation("GITHUB");
  const { getAllRepositories, oauthUrl } = useGitHubEndpoint();
  const [allRepositories, setAllRepositories] = useState<Optional<Array<Repository>>>(Optional.empty());
  const [loadingAllRepositories, setLoadingAllRepositories] = useState(false);
  /*
   * Removing the last linked repository deletes the GitHub token, so adding
   * another needs OAuth again. Deriving this from the list itself, rather than
   * in each remove handler, also holds when several removals overlap.
   */
  const noLinkedRepos = linkedRepos.length === 0;
  useEffect(() => {
    if (noLinkedRepos) setAllRepositories(Optional.empty());
  }, [noLinkedRepos]);

  useBroadcastChannel<GitHubConnectedMessage>(GITHUB_CONNECTION_CHANNEL, (e: MessageEvent<GitHubConnectedMessage>) => {
    if (e.data?.type !== "GITHUB_CONNECTED") {
      console.log("GitHub: Ignoring unknown message", e.data);
      return;
    }
    if (e.data.error) {
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
    void (async () => {
      try {
        const response = await getAllRepositories();
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
  });

  const addHandler = async () => {
    setLoadingAllRepositories(true);
    try {
      const authWindow = window.open(await oauthUrl());
      if (!authWindow) {
        throw new Error(t("integrations.github.errors.authWindowFailed"));
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
            {linkedRepos.length === 0 && (
              <TableRow>
                <TableCell colSpan={2}>{t("integrations.github.repositories.noLinked")}</TableCell>
              </TableRow>
            )}
            {linkedRepos.map((config) => (
              <TableRow key={config.optionsId}>
                <TableCell>
                  <ListItemText primary={config.GITHUB_REPOSITORY_FULL_NAME} />
                </TableCell>
                <TableCell>
                  <Button
                    onClick={() => {
                      void (async () => {
                        try {
                          await deleteAppOptions(config.optionsId);
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
        // Reconnecting lists every repository again, including linked ones; adding one twice would create a duplicate.
        .map((repos) =>
          repos.filter((repo) => !linkedRepos.some((linked) => linked.GITHUB_REPOSITORY_FULL_NAME === repo.full_name)),
        )
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
                              const newState = await saveAppOptions({
                                optionsId: Optional.empty(),
                                option: { GITHUB_REPOSITORY_FULL_NAME: repo.full_name },
                              });
                              const saved = newState.credentials.some((credential) =>
                                credential
                                  .map(
                                    ({ GITHUB_REPOSITORY_FULL_NAME }) => GITHUB_REPOSITORY_FULL_NAME === repo.full_name,
                                  )
                                  .orElse(false),
                              );
                              if (!saved) throw new Error("Save completed but cannot show results.");
                              setAllRepositories((current) =>
                                current.map((allRepos) => allRepos.filter((r) => r.full_name !== repo.full_name)),
                              );
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
      <Box>
        <Button
          disabled={loadingAllRepositories}
          onClick={() => {
            void addHandler();
          }}
        >
          {loadingAllRepositories
            ? t("integrations.github.repositories.loading")
            : allRepositories.isEmpty()
              ? t("common:actions.add")
              : t("actions.connect")}
        </Button>
      </Box>
    </Stack>
  );
}

/*
 * GitHub uses OAuth authentication. Clicking Add opens the GitHub OAuth flow in
 * a new window; on the callback the server stores the user's GitHub token in
 * their UserConnection and the window broadcasts GITHUB_CONNECTED. This dialog
 * then fetches the user's repositories through the server, which uses the
 * stored token, and saves only the names of the repositories the user links.
 * The token never reaches the browser.
 */
function GitHub(): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const integrationState = useIntegrationState("GITHUB");
  const updateMode = useIntegrationModeUpdate("GITHUB");
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
        update={updateMode}
        usageText={t("integrations.github.usage")}
        helpLinkText={t("integrations.github.helpLink")}
        website="https://github.com"
        docLink="github"
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.github.setup.instructions" />
            {ArrayUtils.all(integrationState.credentials)
              .map((linkedRepos) => <DialogContent key={null} linkedRepos={linkedRepos} />)
              .orElse(t("integrations.github.orElse"))}
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(GitHub);
