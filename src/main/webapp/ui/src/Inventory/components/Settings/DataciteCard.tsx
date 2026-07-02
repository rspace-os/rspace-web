import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Fade from "@mui/material/Fade";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import FormLabel from "@mui/material/FormLabel";
import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import docLinks from "../../../assets/DocLinks";
import ApiService from "../../../common/InvApiService";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import RadioField, { type RadioOption } from "../../../components/Inputs/RadioField";
import SubmitSpinnerButton from "../../../components/SubmitSpinnerButton";
import WarningBar from "../../../components/WarningBar";
import TransRichText from "../../../modules/common/i18n/TransRichText";
import type { DataCiteServerUrl, IntegrationState, SystemSettings } from "../../../stores/stores/AuthStore";
import useStores from "../../../stores/use-stores";
import { getErrorMessage } from "../../../util/error";

type DataciteCardArgs = {
  currentSettings: SystemSettings["datacite"];
};

export default function DataciteCard({ currentSettings }: DataciteCardArgs): React.ReactNode {
  const [updatedSettings, setUpdatedSettings] = useState<SystemSettings["datacite"]>(currentSettings);
  const [savedSettings, setSavedSettings] = useState(currentSettings);
  const [lastTestResult, setLastTestResult] = useState<
    { response: "success" } | { response: "failed"; message: string } | null
  >(null);

  const [savingInFlight, setSavingInFlight] = useState(false);
  const { authStore } = useStores();
  const { t } = useTranslation(["inventory", "common"]);
  const settingsLabels: Record<keyof SystemSettings["datacite"], string> = {
    enabled: t("settings.datacite.labels.enabled"),
    serverUrl: t("settings.datacite.labels.serverUrl"),
    username: t("settings.datacite.labels.username"),
    password: t("settings.datacite.labels.password"),
    repositoryPrefix: t("settings.datacite.labels.repositoryPrefix"),
  };
  const dataciteIntegrationOptions: Array<RadioOption<IntegrationState>> = [
    { value: "true", label: t("settings.datacite.statusOptions.enabled") },
    { value: "false", label: t("settings.datacite.statusOptions.disabled") },
  ];
  const dataciteServerUrlOptions: Array<RadioOption<DataCiteServerUrl>> = [
    { value: "https://api.datacite.org", label: t("settings.datacite.serverOptions.production") },
    { value: "https://api.test.datacite.org", label: t("settings.datacite.serverOptions.test") },
  ];

  const onSubmitHandler = async () => {
    if (updatedSettings) {
      setLastTestResult(null);
      setSavingInFlight(true);
      await authStore.updateSystemSettings("datacite", updatedSettings);
      setSavingInFlight(false);
      setSavedSettings(updatedSettings);
    }
  };

  const unsavedChanges: boolean = JSON.stringify(updatedSettings) !== JSON.stringify(savedSettings);

  const connectionStatusId = useId();
  const showConnectionStatus = !unsavedChanges && Boolean(lastTestResult);

  return (
    <Card elevation={0} variant="outlined">
      <CardContent sx={{ pt: 0.5 }}>
        <FormControl>
          <FormLabel>
            {t("settings.datacite.formLabel")}
            <HelpLinkIcon link={docLinks.igsnIdentifiers} title={t("settings.datacite.helpTitle")} />
          </FormLabel>
          <FormHelperText component="div" sx={{ m: 0 }}>
            <TransRichText i18nKey="inventory:settings.datacite.formHelperText" />
          </FormHelperText>
          <RadioField
            name={t("settings.datacite.formLabel")}
            value={updatedSettings.enabled}
            onChange={({ target }) => {
              if (target.value !== null && typeof target.value !== "undefined") {
                setUpdatedSettings({
                  ...updatedSettings,
                  enabled: target.value,
                });
              }
            }}
            options={dataciteIntegrationOptions}
            smallText
          />
        </FormControl>
        <Box sx={{ mt: 1.5 }}>
          <FormControl component="fieldset" fullWidth>
            <FormLabel id="igsn-details-label">{t("settings.datacite.detailsLabel")}</FormLabel>
            {(Object.entries(updatedSettings) as ReadonlyArray<[keyof typeof updatedSettings, string]>)
              .filter((entry) => entry[0] !== "enabled")
              .map((entry) => (
                <Grid
                  key={entry[0]}
                  container
                  direction="row"
                  spacing={1}
                  sx={{
                    alignItems: "center",
                    width: "100%",
                  }}
                >
                  <Grid sx={{ flexGrow: 1 }}>
                    <TextField
                      sx={{ p: 0.5, m: 1 }}
                      size="small"
                      fullWidth
                      label={settingsLabels[entry[0]]}
                      onChange={({ target }) => {
                        setUpdatedSettings({
                          ...updatedSettings,
                          [entry[0]]: target.value,
                        });
                      }}
                      error={entry[1] === ""}
                      value={entry[1]}
                      placeholder={t("settings.datacite.placeholder", { label: settingsLabels[entry[0]] })}
                      helperText={entry[1] === "" ? t("settings.datacite.fieldRequiredError") : null}
                      variant="outlined"
                      disabled={!updatedSettings.enabled || entry[0] === "serverUrl"}
                      slotProps={{
                        inputLabel: {
                          shrink: true,
                        },
                      }}
                    />
                  </Grid>
                  {entry[0] === "serverUrl" && (
                    <Grid sx={{ width: "200px" }}>
                      <RadioField
                        name={t("settings.datacite.serverUrlLabel")}
                        value={updatedSettings.serverUrl}
                        onChange={({ target }) => {
                          if (target.value !== null && typeof target.value !== "undefined") {
                            setUpdatedSettings({
                              ...updatedSettings,
                              serverUrl: target.value,
                            });
                          }
                        }}
                        options={dataciteServerUrlOptions}
                        smallText
                        row
                      />
                    </Grid>
                  )}
                </Grid>
              ))}
          </FormControl>
        </Box>
      </CardContent>
      {unsavedChanges && <WarningBar />}
      <CardActions sx={{ justifyContent: "flex-end" }}>
        <Fade in={showConnectionStatus}>
          <Box sx={{ mr: 1 }} role="status" id={connectionStatusId}>
            {lastTestResult?.response === "success" && (
              <Typography variant="caption" sx={{ color: "success.main" }}>
                {t("settings.datacite.connectionSucceeded")}
              </Typography>
            )}
            {lastTestResult?.response === "failed" && (
              <Typography variant="caption" sx={{ color: "warningRed" }}>
                {t("settings.datacite.connectionFailed", { message: lastTestResult.message })}
              </Typography>
            )}
          </Box>
        </Fade>
        <Button
          /*
           * The test connection API tests against the details that are
           * currently saved. If the user has made changes in the UI that they
           * haven't saved then testing the connection would not be using what
           * the user is seeing, and would likely be confusing. As such, we
           * disable the button and require they save first.
           */
          disabled={unsavedChanges}
          variant="outlined"
          sx={{ minWidth: "max-content" }}
          onClick={() => {
            ApiService.get<boolean>("/identifiers/testIgsnConnection")
              .then(({ data }) => {
                setLastTestResult(data ? { response: "success" } : { response: "failed", message: "" });
              })
              .catch((e) => {
                setLastTestResult({
                  response: "failed",
                  message: getErrorMessage(e, t("errors.unknownReason")),
                });
              });
          }}
          aria-controls={showConnectionStatus ? connectionStatusId : undefined}
        >
          {t("settings.datacite.testConnection")}
        </Button>
        <SubmitSpinnerButton
          label={t("common:actions.save")}
          disabled={!unsavedChanges || savingInFlight}
          loading={savingInFlight}
          onClick={() => void onSubmitHandler()}
        />
      </CardActions>
    </Card>
  );
}
