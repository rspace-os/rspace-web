import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { useForm } from "@tanstack/react-form";
import React, { useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/slack";
import SlackIcon from "../../../assets/branding/slack/logo.svg";
import DescriptionList from "../../../components/DescriptionList";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as ArrayUtils from "../../../util/ArrayUtils";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import {
  useDeleteAppOptionsMutation,
  useIntegrationModeUpdate,
  useIntegrationMutation,
  useIntegrationState,
  useSaveAppOptionsMutation,
} from "../queries";
import { fetchAllIntegrations, type IntegrationStates } from "../useIntegrationsEndpoint";
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

type Channel = UnwrapArray<IntegrationStates["SLACK"]["credentials"]>[number];

const channelLabelSchema = v.object({
  label: v.string(),
});

function ChannelForm({ channel }: { channel: Channel }) {
  const { t } = useTranslation(["apps", "common"]);
  const { addAlert } = useContext(AlertContext);
  const { mutateAsync: saveAppOptions } = useSaveAppOptionsMutation("SLACK");
  const { mutateAsync: deleteAppOptions } = useDeleteAppOptionsMutation("SLACK");
  const form = useForm({
    defaultValues: { label: channel.SLACK_CHANNEL_LABEL },
    validators: { onChange: channelLabelSchema },
    onSubmit: async ({ value, formApi }) => {
      try {
        const { optionsId: _optionsId, ...params } = { ...channel, SLACK_CHANNEL_LABEL: value.label };
        const newState = await saveAppOptions({ optionsId: Optional.present(channel.optionsId), option: params });
        const newCreds = ArrayUtils.all(newState.credentials)
          .toResult(() => new Error(t("integrations.slack.errors.resultsUnavailable")))
          .elseThrow();
        const savedChannel = newCreds.find((c) => c.optionsId === channel.optionsId);
        if (!savedChannel) throw new Error(t("integrations.slack.errors.resultsUnavailable"));
        formApi.reset({ label: savedChannel.SLACK_CHANNEL_LABEL });
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
    },
  });

  return (
    <Card variant="outlined">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          event.stopPropagation();
          void form.handleSubmit();
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
            <form.Field name="label">
              {(field) => (
                <TextField
                  fullWidth
                  value={field.state.value}
                  onChange={({ target: { value } }) => {
                    field.handleChange(value);
                  }}
                  label={t("integrations.slack.fields.rspaceLabel")}
                />
              )}
            </form.Field>
          </Stack>
        </CardContent>
        <CardActions>
          <Button type="submit">{t("common:actions.save")}</Button>
          <Button
            onClick={() => {
              void (async () => {
                try {
                  await deleteAppOptions(channel.optionsId);
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
  );
}

function DialogContent({ linkedChannels }: { linkedChannels: ReadonlyArray<Channel> }) {
  const { t } = useTranslation(["apps", "common"]);
  const [loadingNewChannel, setLoadingNewChannel] = useState(false);
  const { addAlert } = useContext(AlertContext);
  const { oauthUrl } = useSlackEndpoint();
  /*
   * The server saves the channel when Slack redirects back, so the channels
   * are fetched again. Only the channels are replaced; an unreadable list is
   * reported rather than shown.
   */
  const { mutateAsync: refreshChannels } = useIntegrationMutation(
    "SLACK",
    async () => (await fetchAllIntegrations()).SLACK,
    (current, fresh) =>
      ArrayUtils.all(fresh.credentials).isPresent() ? { ...current, credentials: fresh.credentials } : current,
  );

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
    const knownChannels = new Set(linkedChannels.map(({ optionsId }) => optionsId));
    void (async () => {
      try {
        const SLACK = await refreshChannels(undefined);
        const channels = ArrayUtils.all(SLACK.credentials)
          .toResult(() => new Error(t("integrations.slack.errors.resultsUnavailable")))
          .elseThrow();
        if (channels.some((c) => !knownChannels.has(c.optionsId)))
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
        throw new Error(t("integrations.slack.errors.authWindowFailed"));
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
      {linkedChannels.map((channel) => (
        <Box key={channel.optionsId}>
          <ChannelForm channel={channel} />
        </Box>
      ))}
      <Box>
        <Button disabled={loadingNewChannel} onClick={() => void addHandler()}>
          {loadingNewChannel ? t("integrations.slack.loadingChannel") : t("common:actions.add")}
        </Button>
      </Box>
    </Stack>
  );
}

/*
 * Slack uses OAuth authentication, but the credential is stored on a
 * per-channel basis so the user has to reauthenticate whenever they wish
 * to add another channel. For this reason, the current implementation is a
 * little bit of hack and should be further refined once the old apps page has
 * been deprecated.
 */
function Slack(): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const integrationState = useIntegrationState("SLACK");
  const updateMode = useIntegrationModeUpdate("SLACK");
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
        update={updateMode}
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
              .map((linkedChannels) => <DialogContent key={null} linkedChannels={linkedChannels} />)
              .orElse(t("integrations.slack.orElse"))}
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(Slack);
