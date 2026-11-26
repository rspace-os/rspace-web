import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { useForm } from "@tanstack/react-form";
import React, { useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/msteams";
import TeamsIcon from "../../../assets/branding/msteams/logo.svg";
import SecretField from "../../../components/Inputs/SecretField";
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

const newChannelSchema = v.object({
  channelName: v.string(),
  webhookUrl: v.string(),
});

/*
 * Microsoft's Teams integration uses a Webhook URL based approach, where the
 * user provides us with a URL to which we can call to send messages to their
 * Teams channel.
 */
function MSTeams(): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const integrationState = useIntegrationState("MSTEAMS");
  const updateMode = useIntegrationModeUpdate("MSTEAMS");
  const { mutateAsync: deleteAppOptions } = useDeleteAppOptionsMutation("MSTEAMS");
  const { mutateAsync: saveAppOptions } = useSaveAppOptionsMutation("MSTEAMS");
  const { addAlert } = useContext(AlertContext);
  const [addingChannel, setAddingChannel] = useState(false);
  const newChannelForm = useForm({
    defaultValues: { channelName: "", webhookUrl: "" },
    validators: { onChange: newChannelSchema },
    onSubmit: async ({ value, formApi }) => {
      try {
        await saveAppOptions({
          optionsId: Optional.empty(),
          option: {
            MSTEAMS_CHANNEL_LABEL: value.channelName,
            MSTEAMS_WEBHOOK_URL: value.webhookUrl,
          },
        });
        setAddingChannel(false);
        formApi.reset();
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
    },
  });

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
        update={updateMode}
        usageText={t("integrations.msteams.usage")}
        helpLinkText={t("integrations.msteams.helpLink")}
        website="https://teams.microsoft.com"
        docLink="teams"
        setupSection={
          <>
            <Typography variant="body2">
              <TransRichText i18nKey="apps:integrations.msteams.setup.instructions" />
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
                                      await deleteAppOptions(channel.optionsId);
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
                    {addingChannel ? (
                      <Card variant="outlined">
                        <form
                          onSubmit={(event) => {
                            event.preventDefault();
                            event.stopPropagation();
                            void newChannelForm.handleSubmit();
                          }}
                        >
                          <CardContent>
                            <newChannelForm.Field name="channelName">
                              {(field) => (
                                <TextField
                                  label={t("integrations.msteams.fields.channelName")}
                                  fullWidth
                                  value={field.state.value}
                                  onChange={({ target: { value } }) => {
                                    field.handleChange(value);
                                  }}
                                  sx={{ mt: 1 }}
                                />
                              )}
                            </newChannelForm.Field>
                            <newChannelForm.Field name="webhookUrl">
                              {(field) => (
                                <SecretField
                                  label={t("integrations.msteams.fields.webhookUrl")}
                                  fullWidth
                                  value={field.state.value}
                                  onChange={field.handleChange}
                                  autoComplete="new-password"
                                  sx={{ mt: 1 }}
                                />
                              )}
                            </newChannelForm.Field>
                          </CardContent>
                          <CardActions>
                            <Button type="submit">{t("common:actions.save")}</Button>
                          </CardActions>
                        </form>
                      </Card>
                    ) : (
                      <Button
                        onClick={() => {
                          newChannelForm.reset();
                          setAddingChannel(true);
                        }}
                      >
                        {t("common:actions.add")}
                      </Button>
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

export default React.memo(MSTeams);
