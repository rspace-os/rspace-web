import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import React, { useState } from "react";
import { LOGO_COLOR } from "../../../assets/branding/omero";
import OmeroIcon from "../../../assets/branding/omero/logo.svg";
import IntegrationCard from "../IntegrationCard";
// biome-ignore lint/style/useImportType: initial biome migration
import { type IntegrationStates } from "../useIntegrationsEndpoint";

type OmeroArgs = {
  integrationState: IntegrationStates["OMERO"];
  update: (newIntegrationState: IntegrationStates["OMERO"]) => void;
};

/*
 * Omero passes a username and password in a regular form submission.
 */
function Omero({ integrationState, update }: OmeroArgs): React.ReactNode {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
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
            <form action="/apps/omero/connect" method="POST">
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
