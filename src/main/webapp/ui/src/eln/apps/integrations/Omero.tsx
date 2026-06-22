import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import React, { useContext, useState } from "react";
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
  const { addAlert } = useContext(AlertContext);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  useBroadcastChannel<OmeroConnectedMessage>(OMERO_CONNECTION_CHANNEL, (e: MessageEvent<OmeroConnectedMessage>) => {
    if (e.data?.type !== "OMERO_CONNECTED") return;
    if (e.data.error) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Could not connect to OMERO",
          message: e.data.error,
        }),
      );
      return;
    }
    addAlert(
      mkAlert({
        variant: "success",
        message: "Successfully connected to OMERO.",
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
        name="OMERO"
        integrationState={integrationState}
        explanatoryText="View, manage, and share your microscopy image data with an extensible central repository."
        image={OmeroIcon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        usageText="You can import OMERO image thumbnails and metadata into RSpace documents."
        helpLinkText="Omero integration docs"
        website="openmicroscopy.org/omero"
        docLink="omero"
        setupSection={
          <>
            <ol>
              <li>Provide your OMERO credentials and click on Connect.</li>
              <li>Enable the integration.</li>
              <li>
                When editing a document, click on the OMERO icon in the text editor toolbar to access and insert image
                data.
              </li>
            </ol>
            <form
              action="/apps/omero/connect"
              method="POST"
              onSubmit={(event) => {
                const popupName = `rspace-omero-connect-${Date.now()}`;
                window.open("about:blank", popupName, "noopener,noreferrer");
                event.currentTarget.target = popupName;
              }}
            >
              <Stack spacing={1}>
                <TextField
                  fullWidth
                  value={username}
                  onChange={({ target: { value } }) => setUsername(value)}
                  label="Username"
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
                  label="Password"
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
                  Connect
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
