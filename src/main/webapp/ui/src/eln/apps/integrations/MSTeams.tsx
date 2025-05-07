//@flow strict

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
import { doNotAwait } from "../../../util/Util";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import { runInAction } from "mobx";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import TextField from "@mui/material/TextField";
import { Optional } from "../../../util/optional";
import TeamsIcon from "../../../assets/branding/msteams/logo.svg";
import Link from "@mui/material/Link";
import docLinks from "../../../assets/DocLinks";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import * as ArrayUtils from "../../../util/ArrayUtils";
import { LOGO_COLOR } from "../../../assets/branding/msteams";

type MSTeamsArgs = {
  integrationState: IntegrationStates["MSTEAMS"];
  update: (newIntegrationState: IntegrationStates["MSTEAMS"]) => void;
};

/*
 * Microsoft's Teams integration uses a Webhook URL based approach, where the
 * user provides us with a URL to which we can call to send messages to their
 * Teams channel.
 */
function MSTeams({ integrationState, update }: MSTeamsArgs): React.ReactNode {
  const { deleteAppOptions, saveAppOptions } = useIntegrationsEndpoint();
  const { addAlert } = useContext(AlertContext);
  const [newChannelName, setNewChannelName] = useState<string | null>(null);
  const [newWebhook, setNewWebhook] = useState<string | null>(null);

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Teams"
        integrationState={integrationState}
        explanatoryText="Message and video call your colleagues through a workspace platform with file storage."
        image={TeamsIcon}
        color={LOGO_COLOR}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        usageText="You can post messages about a specific RSpace document to a Teams channel directly from RSpace."
        helpLinkText="Microsoft Teams integration docs"
        website="teams.microsoft.com"
        docLink="teams"
        setupSection={
          <>
            <Typography variant="body2">
              The steps to setting up this integration are documented in{" "}
              <Link href={docLinks.teams} target="_blank" rel="noreferrer">
                the Microsoft Teams Integration article.
              </Link>
            </Typography>
            {ArrayUtils.all(integrationState.credentials)
              .map((channels) => (
                <Grid
                  container
                  direction="column"
                  spacing={1}
                  key={null}
                  sx={{ mt: 1 }}
                >
                  <Grid item>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell colSpan={2}>
                            Channel Connector Name
                          </TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {channels.map((channel) => (
                          <TableRow key={channel.optionsId}>
                            <TableCell>
                              {channel.MSTEAMS_CHANNEL_LABEL}
                            </TableCell>
                            <TableCell>
                              <Button
                                onClick={doNotAwait(async () => {
                                  try {
                                    await deleteAppOptions(
                                      "MSTEAMS",
                                      channel.optionsId
                                    );
                                    const indexOfDeleted = channels.findIndex(
                                      (c) => c.optionsId === channel.optionsId
                                    );
                                    runInAction(() => {
                                      integrationState.credentials.splice(
                                        indexOfDeleted,
                                        1
                                      );
                                    });
                                    addAlert(
                                      mkAlert({
                                        variant: "success",
                                        message:
                                          "Successfully removed channel.",
                                      })
                                    );
                                  } catch (e) {
                                    if (e instanceof Error) {
                                      addAlert(
                                        mkAlert({
                                          variant: "error",
                                          title: "Failed to remove channel.",
                                          message: e.message,
                                        })
                                      );
                                    }
                                  }
                                })}
                              >
                                Remove
                              </Button>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </Grid>
                  <Grid item>
                    {newChannelName === null ? (
                      <Button
                        onClick={() => {
                          setNewChannelName("");
                          setNewWebhook("");
                        }}
                      >
                        Add
                      </Button>
                    ) : (
                      <Card variant="outlined">
                        <form
                          onSubmit={doNotAwait(async (event) => {
                            event.preventDefault();
                            try {
                              const newState = await saveAppOptions(
                                "MSTEAMS",
                                Optional.empty(),
                                {
                                  MSTEAMS_CHANNEL_LABEL: newChannelName,
                                  MSTEAMS_WEBHOOK_URL: newWebhook,
                                }
                              );
                              runInAction(() => {
                                integrationState.credentials =
                                  newState.credentials;
                              });
                              setNewChannelName(null);
                              setNewWebhook(null);
                              addAlert(
                                mkAlert({
                                  variant: "success",
                                  message: "Successfully added channel.",
                                })
                              );
                            } catch (e) {
                              if (e instanceof Error) {
                                addAlert(
                                  mkAlert({
                                    variant: "error",
                                    title: "Failed to add channel.",
                                    message: e.message,
                                  })
                                );
                              }
                            }
                          })}
                        >
                          <CardContent>
                            <TextField
                              label="Channel Connector Name"
                              fullWidth
                              value={newChannelName}
                              onChange={({ target: { value } }) => {
                                setNewChannelName(value);
                              }}
                              sx={{ mt: 1 }}
                            />
                            <TextField
                              label="Webhook URL"
                              fullWidth
                              value={newWebhook}
                              onChange={({ target: { value } }) => {
                                setNewWebhook(value);
                              }}
                              sx={{ mt: 1 }}
                            />
                          </CardContent>
                          <CardActions>
                            <Button type="submit">Save</Button>
                          </CardActions>
                        </form>
                      </Card>
                    )}
                  </Grid>
                </Grid>
              ))
              .orElse("Error getting configured channels.")}
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(observer(MSTeams));
