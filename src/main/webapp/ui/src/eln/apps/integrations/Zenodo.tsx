import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";
import { observer } from "mobx-react-lite";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/zenodo";
import ZenodoIcon from "../../../assets/branding/zenodo/logo.svg";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type ZenodoArgs = {
  integrationState: IntegrationStates["ZENODO"];
  update: (newIntegrationState: IntegrationStates["ZENODO"]) => void;
};

/*
 * Zenodo uses API-key based authentication, as implemeted by the form below.
 */
function Zenodo({ integrationState, update }: ZenodoArgs): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const [apiKey, setApiKey] = useState(integrationState.credentials.ZENODO_USER_TOKEN.orElse(""));

  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.zenodo.name")}
        integrationState={integrationState}
        explanatoryText={t("integrations.zenodo.description")}
        image={ZenodoIcon}
        color={LOGO_COLOR}
        usageText={t("integrations.zenodo.usage")}
        helpLinkText={t("integrations.zenodo.helpLink")}
        website="zenodo.org"
        docLink="zenodo"
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.zenodo.setup.instructions" />
            <Card variant="outlined" sx={{ mt: 2 }}>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void update({
                    mode: integrationState.mode,
                    credentials: {
                      ZENODO_USER_TOKEN: Optional.present(apiKey),
                    },
                  });
                }}
              >
                <CardContent>
                  <TextField
                    fullWidth
                    variant="outlined"
                    label={t("integrations.zenodo.fields.apiKey")}
                    type="password"
                    size="small"
                    value={apiKey}
                    onChange={({ target: { value } }) => {
                      setApiKey(value);
                    }}
                  />
                </CardContent>
                <CardActions>
                  <Button type="submit">{t("common:actions.save")}</Button>
                </CardActions>
              </form>
            </Card>
          </>
        }
        update={(newMode) => {
          update({
            mode: newMode,
            credentials: integrationState.credentials,
          });
        }}
      />
    </Grid>
  );
}

export default React.memo(observer(Zenodo));
