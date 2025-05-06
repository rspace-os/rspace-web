import Grid from "@mui/material/Grid";
import React, { useState, useContext } from "react";
import IntegrationCard from "../IntegrationCard";
import { observable, runInAction } from "mobx";
import { observer, useLocalObservable } from "mobx-react-lite";
import { Optional } from "../../../util/optional";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import { useSlackEndpoint } from "../useSlackEndpoint";
import Button from "@mui/material/Button";
import { doNotAwait } from "../../../util/Util";
import DescriptionList from "../../../components/DescriptionList";
import TextField from "@mui/material/TextField";
import {
  type IntegrationStates,
  useIntegrationsEndpoint,
} from "../useIntegrationsEndpoint";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import RsSet from "../../../util/set";
import SlackIcon from "../../../assets/branding/slack/logo.svg";
import Link from "@mui/material/Link";
import docLinks from "../../../assets/DocLinks";
import Typography from "@mui/material/Typography";
import * as ArrayUtils from "../../../util/ArrayUtils";
import { LOGO_COLOR } from "../../../assets/branding/slack";

type Channel = {
  SLACK_TEAM_NAME: string;
  SLACK_CHANNEL_ID: string;
  SLACK_CHANNEL_NAME: string;
  SLACK_USER_ID: string;
  SLACK_CHANNEL_LABEL: string;
  SLACK_USER_ACCESS_TOKEN: string;
  SLACK_TEAM_ID: string;
  SLACK_WEBHOOK_URL: string;
};

type UnwrapOptional<T> = T extends Optional<infer U> ? U : T;

type UnwrapArray<T extends Array<unknown>> = {
  [K in keyof T]: UnwrapOptional<T[K]>;
};

