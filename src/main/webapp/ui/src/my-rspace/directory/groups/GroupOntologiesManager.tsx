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

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
declare const RS: any;
// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
declare const getValidationErrorString: (...args: any[]) => string;

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
function GroupOntologiesManager({ groupId, isCloud, canManageOntologies }: any) {
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
  }, [ontologiesEnforcedStatus]);

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
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  function DisabledOntologiesButton(props: any) {
    // biome-ignore lint/suspicious/noImplicitAnyLet: initial biome migration
    let title;
    if (isCloud) {
      title = "Only available on Enterprise";
    }

    return (
      <>
        {isCloud && canManageOntologies && (
          <Tooltip title={title} aria-label={title}>
            <div>
              <Button sx={{ margin: "0 0 0.5em 15px" }} variant="outlined" size="small" disabled>
                {props.mode} Ontologies
              </Button>
            </div>
          </Tooltip>
        )}
      </>
    );
  }

  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  function OntologiesButton(props: any) {
    return (
      <>
        {!isCloud && canManageOntologies && (
          // biome-ignore lint/complexity/noUselessFragments: initial biome migration
          <>
            <Button sx={{ margin: "0 0 0.5em 15px" }} onClick={props.callback} variant="outlined" size="small">
              {props.mode} Ontologies
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
    <ThemeProvider theme={materialTheme}>
      {loaded && !ontologiesEnforcedStatus && (
        <>
          <DisabledOntologiesButton mode="un-enforce" />
          <OntologiesButton mode="enforce" callback={() => setEnableDialogOpen(true)} />
          <Dialog open={enableDialogOpen} onClose={() => setEnableDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle id="group-publication-dialog-title">Enforce ontologies for tags</DialogTitle>
            <DialogContent>
              <DialogContentText>
                Enforcing ontologies for tags will result in all new tags coming from autopopulated values read from
                ontology files. These ontology files must be shared with a Group and any user creating new tags must be
                a member of that Group. Collaboration Groups can be used for this purpose. Tags can not be created from
                free text and will not autopopulate from existing tags.
              </DialogContentText>
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
            <DialogTitle id="group-sharing-dialog-title">Un-enforce use of ontologies for tags</DialogTitle>
            <DialogContent>
              <DialogContentText>
                Un-enforcing use of ontologies for tags will result in group members being able to enter free text when
                creating new tags. They may also use values autopopulated from ontology files. These ontology files may
                be their own, or may be files shared with them. They may also use values autopopulated from existing
                tags.
              </DialogContentText>
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
