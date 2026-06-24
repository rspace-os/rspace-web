import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import { ThemeProvider } from "@mui/material/styles";
import Tooltip from "@mui/material/Tooltip";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import materialTheme from "../../../theme";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const RS: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const getValidationErrorString: (...args: any[]) => string;

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
function GroupOntologiesManager({ groupId, isCloud, canManageOntologies }: any) {
  const { t } = useTranslation("common");
  const [ontologiesEnforcedStatus, setOntologiesEnforcedStatus] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [waiting, setWaiting] = useState(false);

  const [enableDialogOpen, setEnableDialogOpen] = React.useState(false);
  const [disableDialogOpen, setDisableDialogOpen] = React.useState(false);

  useEffect(() => {
    axios.get(`/groups/ajax/ontologiesEnforcedStatus/${groupId}`).then((response) => {
      if (!response.data.success) {
        const msg = getValidationErrorString(response.data.error, ",", true);
        RS.confirm(msg, "warning", 5000, { sticky: true });
        setWaiting(false);
        return;
      }

      setOntologiesEnforcedStatus(response.data.data);
      setLoaded(true);
    });
  }, [groupId]);

  function enforceGroupOntologies() {
    submit("/groups/ajax/enforceGroupOntologies/", true);
  }

  function unenforceGroupOntologies() {
    submit("/groups/ajax/unenforceGroupOntologies/", false);
  }

  function submit(url: string, isEnabled: boolean) {
    setWaiting(true);

    axios.post(url + groupId).then((response) => {
      if (!response.data.success) {
        const msg = getValidationErrorString(response.data.error, ",", true);
        RS.confirm(msg, "warning", 5000, { sticky: true });
        setWaiting(false);
        return;
      }

      setWaiting(false);
      setOntologiesEnforcedStatus(isEnabled);
      setEnableDialogOpen(false);
      setDisableDialogOpen(false);
    });
  }

  /* Ontologies are not supported on the community version */
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function DisabledOntologiesButton(props: any) {
    // biome-ignore lint/suspicious/noImplicitAnyLet: initial biome migration
    let title;
    if (isCloud) {
      title = t("profile.groups.manager.onlyEnterprise");
    }

    const label =
      props.mode === "un-enforce"
        ? t("profile.groups.ontologies.unenforce.button")
        : t("profile.groups.ontologies.disabledButton");

    return (
      <>
        {isCloud && canManageOntologies && (
          <Tooltip title={title} aria-label={title}>
            <div>
              <Button sx={{ margin: "0 0 0.5em 15px" }} variant="outlined" size="small" disabled>
                {label}
              </Button>
            </div>
          </Tooltip>
        )}
      </>
    );
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function OntologiesButton(props: any) {
    const label =
      props.mode === "enforce"
        ? t("profile.groups.ontologies.enforce.button")
        : t("profile.groups.ontologies.unenforce.button");
    return (
      <>
        {!isCloud && canManageOntologies && (
          <Button sx={{ margin: "0 0 0.5em 15px" }} onClick={props.callback} variant="outlined" size="small">
            {label}
          </Button>
        )}
      </>
    );
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function DialogButtons(props: any) {
    return (
      <>
        <Button onClick={props.onCancel} sx={{ color: "grey" }}>
          {t("profile.groups.manager.cancel")}
        </Button>
        <Button onClick={props.onConfirm} color="primary" disabled={waiting}>
          {t("profile.groups.manager.confirm")}
          {waiting && <CircularProgress size={20} sx={{ position: "absolute", margin: "0 auto" }} />}
        </Button>
      </>
    );
  }

  return (
    <ThemeProvider theme={materialTheme}>
      {loaded && !ontologiesEnforcedStatus && (
        <>
          <DisabledOntologiesButton mode="un-enforce" />
          <OntologiesButton mode="enforce" callback={() => setEnableDialogOpen(true)} />
          <Dialog open={enableDialogOpen} onClose={() => setEnableDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle id="group-publication-dialog-title">
              {t("profile.groups.ontologies.enforce.title")}
            </DialogTitle>
            <DialogContent>
              <DialogContentText>{t("profile.groups.ontologies.enforce.text")}</DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons onCancel={() => setEnableDialogOpen(false)} onConfirm={enforceGroupOntologies} />
            </DialogActions>
          </Dialog>
        </>
      )}
      {loaded && ontologiesEnforcedStatus && (
        <>
          <DisabledOntologiesButton mode="disable" />
          <OntologiesButton mode="un-enforce" callback={() => setDisableDialogOpen(true)} />
          <Dialog open={disableDialogOpen} onClose={() => setDisableDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle id="group-sharing-dialog-title">{t("profile.groups.ontologies.unenforce.title")}</DialogTitle>
            <DialogContent>
              <DialogContentText>{t("profile.groups.ontologies.unenforce.text")}</DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons onCancel={() => setDisableDialogOpen(false)} onConfirm={unenforceGroupOntologies} />
            </DialogActions>
          </Dialog>
        </>
      )}
    </ThemeProvider>
  );
}

export default GroupOntologiesManager;
