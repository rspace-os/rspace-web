import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import React, { useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import { useBroadcastChannel } from "@/modules/common/hooks/broadcast";
import { LOGO_COLOR } from "../../../assets/branding/omero";
import OmeroIcon from "../../../assets/branding/omero/logo.svg";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import IntegrationCard from "../IntegrationCard";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type OmeroArgs = {
  integrationState: IntegrationStates["OMERO"];
  update: (newIntegrationState: IntegrationStates["OMERO"]) => void;
};

export interface OmeroConnectedMessage extends Record<string, unknown> {
  type: "OMERO_CONNECTED";
  error?: string;
}
export const OMERO_CONNECTION_CHANNEL = "rspace.apps.omero.connection";

/*
 * Omero passes a username and password in a regular form submission.
 */
function Omero({ integrationState, update }: OmeroArgs): React.ReactNode {
  const { t } = useTranslation("apps");
  const { addAlert } = useContext(AlertContext);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

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
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        usageText={t("integrations.omero.usage")}
        helpLinkText={t("integrations.omero.helpLink")}
        website="openmicroscopy.org/omero"
        docLink="omero"
        setupSection={
          <>
            <ol>
              <li>{t("integrations.omero.setup.credentials")}</li>
              <li>{t("integrations.omero.setup.enable")}</li>
              <li>{t("integrations.omero.setup.toolbar")}</li>
            </ol>
            <form
              aria-label={t("integrations.omero.credentialsFormLabel")}
              action="/apps/omero/connect"
              method="POST"
              target="_blank"
              rel="noopener"
            >
              <Stack spacing={1}>
                <TextField
                  fullWidth
                  value={username}
                  onChange={({ target: { value } }) => setUsername(value)}
                  label={t("integrations.omero.fields.username")}
                  sx={{ mt: 1 }}
                  slotProps={{
                    htmlInput: {
                      name: "omerousername",
                      autoComplete: "username",
                    },
                  }}
                />
                <TextField
                  fullWidth
                  value={password}
                  onChange={({ target: { value } }) => setPassword(value)}
                  label={t("integrations.omero.fields.password")}
                  sx={{ mt: 1 }}
                  slotProps={{
                    htmlInput: {
                      name: "omeropassword",
                      type: "password",
                      autoComplete: "new-password",
                    },
                  }}
                />
                <Button type="submit" value={"Connect"} sx={{ mt: 1 }}>
                  {t("actions.connect")}
                </Button>
              </Stack>
            </form>
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(Omero);
