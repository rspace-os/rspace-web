import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { observable, runInAction } from "mobx";
import { observer, useLocalObservable } from "mobx-react-lite";
import React, { useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/slack";
import SlackIcon from "../../../assets/branding/slack/logo.svg";
import DescriptionList from "../../../components/DescriptionList";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as ArrayUtils from "../../../util/ArrayUtils";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates, useIntegrationsEndpoint } from "../useIntegrationsEndpoint";
import { useSlackEndpoint } from "../useSlackEndpoint";

type UnwrapOptional<T> = T extends Optional<infer U> ? U : T;

type UnwrapArray<T extends Array<unknown>> = {
  [K in keyof T]: UnwrapOptional<T[K]>;
};

export interface SlackConnectedMessage extends Record<string, unknown> {
  type: "SLACK_CONNECTED";
  error?: string;
}
export const SLACK_CONNECTION_CHANNEL = "rspace.apps.slack.connection";

const DialogContent = observer(
  ({
    linkedChannels,
    integrationState,
  }: {
    linkedChannels: UnwrapArray<IntegrationStates["SLACK"]["credentials"]>;
    integrationState: IntegrationStates["SLACK"];
  }) => {
    const { t } = useTranslation(["apps", "common"]);
    const [loadingNewChannel, setLoadingNewChannel] = useState(false);
    const { addAlert } = useContext(AlertContext);
    const { oauthUrl } = useSlackEndpoint();
    const { allIntegrations, saveAppOptions, deleteAppOptions } = useIntegrationsEndpoint();
    const copyOfChannels = useLocalObservable(() => linkedChannels.map((x) => observable(x)));

    useBroadcastChannel<SlackConnectedMessage>(SLACK_CONNECTION_CHANNEL, (e: MessageEvent<SlackConnectedMessage>) => {
      if (e.data?.type !== "SLACK_CONNECTED") return;
      if (e.data.error) {
        addAlert(
          mkAlert({
            variant: "error",
            title: t("integrations.slack.alerts.connectError"),
            message: e.data.error,
          }),
        );
        setLoadingNewChannel(false);
        return;
      }
      // the server saved the channel when Slack redirected back, so fetch it
      void (async () => {
        try {
          const { SLACK } = await allIntegrations();
          const channels = ArrayUtils.all(SLACK.credentials)
            .toResult(() => new Error("Save completed but cannot show results."))
            .elseThrow();
          const existingIds = new Set(copyOfChannels.map(({ optionsId }) => optionsId));
          runInAction(() => {
            integrationState.credentials = SLACK.credentials;
            copyOfChannels.push(...channels.filter((c) => !existingIds.has(c.optionsId)).map((c) => observable(c)));
          });
          addAlert(
            mkAlert({
              variant: "success",
              message: t("integrations.slack.alerts.addSuccess"),
            }),
          );
        } catch (e) {
          if (e instanceof Error)
            addAlert(
              mkAlert({
                variant: "error",
                title: t("integrations.slack.alerts.addError"),
                message: e.message,
              }),
            );
        } finally {
          setLoadingNewChannel(false);
        }
      })();
    });

    const addHandler = async () => {
      setLoadingNewChannel(true);
      try {
        const authWindow = window.open(await oauthUrl());
        if (!authWindow) {
          throw new Error("Failed to open Slack authentication window");
        }
      } catch (e) {
        if (e instanceof Error)
          addAlert(
            mkAlert({
              variant: "error",
              title: t("integrations.slack.alerts.channelDetailsError"),
              message: e.message,
            }),
          );
        setLoadingNewChannel(false);
      }
    };
    return (
      <Stack spacing={1} sx={{ mt: 1 }}>
        {copyOfChannels.map((channel, index) => (
          <Box key={channel.optionsId}>
            <Card variant="outlined">
              <form
                onSubmit={(event) => {
                  void (async () => {
                    event.preventDefault();
                    try {
                      const params: { optionsId: unknown } = {
                        ...channel,
                      };
                      delete params.optionsId;
                      const newState = await saveAppOptions("SLACK", Optional.present(channel.optionsId), params);
                      runInAction(() => {
                        integrationState.credentials = newState.credentials;
                        const newCreds = ArrayUtils.all(newState.credentials)
                          .toResult(() => new Error("Save completed but cannot show results"))
                          .elseThrow();
                        const indexOfNewConfig = newCreds.findIndex((c) => c.optionsId === channel.optionsId);
                        if (indexOfNewConfig === -1) throw new Error("Save completed but cannot show results.");

                        copyOfChannels.splice(index, 1, observable(newCreds[indexOfNewConfig]));
                      });
                      addAlert(
                        mkAlert({
                          variant: "success",
                          message: t("integrations.slack.alerts.labelSuccess"),
                        }),
                      );
                    } catch (e) {
                      if (e instanceof Error)
                        addAlert(
                          mkAlert({
                            variant: "error",
                            title: t("integrations.slack.alerts.labelError"),
                            message: e.message,
                          }),
                        );
                    }
                  })();
                }}
              >
                <CardContent>
                  <Stack spacing={2}>
                    <DescriptionList
                      content={[
                        {
                          label: t("integrations.slack.fields.workspace"),
                          value: channel.SLACK_TEAM_NAME,
                        },
                        {
                          label: t("integrations.slack.fields.channelName"),
                          value: channel.SLACK_CHANNEL_NAME,
                        },
                      ]}
                    />
                    <TextField
                      fullWidth
                      value={channel.SLACK_CHANNEL_LABEL}
                      onChange={({ target: { value } }) => {
                        runInAction(() => {
                          channel.SLACK_CHANNEL_LABEL = value;
                        });
                      }}
                      label={t("integrations.slack.fields.rspaceLabel")}
                    />
                  </Stack>
                </CardContent>
                <CardActions>
                  <Button type="submit">{t("common:actions.save")}</Button>
                  <Button
                    onClick={() => {
                      void (async () => {
                        try {
                          await deleteAppOptions("SLACK", channel.optionsId);
                          runInAction(() => {
                            const deletedIndex = copyOfChannels.indexOf(channel);
                            copyOfChannels.splice(deletedIndex, 1);
                            integrationState.credentials.splice(deletedIndex, 1);
                          });
                          addAlert(
                            mkAlert({
                              variant: "success",
                              message: t("integrations.slack.alerts.deleteSuccess"),
                            }),
                          );
                        } catch (e) {
                          if (e instanceof Error)
                            addAlert(
                              mkAlert({
                                variant: "error",
                                title: t("integrations.slack.alerts.deleteError"),
                                message: e.message,
                              }),
                            );
                        }
                      })();
                    }}
                  >
                    {t("common:actions.remove")}
                  </Button>
                </CardActions>
              </form>
            </Card>
          </Box>
        ))}
        <Box>
          <Button disabled={loadingNewChannel} onClick={() => void addHandler()}>
            {loadingNewChannel ? t("integrations.slack.loadingChannel") : t("common:actions.add")}
          </Button>
        </Box>
      </Stack>
    );
  },
);

type SlackArgs = {
  integrationState: IntegrationStates["SLACK"];
  update: (newIntegrationState: IntegrationStates["SLACK"]) => void;
};

/*
 * Slack uses OAuth authentication, but the credential is stored on a
 * per-channel basis so the user has to reauthenticate whenever they wish
 * to add another channel. For this reason, the current implementation is a
 * little bit of hack and should be further refined once the old apps page has
 * been deprecated.
 */
function Slack({ integrationState, update }: SlackArgs): React.ReactNode {
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
        name={t("integrations.slack.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.slack.description")}
        image={SlackIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: integrationState.credentials })}
        usageText={t("integrations.slack.usage")}
        helpLinkText={t("integrations.slack.helpLink")}
        website="https://slack.com"
        docLink="slack"
        setupSection={
          <>
            <Typography variant="body2">
              <TransRichText i18nKey="apps:integrations.slack.setup.instructions" />
            </Typography>
            {ArrayUtils.all(integrationState.credentials)
              .map((linkedChannels) => (
                <DialogContent key={null} linkedChannels={linkedChannels} integrationState={integrationState} />
              ))
              .orElse(t("integrations.slack.orElse"))}
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(Slack);
