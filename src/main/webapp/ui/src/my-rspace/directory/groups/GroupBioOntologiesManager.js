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

  smallText: {
    fontSize: "8px",
  },

  loading: {
    position: "absolute",
    margin: "0 auto",
  },
}));

function GroupBioOntologiesManager({ groupId, isCloud, canManageOntologies }) {
  const { classes } = useStyles();
  const [bioOntologiesAllowedStatus, setBioOntologiesAllowedStatus] =
    useState(false);
  const [loaded, setLoaded] = useState(false);
  const [waiting, setWaiting] = useState(false);

  const [enableDialogOpen, setEnableDialogOpen] = React.useState(false);
  const [disableDialogOpen, setDisableDialogOpen] = React.useState(false);

  useEffect(() => {
    axios
      .get(`/groups/ajax/bioOntologiesAllowedStatus/${groupId}`)
      .then((response) => {
        if (!response.data.success) {
          let msg = getValidationErrorString(response.data.error, ",", true);
          RS.confirm(msg, "warning", 5000, { sticky: true });
          setWaiting(false);
          return;
        }

        setBioOntologiesAllowedStatus(response.data.data);
        setLoaded(true);
      });
  }, [bioOntologiesAllowedStatus]);

  function allowGroupBioOntologies() {
    submit("/groups/ajax/allowBioOntologies/", true);
  }

  function disallowGroupBioOntologies() {
    submit("/groups/ajax/disallowBioOntologies/", false);
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
      setBioOntologiesAllowedStatus(isEnabled);
      setEnableDialogOpen(false);
      setDisableDialogOpen(false);
    });
  }

  /* BioPortal Ontologies are not supported on the community version */
  function DisabledBioOntologiesButton(props) {
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

  function BioOntologiesButton(props) {
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
              {props.mode} BioPortal Ontologies
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
      {loaded && !bioOntologiesAllowedStatus && (
        <>
          <DisabledBioOntologiesButton mode="disallow" />
          <BioOntologiesButton
            mode="allow"
            callback={() => setEnableDialogOpen(true)}
          />
          <Dialog
            open={enableDialogOpen}
            onClose={() => setEnableDialogOpen(false)}
            maxWidth="sm"
            fullWidth
          >
            <DialogTitle id="group-publication-dialog-title">
              Allow BioPortal Ontologies to be used for tag suggestions
            </DialogTitle>
            <DialogContent>
              <DialogContentText>
                Allowing BioPortal Ontologies for tags will query data from the
                <a
                  href={"https://bioportal.bioontology.org/ontologies"}
                  target={"_blank"}
                  rel="noreferrer"
                >
                  {" "}
                  BioPortal Ontologies Portal
                </a>{" "}
                and use the values to generate tag suggestions{" "}
                <div className={classes.smallText}>
                  Whetzel PL, Noy NF, Shah NH, Alexander PR, Nyulas C, Tudorache
                  T, Musen MA. BioPortal: enhanced functionality via new Web
                  services from the National Center for Biomedical Ontology to
                  access and use ontologies in software applications. Nucleic
                  Acids Res. 2011 Jul;39(Web Server issue):W541-5. Epub 2011 Jun
                  14.
                </div>
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons
                onCancel={() => setEnableDialogOpen(false)}
                onConfirm={allowGroupBioOntologies}
              />
            </DialogActions>
          </Dialog>
        </>
      )}
      {loaded && bioOntologiesAllowedStatus && (
        <>
          <DisabledBioOntologiesButton mode="disallow" />
          <BioOntologiesButton
            mode="disallow"
            callback={() => setDisableDialogOpen(true)}
          />
          <Dialog
            open={disableDialogOpen}
            onClose={() => setDisableDialogOpen(false)}
            maxWidth="sm"
            fullWidth
          >
            <DialogTitle id="group-sharing-dialog-title">
              Disallow BioPortal Ontologies to be used for tag suggestions
            </DialogTitle>
            <DialogContent>
              <DialogContentText>
                Disallowing use of BioPortal Ontologies will restrict tag
                suggestions: values from the
                <a
                  href={"https://bioportal.bioontology.org/ontologies"}
                  target={"_blank"}
                  rel="noreferrer"
                >
                  {" "}
                  BioPortal Ontologies Portal
                </a>{" "}
                will no longer be suggested.
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons
                onCancel={() => setDisableDialogOpen(false)}
                onConfirm={disallowGroupBioOntologies}
              />
            </DialogActions>
          </Dialog>
        </>
      )}
    </ThemeProvider>
  );
}

export default GroupBioOntologiesManager;
