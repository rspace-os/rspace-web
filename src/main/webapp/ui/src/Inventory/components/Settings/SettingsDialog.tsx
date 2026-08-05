import { faFlask } from "@fortawesome/free-solid-svg-icons/faFlask";
import { faMicroscope } from "@fortawesome/free-solid-svg-icons/faMicroscope";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import RadioField, { type RadioOption } from "../../../components/Inputs/RadioField";
import type { IntegrationState } from "../../../stores/stores/AuthStore";
import useStores from "../../../stores/use-stores";
import IGSNDataciteCard from "./IGSNDataciteCard";
import PIDINSTB2InstCard from "./PIDINSTB2InstCard";
import PIDINSTDataciteCard from "./PIDINSTDataciteCard";

type SettingsDialogArgs = {
  open: boolean;
  setOpen: (open: boolean) => void;
};

type PidinstProvider = "datacite" | "b2inst";

function SettingsDialog({ open, setOpen }: SettingsDialogArgs): React.ReactNode {
  const { authStore } = useStores();
  const { t } = useTranslation(["inventory", "common"]);

  const [fetchingSystemSettings, setFetchingSystemSettings] = useState<
    null | { state: "loading" } | { state: "loaded" } | { state: "error"; error: string }
  >(null);

  const [activeTab, setActiveTab] = useState(0);
  const [pidinstProvider, setPidinstProvider] = useState<PidinstProvider>("datacite");
  const [pidinstDataciteEnabled, setPidinstDataciteEnabled] = useState<IntegrationState>("false");
  const [pidinstB2InstEnabled, setPidinstB2InstEnabled] = useState<IntegrationState>("false");
  const [igsnEnabled, setIgsnEnabled] = useState<IntegrationState>("false");

  /**
   * the settings endpoint is only called when the dialog is actually rendered
   * ie for users who are not sysAdmin it won't be called at all
   */
  useEffect(() => {
    async function initialiseSettings() {
      setFetchingSystemSettings({ state: "loading" });
      try {
        await authStore.getSystemSettings();
        setFetchingSystemSettings({ state: "loaded" });
      } catch (e) {
        if (e instanceof Error) setFetchingSystemSettings({ state: "error", error: e.message });
      }
    }
    void initialiseSettings();
  }, []);

  useEffect(() => {
    if (fetchingSystemSettings?.state === "loaded" && authStore.systemSettings) {
      setPidinstProvider(authStore.systemSettings.pidinstB2Inst.enabled === "true" ? "b2inst" : "datacite");
      setPidinstDataciteEnabled(authStore.systemSettings.pidinstDatacite.enabled);
      setPidinstB2InstEnabled(authStore.systemSettings.pidinstB2Inst.enabled);
      setIgsnEnabled(authStore.systemSettings.igsnDatacite.enabled);
    }
  }, [fetchingSystemSettings?.state]);

  const handleClose = () => {
    setOpen(false);
  };

  if (!authStore.systemSettings) return null;

  const isPidinstConflict = pidinstDataciteEnabled === "true" && pidinstB2InstEnabled === "true";

  const pidinstConnection: PidinstProvider | null =
    pidinstDataciteEnabled === "true" ? "datacite" : pidinstB2InstEnabled === "true" ? "b2inst" : null;

  const igsnConnected = igsnEnabled === "true";

  const pidinstProviderOptions: Array<RadioOption<PidinstProvider>> = [
    { value: "datacite", label: t("settings.pidinst.providerOptions.datacite") },
    { value: "b2inst", label: t("settings.pidinst.providerOptions.b2inst") },
  ];

  function PIDINSTTabLabel() {
    return (
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, textAlign: "left" }}>
        <FontAwesomeIcon icon={faMicroscope} size="2x" color={activeTab === 0 ? "rgb(46, 125, 50)" : undefined} />
        <Box>
          <Typography variant="body2" sx={{ lineHeight: 1.3 }}>
            <strong>{t("settings.tabs.pidinst.typeLabel")}</strong> {t("settings.tabs.pidinst.instrumentsLabel")}
          </Typography>
          <Typography
            variant="caption"
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 0.3,
              color: pidinstConnection ? "success.main" : "text.secondary",
            }}
          >
            {pidinstConnection ? (
              <>
                <CheckCircleIcon sx={{ fontSize: "0.9em" }} />
                {t("settings.tabs.pidinst.connected", {
                  provider: t(`settings.pidinst.providerOptions.${pidinstConnection}`),
                })}
              </>
            ) : (
              t("settings.tabs.pidinst.nothingConnected")
            )}
          </Typography>
        </Box>
      </Box>
    );
  }

  function IGSNTabLabel() {
    return (
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, textAlign: "left" }}>
        <FontAwesomeIcon icon={faFlask} size="2x" color={activeTab === 1 ? "rgb(46, 125, 50)" : undefined} />
        <Box>
          <Typography variant="body2" sx={{ lineHeight: 1.3 }}>
            <strong>{t("settings.tabs.igsn.typeLabel")}</strong> {t("settings.tabs.igsn.samplesLabel")}
          </Typography>
          <Typography
            variant="caption"
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 0.3,
              color: igsnConnected ? "success.main" : "text.secondary",
            }}
          >
            {igsnConnected ? (
              <>
                <CheckCircleIcon sx={{ fontSize: "0.9em" }} />
                {t("settings.tabs.igsn.connected")}
              </>
            ) : (
              t("settings.tabs.igsn.notConnected")
            )}
          </Typography>
        </Box>
      </Box>
    );
  }

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>{t("settings.dialog.title")}</DialogTitle>
      <DialogContent>
        {fetchingSystemSettings?.state === "loading" && <>{t("common:loading")}</>}
        {fetchingSystemSettings?.state === "error" && (
          <Alert severity="error">
            <AlertTitle>{t("settings.dialog.error")}</AlertTitle>
            {fetchingSystemSettings.error}
          </Alert>
        )}
        {fetchingSystemSettings?.state === "loaded" ? (
          <>
            <Tabs
              value={activeTab}
              onChange={(_, newValue: number) => setActiveTab(newValue)}
              variant="fullWidth"
              sx={{ mb: 2, borderBottom: 1, borderColor: "divider" }}
            >
              <Tab
                label={<PIDINSTTabLabel />}
                wrapped
                sx={{ textTransform: "none", alignItems: "flex-start", minHeight: 72 }}
              />
              <Tab
                label={<IGSNTabLabel />}
                wrapped
                sx={{ textTransform: "none", alignItems: "flex-start", minHeight: 72 }}
              />
            </Tabs>

            {/* PIDINST tab panel */}
            <Box role="tabpanel" hidden={activeTab !== 0}>
              <Card elevation={0} variant="outlined" sx={{ mb: 1.5 }}>
                <CardContent sx={{ py: 1, pl: 4, "&:last-child": { pb: 1 } }}>
                  <Typography variant="body2" sx={{ ml: "-7px" }}>
                    {t("settings.pidinst.providerSelectLabel")}
                  </Typography>
                  <FormControl
                    component="fieldset"
                    sx={{
                      width: "fit-content",
                      "& .MuiFormControlLabel-label": { fontWeight: "bold" },
                      "& .MuiRadioGroup-root": { gap: 3, justifyContent: "flex-start" },
                    }}
                  >
                    <RadioField
                      name={t("settings.pidinst.providerLabel")}
                      value={pidinstProvider}
                      onChange={({ target }) => {
                        if (target.value === "datacite" || target.value === "b2inst") {
                          setPidinstProvider(target.value);
                        }
                      }}
                      options={pidinstProviderOptions}
                      smallText
                      row
                    />
                  </FormControl>
                </CardContent>
              </Card>
              {/* Keep both cards mounted so in-progress edits are preserved when switching providers */}
              <Box sx={{ display: pidinstProvider === "datacite" ? undefined : "none" }}>
                <PIDINSTDataciteCard
                  currentSettings={authStore.systemSettings.pidinstDatacite}
                  isConflict={isPidinstConflict}
                  onEnabledChange={setPidinstDataciteEnabled}
                />
              </Box>
              <Box sx={{ display: pidinstProvider === "b2inst" ? undefined : "none" }}>
                <PIDINSTB2InstCard
                  currentSettings={authStore.systemSettings.pidinstB2Inst}
                  isConflict={isPidinstConflict}
                  onEnabledChange={setPidinstB2InstEnabled}
                />
              </Box>
            </Box>

            {/* IGSN tab panel */}
            <Box role="tabpanel" hidden={activeTab !== 1}>
              <FormControl component="fieldset" fullWidth>
                <IGSNDataciteCard
                  currentSettings={authStore.systemSettings.igsnDatacite}
                  onEnabledChange={setIgsnEnabled}
                />
              </FormControl>
            </Box>
          </>
        ) : null}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{t("common:actions.close")}</Button>
      </DialogActions>
    </Dialog>
  );
}

export default observer(SettingsDialog);
