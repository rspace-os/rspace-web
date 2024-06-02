import React, { useState, useEffect } from "react";
import DialogContentText from "@mui/material/DialogContentText";
import { ThemeProvider } from "@mui/material/styles";
import Button from "@mui/material/Button";
import materialTheme from "../../../theme";
import { makeStyles } from "tss-react/mui";
import axios from "axios";
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

function GroupOntologiesManager({ groupId, isCloud, canManageOntologies }) {
  const { classes } = useStyles();
  const [ontologiesEnforcedStatus, setOntologiesEnforcedStatus] =
    useState(false);
  const [loaded, setLoaded] = useState(false);
  const [waiting, setWaiting] = useState(false);

  const [enableDialogOpen, setEnableDialogOpen] = React.useState(false);
  const [disableDialogOpen, setDisableDialogOpen] = React.useState(false);

  useEffect(() => {
    axios
      .get(`/groups/ajax/ontologiesEnforcedStatus/${groupId}`)
      .then((response) => {
        if (!response.data.success) {
          let msg = getValidationErrorString(response.data.error, ",", true);
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
      setOntologiesEnforcedStatus(isEnabled);
      setEnableDialogOpen(false);
      setDisableDialogOpen(false);
    });
  }

  /* Ontologies are not supported on the community version */
  function DisabledOntologiesButton(props) {
    let title;
    if (isCloud) {
      title = "Only available on Enterprise";
    }

    return (
      <>
        {isCloud && canManageOntologies && (
          <Tooltip title={title} aria-label={title}>
            <div>
              <Button
                className={classes.publishManager}
                variant="outlined"
                size="small"
                disabled
              >
                {props.mode} Ontologies
              </Button>
            </div>
          </Tooltip>
        )}
      </>
    );
  }

  function OntologiesButton(props) {
    return (
      <>
        {!isCloud && canManageOntologies && (
          <>
            <Button
              className={classes.publishManager}
              onClick={props.callback}
              variant="outlined"
              size="small"
            >
              {props.mode} Ontologies
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
      {loaded && !ontologiesEnforcedStatus && (
        <>
          <DisabledOntologiesButton mode="un-enforce" />
          <OntologiesButton
            mode="enforce"
            callback={() => setEnableDialogOpen(true)}
          />
          <Dialog
            open={enableDialogOpen}
            onClose={() => setEnableDialogOpen(false)}
            maxWidth="sm"
            fullWidth
          >
            <DialogTitle id="group-publication-dialog-title">
              Enforce ontologies for tags
            </DialogTitle>
            <DialogContent>
              <DialogContentText>
                Enforcing ontologies for tags will result in all new tags coming
                from autopopulated values read from ontology files. These
                ontology files must be shared with a Group and any user creating
                new tags must be a member of that Group. Collaboration Groups
                can be used for this purpose. Tags can not be created from free
                text and will not autopopulate from existing tags.
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons
                onCancel={() => setEnableDialogOpen(false)}
                onConfirm={enforceGroupOntologies}
              />
            </DialogActions>
          </Dialog>
        </>
      )}
      {loaded && ontologiesEnforcedStatus && (
        <>
          <DisabledOntologiesButton mode="disable" />
          <OntologiesButton
            mode="un-enforce"
            callback={() => setDisableDialogOpen(true)}
          />
          <Dialog
            open={disableDialogOpen}
            onClose={() => setDisableDialogOpen(false)}
            maxWidth="sm"
            fullWidth
          >
            <DialogTitle id="group-sharing-dialog-title">
              Un-enforce use of ontologies for tags
            </DialogTitle>
            <DialogContent>
              <DialogContentText>
                Un-enforcing use of ontologies for tags will result in group
                members being able to enter free text when creating new tags.
                They may also use values autopopulated from ontology files.
                These ontology files may be their own, or may be files shared
                with them. They may also use values autopopulated from existing
                tags.
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons
                onCancel={() => setDisableDialogOpen(false)}
                onConfirm={unenforceGroupOntologies}
              />
            </DialogActions>
          </Dialog>
        </>
      )}
    </ThemeProvider>
  );
}

export default GroupOntologiesManager;
