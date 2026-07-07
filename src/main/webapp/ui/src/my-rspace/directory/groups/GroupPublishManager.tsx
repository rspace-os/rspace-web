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
import axios from "@/common/axios";
import materialTheme from "../../../theme";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const RS: any;
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
  const [publishAllowedStatus, setPublishAllowedStatus] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [waiting, setWaiting] = useState(false);

  const [enableDialogOpen, setEnableDialogOpen] = React.useState(false);
  const [disableDialogOpen, setDisableDialogOpen] = React.useState(false);

  useEffect(() => {
    axios.get(`/groups/ajax/publishAllowedStatus/${groupId}`).then((response) => {
      if (!response.data.success) {
        const msg = getValidationErrorString(response.data.error, ",", true);
        RS.confirm(msg, "warning", 5000, { sticky: true });
        setWaiting(false);
        return;
      }

      setPublishAllowedStatus(response.data.data);
      setLoaded(true);
    });
  }, [publishAllowedStatus]);

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
        RS.confirm(msg, "warning", 5000, { sticky: true });
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
      title = `Not available for collaboration groups`;
    }
    if (isCloud) {
      title = "Only available on Enterprise";
    } else if (!isGroupPublicationAllowed) {
      title = "Please contact your system administrator to enable this feature";
    }

    return (
      <>
        {(!isLabGroup || isCloud || !isGroupPublicationAllowed) && canManagePublish && (
          <Tooltip title={title} aria-label={title}>
            <div>
              <Button sx={{ margin: "0 0 0.5em 15px" }} variant="outlined" size="small" disabled>
                {props.mode} publication
              </Button>
            </div>
          </Tooltip>
        )}
      </>
    );
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function PublicationButton(props: any) {
    return (
      <>
        {isLabGroup && !isCloud && isGroupPublicationAllowed && canManagePublish && (
          <Button sx={{ margin: "0 0 0.5em 15px" }} onClick={props.callback} variant="outlined" size="small">
            {props.mode} publication
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
          Cancel
        </Button>
        <Button onClick={props.onConfirm} color="primary" disabled={waiting}>
          Confirm
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
            <DialogTitle id="group-publication-dialog-title">Enable group publication</DialogTitle>
            <DialogContent>
              <DialogContentText>
                Enabling group-wide publication will allow non-PI members in the <strong>{groupDisplayName}</strong>{" "}
                group to publish and unpublish their own documents. PIs can also unpublish these documents.
              </DialogContentText>
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
            <DialogTitle id="group-sharing-dialog-title">Disable group-wide publication</DialogTitle>
            <DialogContent>
              <DialogContentText>
                Disabling group-wide publication will prevent non PI members of the <strong>{groupDisplayName}</strong>{" "}
                group publishing their own documents. They may still unpublish previously published documents.
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
