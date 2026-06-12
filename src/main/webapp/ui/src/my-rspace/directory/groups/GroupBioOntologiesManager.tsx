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
import React, { useEffect, useState } from "react";
import axios from "@/common/axios";
import materialTheme from "../../../theme";

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
declare const RS: any;
// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
declare const getValidationErrorString: (...args: any[]) => string;

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
function GroupBioOntologiesManager({ groupId, isCloud, canManageOntologies }: any) {
  const [bioOntologiesAllowedStatus, setBioOntologiesAllowedStatus] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [waiting, setWaiting] = useState(false);

  const [enableDialogOpen, setEnableDialogOpen] = React.useState(false);
  const [disableDialogOpen, setDisableDialogOpen] = React.useState(false);

  useEffect(() => {
    axios.get(`/groups/ajax/bioOntologiesAllowedStatus/${groupId}`).then((response) => {
      if (!response.data.success) {
        const msg = getValidationErrorString(response.data.error, ",", true);
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
      setBioOntologiesAllowedStatus(isEnabled);
      setEnableDialogOpen(false);
      setDisableDialogOpen(false);
    });
  }

  /* BioPortal Ontologies are not supported on the community version */
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  function DisabledBioOntologiesButton(props: any) {
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
  function BioOntologiesButton(props: any) {
    return (
      <>
        {!isCloud && canManageOntologies && (
          // biome-ignore lint/complexity/noUselessFragments: initial biome migration
          <>
            <Button sx={{ margin: "0 0 0.5em 15px" }} onClick={props.callback} variant="outlined" size="small">
              {props.mode} BioPortal Ontologies
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
      {loaded && !bioOntologiesAllowedStatus && (
        <>
          <DisabledBioOntologiesButton mode="disallow" />
          <BioOntologiesButton mode="allow" callback={() => setEnableDialogOpen(true)} />
          <Dialog open={enableDialogOpen} onClose={() => setEnableDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle id="group-publication-dialog-title">
              Allow BioPortal Ontologies to be used for tag suggestions
            </DialogTitle>
            <DialogContent>
              <DialogContentText>
                Allowing BioPortal Ontologies for tags will query data from the
                <a href={"https://bioportal.bioontology.org/ontologies"} target={"_blank"} rel="noreferrer">
                  {" "}
                  BioPortal Ontologies Portal
                </a>{" "}
                and use the values to generate tag suggestions{" "}
                <Box sx={{ fontSize: "8px" }}>
                  Whetzel PL, Noy NF, Shah NH, Alexander PR, Nyulas C, Tudorache T, Musen MA. BioPortal: enhanced
                  functionality via new Web services from the National Center for Biomedical Ontology to access and use
                  ontologies in software applications. Nucleic Acids Res. 2011 Jul;39(Web Server issue):W541-5. Epub
                  2011 Jun 14.
                </Box>
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
              Disallow BioPortal Ontologies to be used for tag suggestions
            </DialogTitle>
            <DialogContent>
              <DialogContentText>
                Disallowing use of BioPortal Ontologies will restrict tag suggestions: values from the
                <a href={"https://bioportal.bioontology.org/ontologies"} target={"_blank"} rel="noreferrer">
                  {" "}
                  BioPortal Ontologies Portal
                </a>{" "}
                will no longer be suggested.
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
