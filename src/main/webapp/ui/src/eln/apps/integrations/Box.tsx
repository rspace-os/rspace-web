import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import FormControlLabel from "@mui/material/FormControlLabel";
import Grid from "@mui/material/Grid";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { LOGO_COLOR } from "../../../assets/branding/box";
import BoxIcon from "../../../assets/branding/box/logo.svg";
import { Optional } from "../../../util/optional";
import IntegrationCard from "../IntegrationCard";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

type BoxArgs = {
  integrationState: IntegrationStates["BOX"];
  update: (newIntegrationState: IntegrationStates["BOX"]) => void;
};

/*
 * Note that authentication with box is performaned when the user goes to
 * use the integration by inserting a file into their RSpace document.
 * The /integrations API does return a "box.linking.enabled" boolean,
 * which a setting configured by the sysadmin, but it is not parsed out by
 * useIntegrationsEndpoint because it is not used by any of this code. Even if
 * the user has enabled the integration, if the sysadmin has set this flag to
 * false then the import file button will not be available in the RSpace
 * document editor.
 */
function Box({ integrationState, update }: BoxArgs): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const [linkType, setLinkType] = React.useState(integrationState.credentials.BOX_LINK_TYPE.orElse("LIVE"));

  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name={t("integrations.box.name")}
        explanatoryText={t("integrations.box.description")}
        image={BoxIcon}
        color={LOGO_COLOR}
        update={(newMode) =>
          update({
            mode: newMode,
            credentials: integrationState.credentials,
          })
        }
        integrationState={integrationState}
        usageText={t("integrations.box.usage")}
        setupSection={
          <>
            <TransRichText i18nKey="apps:integrations.box.setup.instructions" />
            <Card variant="outlined" sx={{ mt: 2 }}>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void update({
                    mode: integrationState.mode,
                    credentials: {
                      BOX_LINK_TYPE: Optional.present(linkType),
                      "box.api.enabled": integrationState.credentials["box.api.enabled"],
                    },
                  });
                }}
              >
                <CardContent>
                  {!integrationState.credentials["box.api.enabled"].orElse(false) && (
                    <Typography variant="body2">{t("integrations.box.sysadminNote")}</Typography>
                  )}
                  <RadioGroup
                    value={linkType}
                    sx={{ alignItems: "flex-start" }}
                    onChange={(_event, value) => {
                      setLinkType(value as typeof linkType);
                    }}
                  >
                    <FormControlLabel
                      value="LIVE"
                      control={<Radio />}
                      label={
                        <>
                          <Typography variant="body1">{t("integrations.box.linkType.live")}</Typography>
                          <Typography variant="body2" sx={{ fontSize: "0.825em" }}>
                            {t("integrations.box.linkType.liveDescription")}
                          </Typography>
                        </>
                      }
                    />
                    <FormControlLabel
                      value="VERSIONED"
                      sx={{ alignItems: "flex-start" }}
                      disabled={!integrationState.credentials["box.api.enabled"].orElse(false)}
                      control={<Radio />}
                      label={
                        <>
                          <Typography variant="body1">{t("integrations.box.linkType.versioned")}</Typography>
                          <Typography variant="body2" sx={{ fontSize: "0.825em" }}>
                            {t("integrations.box.linkType.versionedDescription")}
                          </Typography>
                        </>
                      }
                    />
                    <FormControlLabel
                      value="ASK"
                      sx={{ alignItems: "flex-start" }}
                      disabled={!integrationState.credentials["box.api.enabled"].orElse(false)}
                      control={<Radio />}
                      label={
                        <>
                          <Typography variant="body1">{t("integrations.box.linkType.ask")}</Typography>
                          <Typography variant="body2" sx={{ fontSize: "0.825em" }}>
                            {t("integrations.box.linkType.askDescription")}
                          </Typography>
                        </>
                      }
                    />
                  </RadioGroup>
                </CardContent>
                <CardActions>
                  <Button type="submit">{t("common:actions.save")}</Button>
                </CardActions>
              </form>
            </Card>
          </>
        }
        helpLinkText={t("integrations.box.helpLink")}
        website="https://box.com"
        docLink="cloudstorage"
      />
    </Grid>
  );
}

export default React.memo(Box);
