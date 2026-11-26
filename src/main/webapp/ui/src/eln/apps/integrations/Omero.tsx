import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import { useForm } from "@tanstack/react-form";
import React, { useContext } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/omero";
import OmeroIcon from "../../../assets/branding/omero/logo.svg";
import SecretField from "../../../components/Inputs/SecretField";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import IntegrationCard from "../IntegrationCard";
import { useIntegrationModeUpdate, useIntegrationState, useTokenConnection } from "../queries";
import { useDisconnectEndpoint } from "../useDisconnect";

/*
 * The credentials are posted by the browser to /apps/omero/connect in a new
 * window, so the form only holds them until then.
 */
const omeroCredentialsSchema = v.object({
  username: v.string(),
  password: v.string(),
});

export interface OmeroConnectedMessage extends Record<string, unknown> {
  type: "OMERO_CONNECTED";
  error?: string;
}
export const OMERO_CONNECTION_CHANNEL = "rspace.apps.omero.connection";

/*
 * Omero passes a username and password in a regular form submission. The
 * backend stores them in the UserConnection table, so the connection outlives
 * the session and this card shows a Disconnect button once connected.
 */
function Omero(): React.ReactNode {
  const { t } = useTranslation("apps");
  const { addAlert } = useContext(AlertContext);
  const integrationState = useIntegrationState("OMERO");
  const updateMode = useIntegrationModeUpdate("OMERO");
  const { disconnect } = useDisconnectEndpoint("/apps/omero", t("integrations.omero.name"));
  const { connected, setConnected } = useTokenConnection("OMERO");
  const form = useForm({
    defaultValues: { username: "", password: "" },
    validators: { onChange: omeroCredentialsSchema },
  });

  useBroadcastChannel<OmeroConnectedMessage>(OMERO_CONNECTION_CHANNEL, (e: MessageEvent<OmeroConnectedMessage>) => {
    if (e.data?.type !== "OMERO_CONNECTED") return;
    if (e.data.error) {
      addAlert(
        mkAlert({
          variant: "error",
          title: t("integrations.omero.alerts.connectError"),
          message: e.data.error,
        }),
      );
      return;
    }
    setConnected(true);
    // do not leave the password in the form, ready to be resubmitted
    form.reset();
    addAlert(
      mkAlert({
        variant: "success",
        message: t("integrations.omero.alerts.connectSuccess"),
      }),
    );
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
        name={t("integrations.omero.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.omero.description")}
        image={OmeroIcon}
        color={LOGO_COLOR}
        update={updateMode}
        usageText={t("integrations.omero.usage")}
        helpLinkText={t("integrations.omero.helpLink")}
        website="https://openmicroscopy.org/omero"
        docLink="omero"
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.omero.setup.instructions" />
            {connected ? (
              <Button
                sx={{ mt: 1 }}
                onClick={() => {
                  void (async () => {
                    // only report a disconnection that actually happened
                    if (await disconnect()) setConnected(false);
                  })();
                }}
              >
                {t("actions.disconnect")}
              </Button>
            ) : (
              <form
                aria-label={t("integrations.omero.credentialsFormLabel")}
                action="/apps/omero/connect"
                method="POST"
                target="_blank"
                rel="noopener"
              >
                <Stack spacing={1}>
                  <form.Field name="username">
                    {(field) => (
                      <TextField
                        fullWidth
                        value={field.state.value}
                        onChange={({ target: { value } }) => field.handleChange(value)}
                        label={t("integrations.omero.fields.username")}
                        sx={{ mt: 1 }}
                        slotProps={{
                          htmlInput: {
                            name: "omerousername",
                            autoComplete: "off",
                          },
                        }}
                      />
                    )}
                  </form.Field>
                  <form.Field name="password">
                    {(field) => (
                      <SecretField
                        fullWidth
                        value={field.state.value}
                        onChange={field.handleChange}
                        label={t("integrations.omero.fields.password")}
                        sx={{ mt: 1 }}
                        name="omeropassword"
                        autoComplete="new-password"
                      />
                    )}
                  </form.Field>
                  <Button type="submit" sx={{ mt: 1 }}>
                    {t("actions.connect")}
                  </Button>
                </Stack>
              </form>
            )}
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(Omero);
