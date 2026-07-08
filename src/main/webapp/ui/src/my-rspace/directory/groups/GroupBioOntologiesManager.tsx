import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import { ThemeProvider } from "@mui/material/styles";
import Tooltip from "@mui/material/Tooltip";
import React, { useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import TransRichText from "@/modules/common/i18n/TransRichText";
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";
import materialTheme from "../../../theme";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const getValidationErrorString: (...args: any[]) => string;

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
function GroupBioOntologiesManager({ groupId, isCloud, canManageOntologies }: any) {
  const { t } = useTranslation("common");
  const [bioOntologiesAllowedStatus, setBioOntologiesAllowedStatus] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [waiting, setWaiting] = useState(false);

  const [enableDialogOpen, setEnableDialogOpen] = React.useState(false);
  const [disableDialogOpen, setDisableDialogOpen] = React.useState(false);
  const { addAlert } = useContext(AlertContext);

  useEffect(() => {
    axios.get(`/groups/ajax/bioOntologiesAllowedStatus/${groupId}`).then((response) => {
      if (!response.data.success) {
        const msg = getValidationErrorString(response.data.error, ",", true);
        addAlert(mkAlert({ message: msg, variant: "warning", duration: 5000 }));
        setWaiting(false);
        return;
      }

      setBioOntologiesAllowedStatus(response.data.data);
      setLoaded(true);
    });
  }, [groupId]);

  function allowGroupBioOntologies() {
    submit("/groups/ajax/allowBioOntologies/", true);
  }

  function disallowGroupBioOntologies() {
    submit("/groups/ajax/disallowBioOntologies/", false);
  }

  function submit(url: string, isEnabled: boolean) {
    setWaiting(true);

    axios.post(url + groupId).then((response) => {
      if (!response.data.success) {
        const msg = getValidationErrorString(response.data.error, ",", true);
        addAlert(mkAlert({ message: msg, variant: "warning", duration: 5000 }));
        setWaiting(false);
        return;
      }

      setWaiting(false);
      setBioOntologiesAllowedStatus(isEnabled);
      setEnableDialogOpen(false);
      setDisableDialogOpen(false);
    });
  }

  /* BioPortal Ontologies are not supported on the community version */
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function DisabledBioOntologiesButton(_props: any) {
    // biome-ignore lint/suspicious/noImplicitAnyLet: initial biome migration
    let title;
    if (isCloud) {
      title = t("profile.groups.manager.onlyEnterprise");
    }

    return (
      <>
        {isCloud && canManageOntologies && (
          <Tooltip title={title} aria-label={title}>
            <div>
              <Button sx={{ margin: "0 0 0.5em 15px" }} variant="outlined" size="small" disabled>
                {t("profile.groups.bioOntologies.disabledButton")}
              </Button>
            </div>
          </Tooltip>
        )}
      </>
    );
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function BioOntologiesButton(props: any) {
    const label =
      props.mode === "allow"
        ? t("profile.groups.bioOntologies.allow.button")
        : t("profile.groups.bioOntologies.disallow.button");
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
      {loaded && !bioOntologiesAllowedStatus && (
        <>
          <DisabledBioOntologiesButton mode="disallow" />
          <BioOntologiesButton mode="allow" callback={() => setEnableDialogOpen(true)} />
          <Dialog open={enableDialogOpen} onClose={() => setEnableDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle id="group-publication-dialog-title">
              {t("profile.groups.bioOntologies.allow.title")}
            </DialogTitle>
            <DialogContent>
              <DialogContentText component="div">
                <TransRichText i18nKey="common:profile.groups.bioOntologies.allow.text" />{" "}
                <Box sx={{ fontSize: "8px" }}>{t("profile.groups.bioOntologies.allow.citation")}</Box>
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons onCancel={() => setEnableDialogOpen(false)} onConfirm={allowGroupBioOntologies} />
            </DialogActions>
          </Dialog>
        </>
      )}
      {loaded && bioOntologiesAllowedStatus && (
        <>
          <DisabledBioOntologiesButton mode="disallow" />
          <BioOntologiesButton mode="disallow" callback={() => setDisableDialogOpen(true)} />
          <Dialog open={disableDialogOpen} onClose={() => setDisableDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle id="group-sharing-dialog-title">
              {t("profile.groups.bioOntologies.disallow.title")}
            </DialogTitle>
            <DialogContent>
              <DialogContentText>
                <TransRichText i18nKey="common:profile.groups.bioOntologies.disallow.text" />
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons onCancel={() => setDisableDialogOpen(false)} onConfirm={disallowGroupBioOntologies} />
            </DialogActions>
          </Dialog>
        </>
      )}
    </ThemeProvider>
  );
}

export default GroupBioOntologiesManager;
