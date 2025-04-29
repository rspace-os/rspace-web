import Grid from "@mui/material/Grid";
import React, { useState } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import OmeroIcon from "../../../assets/branding/omero/logo.svg";
import TextField from "@mui/material/TextField";
import Button from "@mui/material/Button";
import { LOGO_COLOR } from "../../../assets/branding/omero";

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
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
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
                When editing a document, click on the OMERO icon in the text
                editor toolbar to access and insert image data.
              </li>
            </ol>
            <form action="/apps/omero/connect" method="POST">
              <Grid container direction="column" spacing={1}>
                <Grid item>
                  <TextField
                    inputProps={{
                      name: "omerousername",
                      autoComplete: "username",
                    }}
                    fullWidth
                    value={username}
                    onChange={({ target: { value } }) => setUsername(value)}
                    label="Username"
                    sx={{ mt: 1 }}
                  />
                </Grid>
                <Grid item>
                  <TextField
                    inputProps={{
                      name: "omeropassword",
                      type: "password",
                      autoComplete: "new-password",
                    }}
                    fullWidth
                    value={password}
                    onChange={({ target: { value } }) => setPassword(value)}
                    label="Password"
                    sx={{ mt: 1 }}
                  />
                </Grid>
                <Grid item>
                  <Button type="submit" value={"Connect"} sx={{ mt: 1 }}>
                    Connect
                  </Button>
                </Grid>
              </Grid>
            </form>
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(Omero);
