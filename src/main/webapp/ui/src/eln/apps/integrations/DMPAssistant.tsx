import Grid from "@mui/material/Grid";
import React from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import Button from "@mui/material/Button";
import DMPAssistantIcon from "../../../assets/branding/dmpassistant/logo.svg";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import TextField from "@mui/material/TextField";
import { Optional } from "../../../util/optional";
import { LOGO_COLOR } from "../../../assets/branding/dmpassistant";

type DMPAssistantArgs = {
  integrationState: IntegrationStates["DMPASSISTANT"];
  update: (newIntegrationState: IntegrationStates["DMPASSISTANT"]) => void;
};

/*
 * DMP Assistant uses a personal-access-token authentication mechanism; the
 * user pastes a token from their DMP Assistant account into the text field
 * below.
 */
function DMPAssistant({
  integrationState,
  update,
}: DMPAssistantArgs): React.ReactNode {
  const [apiKey, setApiKey] = React.useState(
    integrationState.credentials.DMPASSISTANT_USER_TOKEN.orElse("")
  );
  const trimmedApiKey = apiKey.trim();
  const isApiKeyBlank = trimmedApiKey.length === 0;

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="DMP Assistant"
        integrationState={integrationState}
        explanatoryText="Portage Network's DMP Assistant — create and manage Data Management Plans in DMPRoadmap."
        image={DMPAssistantIcon}
        color={LOGO_COLOR}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        helpLinkText="DMP Assistant integration docs"
        website="dmp-pgd.ca"
        docLink="dmpassistant"
        usageText="You can import Data Management Plans (DMPs) from DMP Assistant into RSpace, reference them in RSpace documents, and attach them to data deposits when exporting to repositories."
        setupSection={
          <>
            <ol>
              <li>
                Obtain a personal access token from DMP Assistant (account
                settings → API).
              </li>
              <li>Paste it into the field below, then Save.</li>
              <li>Enable the integration.</li>
            </ol>
            <Card variant="outlined" sx={{ mt: 2 }}>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  if (isApiKeyBlank) return;
                  void update({
                    mode: integrationState.mode,
                    credentials: {
                      DMPASSISTANT_USER_TOKEN: Optional.present(trimmedApiKey),
                    },
                  });
                }}
              >
                <CardContent>
                  <TextField
                    fullWidth
                    variant="outlined"
                    label="Personal Access Token"
                    type="password"
                    size="small"
                    value={apiKey}
                    onChange={({ target: { value } }) => {
                      setApiKey(value);
                    }}
                    error={isApiKeyBlank}
                    helperText={
                      isApiKeyBlank
                        ? "A personal access token is required."
                        : undefined
                    }
                  />
                </CardContent>
                <CardActions>
                  <Button type="submit" disabled={isApiKeyBlank}>
                    Save
                  </Button>
                </CardActions>
              </form>
            </Card>
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(DMPAssistant);