const DialogContent = observer(
  ({
    linkedChannels,
    integrationState,
  }: {
    linkedChannels: UnwrapArray<IntegrationStates["SLACK"]["credentials"]>;
    integrationState: IntegrationStates["SLACK"];
  }) => {
    const [loadingNewChannel, setLoadingNewChannel] = useState(false);
    const [newChannel, setNewChannel] = useState<Channel | null>(null);
    const { addAlert } = useContext(AlertContext);
    const { oauthUrl } = useSlackEndpoint();
    const { saveAppOptions, deleteAppOptions } = useIntegrationsEndpoint();
    const copyOfChannels = useLocalObservable(() =>
      linkedChannels.map((x) => observable(x))
    );

    const addHandler = async () => {
      setLoadingNewChannel(true);
      try {
        /*
         * So this is a bit of hack to ensure that the new apps page and the
         * old one can continue to work side by side. For the Slack
         * integration, we don't store the oauth token, but instead we store an
         * access token for each of the user's channels that they have
         * connected. As such, when the user goes to add another Slack
         * channel, we first need to check that they are authenticated and
         * then get the details of their chosen channel. To get these details,
         * we open a new window, which can be freely redirected by Slack
         * until they invoke our redirect URL. Once that window has reached
         * our redirect URL and rendered the
         * ../../../../WEB-INF/pages/connect/slack/connected.jsp, then we
         * reach in and grab the details. Once we remove the old apps
         * page, we can instead have the redirect URL expose this information
         * without the need for a JSP.
         */
        const authWindow = window.open(await oauthUrl());
        if (!authWindow) {
          throw new Error("Failed to open Slack authentication window");
        }

        let timer: NodeJS.Timeout;
        const channelDetailsJson: string = await new Promise(
          (resolve, reject) => {
            const f = () => {
              try {
                if (authWindow.document.URL.indexOf("redirect_uri") > 0) {
                  const slackResponseInput =
                    authWindow.document.getElementById("slackResponse");
                  if (!slackResponseInput) {
                    reject(new Error("Failed to retrieve access token"));
                    return;
                  }
                  // @ts-expect-error value will be on the input; don't cast to HTMLInputElement because test relies on a simple mock
                  const slackResponse = slackResponseInput.value;
                  authWindow.close();
                  clearInterval(timer);
                  resolve(slackResponse);
                }
              } catch {
                // do nothing, as we will retry 1s later
              }
            };
            timer = setInterval(f, 1000);
          }
        );

        const channelDetails: unknown = JSON.parse(channelDetailsJson);
        if (typeof channelDetails !== "object" || channelDetails === null)
          throw new Error(
            "Could not decode channel details. Invalid root object"
          );
        const channelDetailsRecord = channelDetails as Record<string, unknown>;
        if (
          typeof channelDetailsRecord.incoming_webhook !== "object" ||
          channelDetailsRecord.incoming_webhook === null
        )
          throw new Error(
            "Could not decode channel details. Invalid incoming_webhook"
          );
        const incomingWebhookRecord =
          channelDetailsRecord.incoming_webhook as Record<string, unknown>;
        if (typeof channelDetailsRecord.team_name !== "string")
          throw new Error(
            "Could not decode channel details. Invalid team name"
          );
        const SLACK_TEAM_NAME = channelDetailsRecord.team_name;
        if (typeof incomingWebhookRecord.channel_id !== "string")
          throw new Error(
            "Could not decode channel details. Invalid channel id"
          );
        const SLACK_CHANNEL_ID = incomingWebhookRecord.channel_id;
        if (typeof incomingWebhookRecord.channel !== "string")
          throw new Error(
            "Could not decode channel details. Invalid channel name"
          );
        const SLACK_CHANNEL_NAME = incomingWebhookRecord.channel;
        const SLACK_CHANNEL_LABEL = SLACK_CHANNEL_NAME;
        if (typeof channelDetailsRecord.user_id !== "string")
          throw new Error("Could not decode channel details. Invalid user id");
        const SLACK_USER_ID = channelDetailsRecord.user_id;
        if (typeof channelDetailsRecord.access_token !== "string")
          throw new Error(
            "Could not decode channel details. Invalid access token"
          );
        const SLACK_USER_ACCESS_TOKEN = channelDetailsRecord.access_token;
        if (typeof channelDetailsRecord.team_id !== "string")
          throw new Error("Could not decode channel details. Invalid user id");
        const SLACK_TEAM_ID = channelDetailsRecord.team_id;
        if (typeof incomingWebhookRecord.url !== "string")
          throw new Error("Could not decode channel details. Invalid url");
        const SLACK_WEBHOOK_URL = incomingWebhookRecord.url;
        setNewChannel(
          observable({
            SLACK_TEAM_NAME,
            SLACK_CHANNEL_ID,
            SLACK_CHANNEL_NAME,
            SLACK_USER_ID,
            SLACK_CHANNEL_LABEL,
            SLACK_USER_ACCESS_TOKEN,
            SLACK_TEAM_ID,
            SLACK_WEBHOOK_URL,
          })
        );
      } catch (e) {
        if (e instanceof Error)
          addAlert(
            mkAlert({
              variant: "error",
              title: "Could not get details of new Slack channel.",
              message: e.message,
            })
          );
      } finally {
        setLoadingNewChannel(false);
      }
    };
    return (
      <Grid container direction="column" spacing={1} sx={{ mt: 1 }}>
        {copyOfChannels.map((channel, index) => (
          <Grid item key={channel.optionsId}>
            <Card variant="outlined">
              <form
                onSubmit={doNotAwait(async (event) => {
                  event.preventDefault();
                  try {
                    const params: { optionsId: unknown } = {
                      ...channel,
                    };
                    delete params.optionsId;
                    const newState = await saveAppOptions(
                      "SLACK",
                      Optional.present(channel.optionsId),
                      params
                    );
                    runInAction(() => {
                      integrationState.credentials = newState.credentials;
                      const newCreds = ArrayUtils.all(newState.credentials)
                        .toResult(
                          () =>
                            new Error("Save completed but cannot show results")
                        )
                        .elseThrow();
                      const indexOfNewConfig = newCreds.findIndex(
                        (c) => c.optionsId === channel.optionsId
                      );
                      if (indexOfNewConfig === -1)
                        throw new Error(
                          "Save completed but cannot show results."
                        );

                      copyOfChannels.splice(
                        index,
                        1,
                        observable(newCreds[indexOfNewConfig])
                      );
                    });
                    addAlert(
                      mkAlert({
                        variant: "success",
                        message: "Successfully changed label.",
                      })
                    );
                  } catch (e) {
                    if (e instanceof Error)
                      addAlert(
                        mkAlert({
                          variant: "error",
                          title: "Failed to change label.",
                          message: e.message,
                        })
                      );
                  }
                })}
              >
                <CardContent>
                  <Grid container spacing={2} direction="column">
                    <Grid item>
                      <DescriptionList
                        content={[
                          {
                            label: "Workspace",
                            value: channel.SLACK_TEAM_NAME,
                          },
                          {
                            label: "Channel name",
                            value: channel.SLACK_CHANNEL_NAME,
                          },
                        ]}
                      />
                    </Grid>
                    <Grid item>
                      <TextField
                        fullWidth
                        value={channel.SLACK_CHANNEL_LABEL}
                        onChange={({ target: { value } }) => {
                          runInAction(() => {
                            channel.SLACK_CHANNEL_LABEL = value;
                          });
                        }}
                        label="RSpace Label"
                      />
                    </Grid>
                  </Grid>
                </CardContent>
                <CardActions>
                  <Button type="submit">Save</Button>
                  <Button
                    onClick={doNotAwait(async () => {
                      try {
                        await deleteAppOptions("SLACK", channel.optionsId);
                        runInAction(() => {
                          const deletedIndex = copyOfChannels.findIndex(
                            (c) => c === channel
                          );
                          copyOfChannels.splice(deletedIndex, 1);
                          integrationState.credentials.splice(deletedIndex, 1);
                        });
                        addAlert(
                          mkAlert({
                            variant: "success",
                            message: "Successfully deleted channel.",
                          })
                        );
                      } catch (e) {
                        if (e instanceof Error)
                          addAlert(
                            mkAlert({
                              variant: "error",
                              title: "Could not delete channel.",
                              message: e.message,
                            })
                          );
                      }
                    })}
                  >
                    Remove
                  </Button>
                </CardActions>
              </form>
            </Card>
          </Grid>
        ))}
        <Grid item>
          {newChannel ? (
            <Card variant="outlined">
              <form
                onSubmit={doNotAwait(async (event) => {
                  event.preventDefault();
                  try {
                    const newState = await saveAppOptions(
                      "SLACK",
                      Optional.empty(),
                      newChannel
                    );
                    const optionIdsOfExistingRepos = new RsSet(
                      copyOfChannels.map(({ optionsId }) => optionsId)
                    );
                    runInAction(() => {
                      integrationState.credentials = newState.credentials;
                      const newlySavedRepo = new RsSet(newState.credentials)
                        .mapOptional((x) => x)
                        .subtractMap(
                          ({ optionsId }) => optionsId,
                          optionIdsOfExistingRepos
                        ).first;
                      copyOfChannels.push(newlySavedRepo);
                    });
                    setNewChannel(null);
                    addAlert(
                      mkAlert({
                        variant: "success",
                        message: "Successfully added channel.",
                      })
                    );
                  } catch (e) {
                    if (e instanceof Error)
                      addAlert(
                        mkAlert({
                          variant: "error",
                          title: "Failed to add channel.",
                          message: e.message,
                        })
                      );
                  }
                })}
              >
                <CardContent>
                  <DescriptionList
                    content={[
                      {
                        label: "Workspace",
                        value: newChannel.SLACK_TEAM_NAME,
                      },
                      {
                        label: "Channel name",
                        value: newChannel.SLACK_CHANNEL_NAME,
                      },
                    ]}
                  />
                  <TextField
                    fullWidth
                    value={newChannel.SLACK_CHANNEL_LABEL}
                    onChange={({ target: { value } }) => {
                      runInAction(() => {
                        newChannel.SLACK_CHANNEL_LABEL = value;
                      });
                    }}
                    label="RSpace Label"
                  />
                </CardContent>
                <CardActions>
                  <Button
                    onClick={() => {
                      setNewChannel(null);
                    }}
                  >
                    Cancel
                  </Button>
                  <Button type="submit">Save</Button>
                </CardActions>
              </form>
            </Card>
          ) : (
            <Button
              disabled={loadingNewChannel}
              onClick={doNotAwait(addHandler)}
            >
              {loadingNewChannel ? "Loading a new channel" : "Add"}
            </Button>
          )}
        </Grid>
      </Grid>
    );
  }
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
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Slack"
        integrationState={integrationState}
        explanatoryText="Message and collaborate with your team with a cloud-based communication tool."
        image={SlackIcon}
        color={LOGO_COLOR}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        usageText="You can send messages or forward notifications to your chosen Slack channels. You can also post links to RSpace documents directly into Slack channels or private messages."
        helpLinkText="Slack integration docs"
        website="slack.com"
        docLink="slack"
        setupSection={
          <>
            <Typography variant="body2">
              The steps to setting up this integration are documented in{" "}
              <Link href={docLinks.slack} target="_blank" rel="noreferrer">
                the Slack Integration article.
              </Link>
            </Typography>
            {ArrayUtils.all(integrationState.credentials)
              .map((linkedChannels) => (
                <DialogContent
                  key={null}
                  linkedChannels={linkedChannels}
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

export default React.memo(Slack);
