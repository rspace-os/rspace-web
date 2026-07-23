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
import SubmitSpinnerButton from "../../../components/SubmitSpinnerButton";
import WarningBar from "../../../components/WarningBar";
import type { B2InstSettings, IntegrationState, SystemSettings } from "../../../stores/stores/AuthStore";
import useStores from "../../../stores/use-stores";
import { getErrorMessage } from "../../../util/error";

type PIDINSTB2InstCardArgs = {
  currentSettings: SystemSettings["pidinstB2Inst"];
  isConflict: boolean;
  onEnabledChange: (enabled: IntegrationState) => void;
};

/** Labels for the fields that are shown in the UI (repositoryPrefix is always hidden). */
type VisibleB2InstField = Exclude<keyof B2InstSettings, "enabled" | "repositoryPrefix">;

export default function PIDINSTB2InstCard({
  currentSettings,
  isConflict,
  onEnabledChange,
}: PIDINSTB2InstCardArgs): React.ReactNode {
  const [updatedSettings, setUpdatedSettings] = useState<SystemSettings["pidinstB2Inst"]>(currentSettings);
  const [savedSettings, setSavedSettings] = useState(currentSettings);
  const [lastTestResult, setLastTestResult] = useState<
    { response: "success" } | { response: "failed"; message: string } | null
  >(null);

  const [savingInFlight, setSavingInFlight] = useState(false);
  const { authStore } = useStores();
  const { t } = useTranslation(["inventory", "common"]);

  const settingsLabels: Record<VisibleB2InstField, string> = {
    serverUrl: t("settings.pidinst.b2inst.labels.serverUrl"),
    username: t("settings.pidinst.b2inst.labels.username"),
    password: t("settings.pidinst.b2inst.labels.password"),
  };

  const onSubmitHandler = async () => {
    if (updatedSettings) {
      setLastTestResult(null);
      setSavingInFlight(true);
      await authStore.updateSystemSettings("pidinstB2Inst", updatedSettings);
      setSavingInFlight(false);
      setSavedSettings(updatedSettings);
    }
  };

  const unsavedChanges: boolean = JSON.stringify(updatedSettings) !== JSON.stringify(savedSettings);

  const connectionStatusId = useId();
  const showConnectionStatus = !unsavedChanges && Boolean(lastTestResult);

  const visibleFields: ReadonlyArray<VisibleB2InstField> = ["serverUrl", "username", "password"];

  return (
    <Card elevation={0} variant="outlined">
      <CardContent sx={{ pt: 0.5, pb: 1 }}>
        <FormControl>
          <FormLabel>
            {t("settings.pidinst.b2inst.formLabel")}
            <HelpLinkIcon
              link={helpDocsArticleUrl("pidinstIdentifiers")}
              title={t("settings.pidinst.b2inst.helpTitle")}
            />
          </FormLabel>
          <FormHelperText component="div" sx={{ m: 0 }}>
            <TransRichText i18nKey="inventory:settings.pidinst.b2inst.formHelperText" />
          </FormHelperText>
        </FormControl>
        <Box sx={{ mt: 1.5 }}>
          <FormControl component="fieldset" fullWidth>
            <FormLabel id="pidinst-b2inst-details-label">{t("settings.pidinst.b2inst.detailsLabel")}</FormLabel>
            {visibleFields.map((field) => (
              <Grid
                key={field}
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
                    label={settingsLabels[field]}
                    onChange={({ target }) => {
                      setUpdatedSettings({
                        ...updatedSettings,
                        [field]: target.value,
                      });
                    }}
                    error={updatedSettings[field] === ""}
                    value={updatedSettings[field]}
                    placeholder={t("settings.pidinst.b2inst.placeholder", { label: settingsLabels[field] })}
                    helperText={updatedSettings[field] === "" ? t("settings.pidinst.b2inst.fieldRequiredError") : null}
                    variant="outlined"
                    slotProps={{
                      inputLabel: {
                        shrink: true,
                      },
                    }}
                  />
                </Grid>
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
            label={t("settings.pidinst.b2inst.enableLabel")}
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
                    {t("settings.pidinst.b2inst.connectionSucceeded")}
                  </Typography>
                )}
                {lastTestResult?.response === "failed" && (
                  <Typography variant="caption" sx={{ color: "warningRed" }}>
                    {t("settings.pidinst.b2inst.connectionFailed", { message: lastTestResult.message })}
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
          {t("settings.pidinst.b2inst.testConnection")}
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
