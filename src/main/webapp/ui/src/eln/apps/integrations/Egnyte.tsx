import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "../../../assets/branding/egnyte";
import EgnyteIcon from "../../../assets/branding/egnyte/logo.svg";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type EgnyteArgs = {
  integrationState: IntegrationStates["EGNYTE"];
  update: (newIntegrationState: IntegrationStates["EGNYTE"]) => void;
};

/*
 * Egnyte has a domain URL that is configured by the this text field.
 */
function Egnyte({ integrationState, update }: EgnyteArgs): React.ReactNode {
  const { t } = useTranslation("apps");
  const [url, setUrl] = useState(integrationState.credentials.EGNYTE_DOMAIN);

  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.egnyte.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.egnyte.description")}
        image={EgnyteIcon}
        color={LOGO_COLOR}
        update={(newMode) => {
          return update({
            mode: newMode,
            credentials: integrationState.credentials,
          });
        }}
        usageText={t("integrations.egnyte.usage")}
        helpLinkText={t("integrations.egnyte.helpLink")}
        website="egnyte.com"
        docLink="cloudstorage"
        setupSection={
          <>
            <ol>
              <li>{t("integrations.egnyte.setup.provideDomain")}</li>
              <li>{t("integrations.egnyte.setup.enable")}</li>
              <li>{t("integrations.egnyte.setup.toolbar")}</li>
            </ol>
            <Card variant="outlined" sx={{ mt: 2 }}>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void update({
                    mode: integrationState.mode,
                    credentials: {
                      EGNYTE_DOMAIN: url,
                    },
                  });
                }}
              >
                <CardContent>
                  <TextField
                    fullWidth
                    variant="outlined"
                    label={t("integrations.egnyte.fields.domainUrl")}
                    size="small"
                    value={url.orElse("")}
                    onChange={({ target: { value } }) => {
                      setUrl(Optional.present(value));
                    }}
                  />
                </CardContent>
                <CardActions>
                  <Button type="submit">{t("actions.save")}</Button>
                </CardActions>
              </form>
            </Card>
          </>
        }
      />
    </Grid>
  );
}

export default React.memo(Egnyte);
