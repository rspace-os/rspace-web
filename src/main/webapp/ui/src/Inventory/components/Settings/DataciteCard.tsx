import React, { useState, useId } from "react";
import useStores from "../../../stores/use-stores";
import Box from "@mui/material/Box";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import RadioField, {
  type RadioOption,
} from "../../../components/Inputs/RadioField";
import SubmitSpinnerButton from "../../../components/SubmitSpinnerButton";
import TextField from "@mui/material/TextField";
import {
  type IntegrationState,
  type DataCiteServerUrl,
  type SystemSettings,
} from "../../../stores/stores/AuthStore";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import WarningBar from "../../../components/WarningBar";
import docLinks from "../../../assets/DocLinks";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import FormHelperText from "@mui/material/FormHelperText";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import Fade from "@mui/material/Fade";
import Grid from "@mui/material/Grid";
import ApiService from "../../../common/InvApiService";
import { doNotAwait } from "../../../util/Util";
import { getErrorMessage } from "../../../util/error";

const SETTINGS_LABELS: Record<keyof SystemSettings["datacite"], string> = {
  enabled: "Enabled",
  serverUrl: "Server URL",
  username: "Repository Account ID",
  password: "Password",
  repositoryPrefix: "Repository Prefix",
};

const dataciteIntegrationOptions: Array<RadioOption<IntegrationState>> = [
  { value: "true", label: "Enabled" },
  { value: "false", label: "Disabled" },
];

const dataciteServerUrlOptions: Array<RadioOption<DataCiteServerUrl>> = [
  { value: "https://api.datacite.org", label: "Production" },
  { value: "https://api.test.datacite.org", label: "Test" },
];

type DataciteCardArgs = {
  currentSettings: SystemSettings["datacite"];
};

export default function DataciteCard({
  currentSettings,
}: DataciteCardArgs): React.ReactNode {
  const [updatedSettings, setUpdatedSettings] =
    useState<SystemSettings["datacite"]>(currentSettings);
  const [savedSettings, setSavedSettings] = useState(currentSettings);
  const [lastTestResult, setLastTestResult] = useState<
    { response: "success" } | { response: "failed"; message: string } | null
  >(null);

  const [savingInFlight, setSavingInFlight] = useState(false);
  const { authStore } = useStores();

  const onSubmitHandler = async () => {
    if (updatedSettings) {
      setLastTestResult(null);
      setSavingInFlight(true);
      await authStore.updateSystemSettings("datacite", updatedSettings);
      setSavingInFlight(false);
      setSavedSettings(updatedSettings);
    }
  };

  const unsavedChanges: boolean =
    JSON.stringify(updatedSettings) !== JSON.stringify(savedSettings);

  const connectionStatusId = useId();
  return (
    <Card elevation={0} variant="outlined">
      <CardContent sx={{ pt: 0.5 }}>
        <FormControl>
          <FormLabel>
            DataCite IGSN Integration
            <HelpLinkIcon
              link={docLinks.IGSNIdentifiers}
              title="Add IGSN Identifiers to your Inventory Items"
            />
          </FormLabel>
          <FormHelperText component="div" sx={{ m: 0 }}>
            You can associate IGSN IDs with Inventory items by connecting to{" "}
            <a href="https://datacite.org/" target="_blank" rel="noreferrer">
              DataCite
            </a>{" "}
            using your Repository account credentials.
          </FormHelperText>
          <RadioField
            name={"DataCite Integration Settings"}
            value={updatedSettings.enabled}
            onChange={({ target }) => {
              if (
                target.value !== null &&
                typeof target.value !== "undefined"
              ) {
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
        <Box mt={1.5}>
          <FormControl component="fieldset" fullWidth>
            <FormLabel id="igsn-details-label">Details</FormLabel>
            {(
              Object.entries(updatedSettings) as ReadonlyArray<
                [keyof typeof updatedSettings, string]
              >
            )
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
                  <Grid item sx={{ flexGrow: 1 }}>
                    <TextField
                      sx={{ p: 0.5, m: 1 }}
                      InputLabelProps={{
                        shrink: true,
                      }}
                      size="small"
                      fullWidth
                      label={SETTINGS_LABELS[entry[0]]}
                      onChange={({ target }) => {
                        setUpdatedSettings({
                          ...updatedSettings,
                          [entry[0]]: target.value,
                        });
                      }}
                      error={entry[1] === ""}
                      value={entry[1]}
                      placeholder={`Please enter a value for ${
                        SETTINGS_LABELS[entry[0]]
                      }`}
                      helperText={
                        entry[1] === "" ? "A valid value is required" : null
                      }
                      variant="outlined"
                      disabled={
                        !updatedSettings.enabled || entry[0] === "serverUrl"
                      }
                    />
                  </Grid>
                  {entry[0] === "serverUrl" && (
                    <Grid item sx={{ width: "200px" }}>
                      <RadioField
                        name={"DataCite Server URL"}
                        value={updatedSettings.serverUrl}
                        onChange={({ target }) => {
                          if (
                            target.value !== null &&
                            typeof target.value !== "undefined"
                          ) {
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
        <Fade in={!unsavedChanges && Boolean(lastTestResult)}>
          <Box mr={1} role="status" id={connectionStatusId}>
            {lastTestResult?.response === "success" && (
              <Typography variant="caption" sx={{ color: "success.main" }}>
                Connection succeeded
              </Typography>
            )}
            {lastTestResult?.response === "failed" && (
              <Typography variant="caption" sx={{ color: "warningRed" }}>
                Connection failed. {lastTestResult.message}
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
          style={{ minWidth: "max-content" }}
          onClick={() => {
            ApiService.get<boolean>("/identifiers/testDataCiteConnection")
              .then(({ data }) => {
                setLastTestResult(
                  data
                    ? { response: "success" }
                    : { response: "failed", message: "" }
                );
              })
              .catch((e) => {
                setLastTestResult({
                  response: "failed",
                  message: getErrorMessage(e, "Unknown reason."),
                });
              });
          }}
          aria-controls={connectionStatusId}
        >
          Test Connection
        </Button>
        <SubmitSpinnerButton
          label="Save"
          disabled={!unsavedChanges || savingInFlight}
          loading={savingInFlight}
          onClick={doNotAwait(onSubmitHandler)}
        />
      </CardActions>
    </Card>
  );
}
