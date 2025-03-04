import React, { useState, useEffect } from "react";
import { DialogContentText } from "@mui/material";
import { ThemeProvider } from "@mui/material/styles";
import Button from "@mui/material/Button";
import materialTheme from "../../../theme";
import AdditionalInfo from "./Autoshare/AdditionalInfo";
import { makeStyles } from "tss-react/mui";
import axios from "@/common/axios";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import CircularProgress from "@mui/material/CircularProgress";
import Tooltip from "@mui/material/Tooltip";

const useStyles = makeStyles()(() => ({
  publishManager: {
    margin: "0 0 0.5em 15px",
  },

  loading: {
    position: "absolute",
    margin: "0 auto",
  },
}));

function GroupPublishManager({
  groupId,
  groupDisplayName,
  isCloud,
  isLabGroup,
  isGroupPublicationAllowed,
  canManagePublish,
}) {
  const { classes } = useStyles();
  const [publishAllowedStatus, setPublishAllowedStatus] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [waiting, setWaiting] = useState(false);

  const [enableDialogOpen, setEnableDialogOpen] = React.useState(false);
  const [disableDialogOpen, setDisableDialogOpen] = React.useState(false);

  useEffect(() => {
    axios
      .get(`/groups/ajax/publishAllowedStatus/${groupId}`)
      .then((response) => {
        if (!response.data.success) {
          let msg = getValidationErrorString(response.data.error, ",", true);
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

  function submit(url, isEnabled) {
    setWaiting(true);

    axios.post(url + groupId).then((response) => {
      if (!response.data.success) {
        let msg = getValidationErrorString(response.data.error, ",", true);
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
  function DisabledPublicationButton(props) {
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
        {(!isLabGroup || isCloud || !isGroupPublicationAllowed) &&
          canManagePublish && (
            <Tooltip title={title} aria-label={title}>
              <div>
                <Button
                  className={classes.publishManager}
                  variant="outlined"
                  size="small"
                  disabled
                >
                  {props.mode} publication
                </Button>
              </div>
            </Tooltip>
          )}
      </>
    );
  }

  function PublicationButton(props) {
    return (
      <>
        {isLabGroup &&
          !isCloud &&
          isGroupPublicationAllowed &&
          canManagePublish && (
            <>
              <Button
                className={classes.publishManager}
                onClick={props.callback}
                variant="outlined"
                size="small"
              >
                {props.mode} publication
              </Button>
            </>
          )}
      </>
    );
  }

  function DialogButtons(props) {
    return (
      <>
        <Button onClick={props.onCancel} style={{ color: "grey" }}>
          Cancel
        </Button>
        <Button onClick={props.onConfirm} color="primary" disabled={waiting}>
          Confirm
          {waiting && (
            <CircularProgress size={20} className={classes.loading} />
          )}
        </Button>
      </>
    );
  }

  return (
    <ThemeProvider theme={materialTheme}>
      {loaded && !publishAllowedStatus && (
        <>
          <DisabledPublicationButton mode="enable" />
          <PublicationButton
            mode="enable"
            callback={() => setEnableDialogOpen(true)}
          />
          <Dialog
            open={enableDialogOpen}
            onClose={() => setEnableDialogOpen(false)}
            maxWidth="sm"
            fullWidth
          >
            <DialogTitle id="group-publication-dialog-title">
              Enable group publication
            </DialogTitle>
            <DialogContent>
              <DialogContentText>
                Enabling group-wide publication will allow non-PI members in the{" "}
                <b>{groupDisplayName}</b> group to publish and unpublish their
                own documents. PIs can also unpublish these documents.
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons
                onCancel={() => setEnableDialogOpen(false)}
                onConfirm={allowGroupPublications}
              />
            </DialogActions>
          </Dialog>
        </>
      )}
      {loaded && publishAllowedStatus && (
        <>
          <DisabledPublicationButton mode="disable" />
          <PublicationButton
            mode="disable"
            callback={() => setDisableDialogOpen(true)}
          />
          <Dialog
            open={disableDialogOpen}
            onClose={() => setDisableDialogOpen(false)}
            maxWidth="sm"
            fullWidth
          >
            <DialogTitle id="group-sharing-dialog-title">
              Disable group-wide publication
            </DialogTitle>
            <DialogContent>
              <DialogContentText>
                Disabling group-wide publication will prevent non PI members of
                the <b>{groupDisplayName}</b> group publishing their own
                documents. They may still unpublish previously published
                documents.
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons
                onCancel={() => setDisableDialogOpen(false)}
                onConfirm={disableGroupPublications}
              />
            </DialogActions>
          </Dialog>
        </>
      )}
    </ThemeProvider>
  );
}

export default GroupPublishManager;
