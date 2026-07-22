import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Fade from "@mui/material/Fade";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormHelperText from "@mui/material/FormHelperText";
import FormLabel from "@mui/material/FormLabel";
import Grid from "@mui/material/Grid";
import Switch from "@mui/material/Switch";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import TransRichText, { helpDocsArticleUrl } from "@/modules/common/i18n/TransRichText";
import ApiService from "../../../common/InvApiService";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import RadioField, { type RadioOption } from "../../../components/Inputs/RadioField";
import SubmitSpinnerButton from "../../../components/SubmitSpinnerButton";
import WarningBar from "../../../components/WarningBar";
import type { DataCiteServerUrl, IntegrationState, SystemSettings } from "../../../stores/stores/AuthStore";
import useStores from "../../../stores/use-stores";
import { getErrorMessage } from "../../../util/error";

type PIDINSTDataciteCardArgs = {
  currentSettings: SystemSettings["pidinstDatacite"];
  isConflict: boolean;
  onEnabledChange: (enabled: IntegrationState) => void;
};

export default function PIDINSTDataciteCard({
  currentSettings,
  isConflict,
  onEnabledChange,
}: PIDINSTDataciteCardArgs): React.ReactNode {
  const [updatedSettings, setUpdatedSettings] = useState<SystemSettings["pidinstDatacite"]>(currentSettings);
  const [savedSettings, setSavedSettings] = useState(currentSettings);
  const [lastTestResult, setLastTestResult] = useState<
    { response: "success" } | { response: "failed"; message: string } | null
  >(null);

  const [savingInFlight, setSavingInFlight] = useState(false);
  const { authStore } = useStores();
  const { t } = useTranslation(["inventory", "common"]);
  const settingsLabels: Record<keyof SystemSettings["pidinstDatacite"], string> = {
    enabled: t("settings.pidinst.datacite.labels.enabled"),
    serverUrl: t("settings.pidinst.datacite.labels.serverUrl"),
    username: t("settings.pidinst.datacite.labels.username"),
    password: t("settings.pidinst.datacite.labels.password"),
    repositoryPrefix: t("settings.pidinst.datacite.labels.repositoryPrefix"),
  };
  const serverUrlOptions: Array<RadioOption<DataCiteServerUrl>> = [
    { value: "https://api.datacite.org", label: t("settings.pidinst.datacite.serverOptions.production") },
    { value: "https://api.test.datacite.org", label: t("settings.pidinst.datacite.serverOptions.test") },
  ];

  const onSubmitHandler = async () => {
    if (updatedSettings) {
      setLastTestResult(null);
      setSavingInFlight(true);
      await authStore.updateSystemSettings("pidinstDatacite", updatedSettings);
      setSavingInFlight(false);
      setSavedSettings(updatedSettings);
    }
  };

  const unsavedChanges: boolean = JSON.stringify(updatedSettings) !== JSON.stringify(savedSettings);

  const connectionStatusId = useId();
  const showConnectionStatus = !unsavedChanges && Boolean(lastTestResult);

  return (
    <Card elevation={0} variant="outlined">
      <CardContent sx={{ pt: 0.5, pb: 1 }}>
        <FormControl>
          <FormLabel>
            {t("settings.pidinst.datacite.formLabel")}
            <HelpLinkIcon
              link={helpDocsArticleUrl("pidinstIdentifiers")}
              title={t("settings.pidinst.datacite.helpTitle")}
            />
          </FormLabel>
          <FormHelperText component="div" sx={{ m: 0 }}>
            <TransRichText i18nKey="inventory:settings.pidinst.datacite.formHelperText" />
          </FormHelperText>
        </FormControl>
        <Box sx={{ mt: 1.5 }}>
          <FormControl component="fieldset" fullWidth>
            <FormLabel id="pidinst-datacite-details-label">{t("settings.pidinst.datacite.detailsLabel")}</FormLabel>
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
                      placeholder={t("settings.pidinst.datacite.placeholder", {
                        label: settingsLabels[entry[0]],
                      })}
                      helperText={entry[1] === "" ? t("settings.pidinst.datacite.fieldRequiredError") : null}
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
                        name={t("settings.pidinst.datacite.serverUrlLabel")}
                        value={updatedSettings.serverUrl}
                        onChange={({ target }) => {
                          if (target.value !== null && typeof target.value !== "undefined") {
                            setUpdatedSettings({
                              ...updatedSettings,
                              serverUrl: target.value,
                            });
                          }
                        }}
                        options={serverUrlOptions}
                        smallText
                        row
                      />
                    </Grid>
                  )}
                </Grid>
              ))}
          </FormControl>
        </Box>
        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mt: 1.5 }}>
          <FormControlLabel
            sx={{ "& .MuiFormControlLabel-label": { fontSize: "0.8125rem" } }}
            control={
              <Switch
                checked={updatedSettings.enabled === "true"}
                onChange={({ target: { checked } }) => {
                  const newEnabled: IntegrationState = checked ? "true" : "false";
                  setUpdatedSettings({ ...updatedSettings, enabled: newEnabled });
                  onEnabledChange(newEnabled);
                }}
                color="primary"
                slotProps={{ input: { role: "checkbox" } }}
              />
            }
            label={t("settings.pidinst.datacite.enableLabel")}
          />
          {unsavedChanges && <WarningBar />}
        </Box>
      </CardContent>
      <CardActions sx={{ justifyContent: "flex-end", pt: 0.5 }}>
        <Box sx={{ mr: 1 }}>
          {isConflict ? (
            <Typography variant="caption" sx={{ color: "error.main" }}>
              {t("settings.pidinst.conflictWarning")}
            </Typography>
          ) : (
            <Fade in={showConnectionStatus}>
              <Box role="status" id={connectionStatusId}>
                {lastTestResult?.response === "success" && (
                  <Typography variant="caption" sx={{ color: "success.main" }}>
                    {t("settings.pidinst.datacite.connectionSucceeded")}
                  </Typography>
                )}
                {lastTestResult?.response === "failed" && (
                  <Typography variant="caption" sx={{ color: "warningRed" }}>
                    {t("settings.pidinst.datacite.connectionFailed", { message: lastTestResult.message })}
                  </Typography>
                )}
              </Box>
            </Fade>
          )}
        </Box>
        <Button
          /*
           * The test connection API tests against the details that are
           * currently saved. If the user has made changes in the UI that they
           * haven't saved then testing the connection would not be using what
           * the user is seeing, and would likely be confusing. As such, we
           * disable the button and require they save first.
           */
          disabled={unsavedChanges || isConflict}
          variant="outlined"
          sx={{ minWidth: "max-content" }}
          onClick={() => {
            ApiService.get<boolean>("/identifiers/testPidinstConnection")
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
          {t("settings.pidinst.datacite.testConnection")}
        </Button>
        <SubmitSpinnerButton
          label={t("common:actions.save")}
          disabled={!unsavedChanges || savingInFlight || isConflict}
          loading={savingInFlight}
          onClick={() => void onSubmitHandler()}
        />
      </CardActions>
    </Card>
  );
}
