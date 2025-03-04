import React, { useState, useEffect } from "react";
import { DialogContentText } from "@mui/material";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import Button from "@mui/material/Button";
import materialTheme from "../../../../theme";
import AdditionalInfo from "./AdditionalInfo";
import { makeStyles } from "tss-react/mui";
import axios from "@/common/axios";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import CircularProgress from "@mui/material/CircularProgress";
import Tooltip from "@mui/material/Tooltip";

const useStyles = makeStyles()(() => ({
  autoshareManager: {
    margin: "0 0 0.5em 15px",
  },

  loading: {
    position: "absolute",
    margin: "0 auto",
  },
}));

function GroupAutoshareManager({
  groupId,
  groupDisplayName,
  isCloud,
  isLabGroup,
  isGroupAutoshareAllowed,
}) {
  const { classes } = useStyles();
  const [autoshareStatus, setAutoshareStatus] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [waiting, setWaiting] = useState(false);

  const [enableDialogOpen, setEnableDialogOpen] = React.useState(false);
  const [disableDialogOpen, setDisableDialogOpen] = React.useState(false);

  useEffect(() => {
    axios.get(`/groups/ajax/autoshareStatus/${groupId}`).then((response) => {
      if (!response.data.success) {
        let msg = getValidationErrorString(response.data.error, ",", true);
        RS.confirm(msg, "warning", 5000, { sticky: true });
        return;
      }

      setAutoshareStatus(response.data.data);
      setLoaded(true);
    });
  });

  function enableAutoshare() {
    submit("/groups/ajax/enableAutoshare/");
  }

  function disableAutoshare() {
    submit("/groups/ajax/disableAutoshare/");
  }

  function submit(url) {
    setWaiting(true);

    axios.post(url + groupId).then((response) => {
      if (!response.data.success) {
        let msg = getValidationErrorString(response.data.error, ",", true);
        RS.confirm(msg, "warning", 5000, { sticky: true });
        return;
      }

      setWaiting(false);
      setAutoshareStatus(true);
      setEnableDialogOpen(false);
      setDisableDialogOpen(false);
      RS.confirm(
        "Please allow some time for the setting to take into-effect. You will receive a notification when it is complete",
        "notice",
        5000
      );
    });
  }

  /* Autosharing is not supported on collaboration groups and the community version */
  function DisabledAutoshareButton(props) {
    let title;

    if (!isLabGroup) {
      title = `Can only ${props.mode} autoshare for lab groups`;
    } else if (isCloud) {
      title = "Only available on Enterprise";
    } else if (!isGroupAutoshareAllowed) {
      title = "Please contact your system administrator to enable this feature";
    }

    return (
      <>
        {(!isLabGroup || isCloud || !isGroupAutoshareAllowed) && (
          <Tooltip title={title} aria-label={title}>
            <div>
              <Button
                className={classes.autoshareManager}
                variant="outlined"
                size="small"
                disabled
              >
                {props.mode} autosharing
              </Button>
            </div>
          </Tooltip>
        )}
      </>
    );
  }

  function AutoshareButton(props) {
    return (
      <>
        {isLabGroup && !isCloud && isGroupAutoshareAllowed && (
          <>
            <Button
              className={classes.autoshareManager}
              onClick={props.callback}
              variant="outlined"
              size="small"
            >
              {props.mode} autosharing
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
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        {loaded && !autoshareStatus && (
          <>
            <DisabledAutoshareButton mode="enable" />
            <AutoshareButton
              mode="enable"
              callback={() => setEnableDialogOpen(true)}
            />
            <Dialog
              open={enableDialogOpen}
              onClose={() => setEnableDialogOpen(false)}
              maxWidth="sm"
              fullWidth
            >
              <DialogTitle id="group-sharing-dialog-title">
                Enable group-wide autosharing
              </DialogTitle>
              <DialogContent>
                <DialogContentText>
                  Enabling group-wide autosharing will enable autosharing for
                  all non-PI members in the <b>{groupDisplayName}</b> group.
                  Once enabled, new non-PI members will have autosharing
                  automatically enabled on joining.
                </DialogContentText>
                <AdditionalInfo />
              </DialogContent>
              <DialogActions>
                <DialogButtons
                  onCancel={() => setEnableDialogOpen(false)}
                  onConfirm={enableAutoshare}
                />
              </DialogActions>
            </Dialog>
          </>
        )}
        {loaded && autoshareStatus && (
          <>
            <DisabledAutoshareButton mode="disable" />
            <AutoshareButton
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
                Disable group-wide autosharing
              </DialogTitle>
              <DialogContent>
                <DialogContentText>
                  Disabling group-wide autosharing will unshare all work shared
                  by non-PI members in the <b>{groupDisplayName}</b> group. Once
                  disabled, new non-PI members will no longer have autosharing
                  automatically enabled on joining.
                </DialogContentText>
                <AdditionalInfo />
              </DialogContent>
              <DialogActions>
                <DialogButtons
                  onCancel={() => setDisableDialogOpen(false)}
                  onConfirm={disableAutoshare}
                />
              </DialogActions>
            </Dialog>
          </>
        )}
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

/*
 * This is necessary because as of MUI v5 useStyles cannot be used in the same
 * component as the root MuiThemeProvider
 */
export default function WrappedGroupAutoshareManager(props) {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <GroupAutoshareManager {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
