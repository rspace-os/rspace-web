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
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";
import materialTheme from "../../../theme";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const getValidationErrorString: (...args: any[]) => string;

function GroupPublishManager({
  groupId,
  groupDisplayName,
  isCloud,
  isLabGroup,
  isGroupPublicationAllowed,
  canManagePublish,
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
}: any) {
  const { t } = useTranslation("common");
  const [publishAllowedStatus, setPublishAllowedStatus] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [waiting, setWaiting] = useState(false);

  const [enableDialogOpen, setEnableDialogOpen] = React.useState(false);
  const [disableDialogOpen, setDisableDialogOpen] = React.useState(false);
  const { addAlert } = useContext(AlertContext);

  useEffect(() => {
    axios.get(`/groups/ajax/publishAllowedStatus/${groupId}`).then((response) => {
      if (!response.data.success) {
        const msg = getValidationErrorString(response.data.error, ",", true);
        addAlert(mkAlert({ message: msg, variant: "warning", duration: 5000 }));
        setWaiting(false);
        return;
      }

      setPublishAllowedStatus(response.data.data);
      setLoaded(true);
    });
  }, [groupId]);

  function allowGroupPublications() {
    submit("/groups/ajax/allowGroupPublications/", true);
  }

  function disableGroupPublications() {
    submit("/groups/ajax/disableGroupPublications/", false);
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
      setPublishAllowedStatus(isEnabled);
      setEnableDialogOpen(false);
      setDisableDialogOpen(false);
    });
  }

  /* Publication is not supported on collaboration groups and the community version */
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function DisabledPublicationButton(props: any) {
    // biome-ignore lint/suspicious/noImplicitAnyLet: initial biome migration
    let title;
    if (!isLabGroup) {
      title = t("profile.groups.manager.notAvailableForCollaboration");
    }
    if (isCloud) {
      title = t("profile.groups.manager.onlyEnterprise");
    } else if (!isGroupPublicationAllowed) {
      title = t("profile.groups.manager.contactAdmin");
    }

    const label =
      props.mode === "enable"
        ? t("profile.groups.publication.enable.button")
        : t("profile.groups.publication.disable.button");

    return (
      <>
        {(!isLabGroup || isCloud || !isGroupPublicationAllowed) && canManagePublish && (
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
  function PublicationButton(props: any) {
    const label =
      props.mode === "enable"
        ? t("profile.groups.publication.enable.button")
        : t("profile.groups.publication.disable.button");
    return (
      <>
        {isLabGroup && !isCloud && isGroupPublicationAllowed && canManagePublish && (
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
      {loaded && !publishAllowedStatus && (
        <>
          <DisabledPublicationButton mode="enable" />
          <PublicationButton mode="enable" callback={() => setEnableDialogOpen(true)} />
          <Dialog open={enableDialogOpen} onClose={() => setEnableDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle id="group-publication-dialog-title">
              {t("profile.groups.publication.enable.title")}
            </DialogTitle>
            <DialogContent>
              <DialogContentText>{t("profile.groups.publication.enable.text", { groupDisplayName })}</DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons onCancel={() => setEnableDialogOpen(false)} onConfirm={allowGroupPublications} />
            </DialogActions>
          </Dialog>
        </>
      )}
      {loaded && publishAllowedStatus && (
        <>
          <DisabledPublicationButton mode="disable" />
          <PublicationButton mode="disable" callback={() => setDisableDialogOpen(true)} />
          <Dialog open={disableDialogOpen} onClose={() => setDisableDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle id="group-sharing-dialog-title">{t("profile.groups.publication.disable.title")}</DialogTitle>
            <DialogContent>
              <DialogContentText>
                {t("profile.groups.publication.disable.text", { groupDisplayName })}
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons onCancel={() => setDisableDialogOpen(false)} onConfirm={disableGroupPublications} />
            </DialogActions>
          </Dialog>
        </>
      )}
    </ThemeProvider>
  );
}

export default GroupPublishManager;
