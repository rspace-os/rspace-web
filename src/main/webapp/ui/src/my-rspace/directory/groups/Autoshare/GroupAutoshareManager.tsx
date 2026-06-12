// biome-ignore lint/style/noRestrictedImports: initial biome migration
import { DialogContentText } from "@mui/material";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import { ThemeProvider } from "@mui/material/styles";
import Tooltip from "@mui/material/Tooltip";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React, { useEffect, useState } from "react";
import axios from "@/common/axios";
import materialTheme from "../../../../theme";
import AdditionalInfo from "./AdditionalInfo";

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
declare const RS: any;
// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
declare const getValidationErrorString: (...args: any[]) => string;

function GroupAutoshareManager({
  groupId,
  groupDisplayName,
  isCloud,
  isLabGroup,
  isGroupAutoshareAllowed,
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
}: any) {
  const [autoshareStatus, setAutoshareStatus] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [waiting, setWaiting] = useState(false);

  const [enableDialogOpen, setEnableDialogOpen] = React.useState(false);
  const [disableDialogOpen, setDisableDialogOpen] = React.useState(false);

  useEffect(() => {
    axios.get(`/groups/ajax/autoshareStatus/${groupId}`).then((response) => {
      if (!response.data.success) {
        const msg = getValidationErrorString(response.data.error, ",", true);
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

  function submit(url: string) {
    setWaiting(true);

    axios.post(url + groupId).then((response) => {
      if (!response.data.success) {
        const msg = getValidationErrorString(response.data.error, ",", true);
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
        5000,
      );
    });
  }

  /* Autosharing is not supported on collaboration groups and the community version */
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  function DisabledAutoshareButton(props: any) {
    // biome-ignore lint/suspicious/noImplicitAnyLet: initial biome migration
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
              <Button sx={{ margin: "0 0 0.5em 15px" }} variant="outlined" size="small" disabled>
                {props.mode} autosharing
              </Button>
            </div>
          </Tooltip>
        )}
      </>
    );
  }

  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  function AutoshareButton(props: any) {
    return (
      <>
        {isLabGroup && !isCloud && isGroupAutoshareAllowed && (
          // biome-ignore lint/complexity/noUselessFragments: initial biome migration
          <>
            <Button sx={{ margin: "0 0 0.5em 15px" }} onClick={props.callback} variant="outlined" size="small">
              {props.mode} autosharing
            </Button>
          </>
        )}
      </>
    );
  }

  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
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
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        {loaded && !autoshareStatus && (
          <>
            <DisabledAutoshareButton mode="enable" />
            <AutoshareButton mode="enable" callback={() => setEnableDialogOpen(true)} />
            <Dialog open={enableDialogOpen} onClose={() => setEnableDialogOpen(false)} maxWidth="sm" fullWidth>
              <DialogTitle id="group-sharing-dialog-title">Enable group-wide autosharing</DialogTitle>
              <DialogContent>
                <DialogContentText>
                  Enabling group-wide autosharing will enable autosharing for all non-PI members in the{" "}
                  <strong>{groupDisplayName}</strong> group. Once enabled, new non-PI members will have autosharing
                  automatically enabled on joining.
                </DialogContentText>
                <AdditionalInfo />
              </DialogContent>
              <DialogActions>
                <DialogButtons onCancel={() => setEnableDialogOpen(false)} onConfirm={enableAutoshare} />
              </DialogActions>
            </Dialog>
          </>
        )}
        {loaded && autoshareStatus && (
          <>
            <DisabledAutoshareButton mode="disable" />
            <AutoshareButton mode="disable" callback={() => setDisableDialogOpen(true)} />
            <Dialog open={disableDialogOpen} onClose={() => setDisableDialogOpen(false)} maxWidth="sm" fullWidth>
              <DialogTitle id="group-sharing-dialog-title">Disable group-wide autosharing</DialogTitle>
              <DialogContent>
                <DialogContentText>
                  Disabling group-wide autosharing will unshare all work shared by non-PI members in the{" "}
                  <strong>{groupDisplayName}</strong> group. Once disabled, new non-PI members will no longer have
                  autosharing automatically enabled on joining.
                </DialogContentText>
                <AdditionalInfo />
              </DialogContent>
              <DialogActions>
                <DialogButtons onCancel={() => setDisableDialogOpen(false)} onConfirm={disableAutoshare} />
              </DialogActions>
            </Dialog>
          </>
        )}
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
export default function WrappedGroupAutoshareManager(props: any) {
  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <GroupAutoshareManager {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
