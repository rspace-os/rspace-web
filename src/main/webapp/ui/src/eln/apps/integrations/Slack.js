//@flow strict

import Grid from "@mui/material/Grid";
import React, {
  type Node,
  useState,
  useContext,
  type AbstractComponent,
} from "react";
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
import SlackIcon from "../icons/slack.svg";
import Link from "@mui/material/Link";
import docLinks from "../../../assets/DocLinks";
import Typography from "@mui/material/Typography";
import * as ArrayUtils from "../../../util/ArrayUtils";

type Channel = {|
  SLACK_TEAM_NAME: string,
  SLACK_CHANNEL_ID: string,
  SLACK_CHANNEL_NAME: string,
  SLACK_USER_ID: string,
  SLACK_CHANNEL_LABEL: string,
  SLACK_USER_ACCESS_TOKEN: string,
  SLACK_TEAM_ID: string,
  SLACK_WEBHOOK_URL: string,
|};

const DialogContent = observer(
  ({
    linkedChannels,
    integrationState,
  }: {|
    linkedChannels: $TupleMap<
      IntegrationStates["SLACK"]["credentials"],
      <A>(Optional<A>) => A
    >,
    integrationState: IntegrationStates["SLACK"],
  |}) => {
    const [loadingNewChannel, setLoadingNewChannel] = useState(false);
    const [newChannel, setNewChannel] = useState<?Channel>(null);
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

        let timer;
        const channelDetailsJson: string = await new Promise((resolve) => {
          const f = () => {
            try {
              if (authWindow.document.URL.indexOf("redirect_uri") > 0) {
                const slackResponse =
                  authWindow.document.getElementById("slackResponse").value;
                authWindow.close();
                clearInterval(timer);
                resolve(slackResponse);
              }
            } catch {
              // do nothing, as we will retry 1s later
            }
          };
          timer = setInterval(f, 1000);
        });

        const channelDetails = JSON.parse(channelDetailsJson);
        if (typeof channelDetails !== "object" || channelDetails === null)
          throw new Error(
            "Could not decode channel details. Invalid root object"
          );
        if (
          typeof channelDetails.incoming_webhook !== "object" ||
          channelDetails.incoming_webhook === null
        )
          throw new Error(
            "Could not decode channel details. Invalid incoming_webhook"
          );
        if (typeof channelDetails.team_name !== "string")
          throw new Error(
            "Could not decode channel details. Invalid team name"
          );
        const SLACK_TEAM_NAME = channelDetails.team_name;
        if (typeof channelDetails.incoming_webhook.channel_id !== "string")
          throw new Error(
            "Could not decode channel details. Invalid channel id"
          );
        const SLACK_CHANNEL_ID = channelDetails.incoming_webhook.channel_id;
        if (typeof channelDetails.incoming_webhook.channel !== "string")
          throw new Error(
            "Could not decode channel details. Invalid channel name"
          );
        const SLACK_CHANNEL_NAME = channelDetails.incoming_webhook.channel;
        const SLACK_CHANNEL_LABEL = SLACK_CHANNEL_NAME;
        if (typeof channelDetails.user_id !== "string")
          throw new Error("Could not decode channel details. Invalid user id");
        const SLACK_USER_ID = channelDetails.user_id;
        if (typeof channelDetails.access_token !== "string")
          throw new Error(
            "Could not decode channel details. Invalid access token"
          );
        const SLACK_USER_ACCESS_TOKEN = channelDetails.access_token;
        if (typeof channelDetails.team_id !== "string")
          throw new Error("Could not decode channel details. Invalid user id");
        const SLACK_TEAM_ID = channelDetails.team_id;
        if (typeof channelDetails.incoming_webhook.url !== "string")
          throw new Error("Could not decode channel details. Invalid url");
        const SLACK_WEBHOOK_URL = channelDetails.incoming_webhook.url;
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
                    const params: { optionsId: mixed, ... } = {
                      ...channel,
                    };
                    delete params.optionsId;
                    const newState = await saveAppOptions(
                      "SLACK",
                      Optional.present(channel.optionsId),
                      (params: { ... })
                    );
                    runInAction(() => {
                      integrationState.credentials = newState.credentials;
                      ArrayUtils.all(newState.credentials)
                        .map((newCreds) => {
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
                        })
                        .orElseGet(() => {
                          throw new Error(
                            "Save completed but cannot show results."
                          );
                        });
                    });
                    addAlert(
                      mkAlert({
                        variant: "success",
                        message: "Successfully changed label.",
                      })
                    );
                  } catch (e) {
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

type SlackArgs = {|
  integrationState: IntegrationStates["SLACK"],
  update: (IntegrationStates["SLACK"]) => void,
|};

/*
 * Slack uses OAuth authentication, but the credential is stored on a
 * per-channel basis so the user has to reauthenticate whenever they wish
 * to add another channel. For this reason, the current implementation is a
 * little bit of hack and should be further refined once the old apps page has
 * been deprecated.
 */
function Slack({ integrationState, update }: SlackArgs): Node {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Slack"
        integrationState={integrationState}
        explanatoryText="Message and collaborate with your team with a cloud-based communication tool."
        image={SlackIcon}
        color={{
          hue: 298,
          saturation: 56,
          lightness: 19,
        }}
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

export default (React.memo(Slack): AbstractComponent<SlackArgs>);
