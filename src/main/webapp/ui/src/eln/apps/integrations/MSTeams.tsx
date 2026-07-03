import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";
import Link from "@mui/material/Link";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { runInAction } from "mobx";
import { observer } from "mobx-react-lite";
import React, { useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/msteams";
import TeamsIcon from "../../../assets/branding/msteams/logo.svg";
import docLinks from "../../../assets/DocLinks";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as ArrayUtils from "../../../util/ArrayUtils";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates, useIntegrationsEndpoint } from "../useIntegrationsEndpoint";

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
  const { t } = useTranslation(["apps", "common"]);
  const { deleteAppOptions, saveAppOptions } = useIntegrationsEndpoint();
  const { addAlert } = useContext(AlertContext);
  const [newChannelName, setNewChannelName] = useState<string | null>(null);
  const [newWebhook, setNewWebhook] = useState<string | null>(null);

  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.msteams.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.msteams.description")}
        image={TeamsIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: integrationState.credentials })}
        usageText={t("integrations.msteams.usage")}
        helpLinkText={t("integrations.msteams.helpLink")}
        website="teams.microsoft.com"
        docLink="teams"
        setupSection={
          <>
            <Typography variant="body2">
              <TransRichText
                i18nKey="apps:integrations.msteams.setup.instructions"
                components={{
                  articleLink: <Link href={docLinks.teams} target="_blank" rel="noreferrer" />,
                }}
              />
            </Typography>
            {ArrayUtils.all(integrationState.credentials)
              .map((channels) => (
                <Stack spacing={1} key={null} sx={{ mt: 1 }}>
                  <Box>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell colSpan={2}>{t("integrations.msteams.tableHeader")}</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {channels.map((channel) => (
                          <TableRow key={channel.optionsId}>
                            <TableCell>{channel.MSTEAMS_CHANNEL_LABEL}</TableCell>
                            <TableCell>
                              <Button
                                onClick={() => {
                                  void (async () => {
                                    try {
                                      await deleteAppOptions("MSTEAMS", channel.optionsId);
                                      const indexOfDeleted = channels.findIndex(
                                        (c) => c.optionsId === channel.optionsId,
                                      );
                                      runInAction(() => {
                                        integrationState.credentials.splice(indexOfDeleted, 1);
                                      });
                                      addAlert(
                                        mkAlert({
                                          variant: "success",
                                          message: t("integrations.msteams.alerts.removeSuccess"),
                                        }),
                                      );
                                    } catch (e) {
                                      if (e instanceof Error) {
                                        addAlert(
                                          mkAlert({
                                            variant: "error",
                                            title: t("integrations.msteams.alerts.removeError"),
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
                  <Box>
                    {newChannelName === null ? (
                      <Button
                        onClick={() => {
                          setNewChannelName("");
                          setNewWebhook("");
                        }}
                      >
                        {t("common:actions.add")}
                      </Button>
                    ) : (
                      <Card variant="outlined">
                        <form
                          onSubmit={(event) => {
                            void (async () => {
                              event.preventDefault();
                              try {
                                const newState = await saveAppOptions("MSTEAMS", Optional.empty(), {
                                  MSTEAMS_CHANNEL_LABEL: newChannelName,
                                  MSTEAMS_WEBHOOK_URL: newWebhook,
                                });
                                runInAction(() => {
                                  integrationState.credentials = newState.credentials;
                                });
                                setNewChannelName(null);
                                setNewWebhook(null);
                                addAlert(
                                  mkAlert({
                                    variant: "success",
                                    message: t("integrations.msteams.alerts.addSuccess"),
                                  }),
                                );
                              } catch (e) {
                                if (e instanceof Error) {
                                  addAlert(
                                    mkAlert({
                                      variant: "error",
                                      title: t("integrations.msteams.alerts.addError"),
                                      message: e.message,
                                    }),
                                  );
                                }
                              }
                            })();
                          }}
                        >
                          <CardContent>
                            <TextField
                              label={t("integrations.msteams.fields.channelName")}
                              fullWidth
                              value={newChannelName}
                              onChange={({ target: { value } }) => {
                                setNewChannelName(value);
                              }}
                              sx={{ mt: 1 }}
                            />
                            <TextField
                              label={t("integrations.msteams.fields.webhookUrl")}
                              fullWidth
                              value={newWebhook}
                              onChange={({ target: { value } }) => {
                                setNewWebhook(value);
                              }}
                              sx={{ mt: 1 }}
                            />
                          </CardContent>
                          <CardActions>
                            <Button type="submit">{t("common:actions.save")}</Button>
                          </CardActions>
                        </form>
                      </Card>
                    )}
                  </Box>
                </Stack>
              ))
              .orElse(t("integrations.msteams.orElse"))}
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(observer(MSTeams));
