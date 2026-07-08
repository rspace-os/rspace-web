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
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";
import materialTheme from "../../../theme";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const getValidationErrorString: (...args: any[]) => string;

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
function GroupSeoManager({ groupId, groupDisplayName, isCloud, isLabGroup, isGroupSeoAllowed, canManagePublish }: any) {
  const [seoAllowedStatus, setSeoAllowedStatus] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [waiting, setWaiting] = useState(false);

  const [enableDialogOpen, setEnableDialogOpen] = React.useState(false);
  const [disableDialogOpen, setDisableDialogOpen] = React.useState(false);
  const { addAlert } = useContext(AlertContext);

  useEffect(() => {
    axios.get(`/groups/ajax/seoAllowedStatus/${groupId}`).then((response) => {
      if (!response.data.success) {
        const msg = getValidationErrorString(response.data.error, ",", true);
        addAlert(mkAlert({ message: msg, variant: "warning", duration: 5000 }));
        setWaiting(false);
        return;
      }

      setSeoAllowedStatus(response.data.data);
      setLoaded(true);
    });
  }, [seoAllowedStatus]);

  function allowGroupSeo() {
    submit("/groups/ajax/allowGroupSeo/", true);
  }

  function disableGroupSeo() {
    submit("/groups/ajax/disableGroupSeo/", false);
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
      setSeoAllowedStatus(isEnabled);
      setEnableDialogOpen(false);
      setDisableDialogOpen(false);
    });
  }

  /* Publication is not supported on collaboration groups and the community version */
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function DisabledSEOButton(props: any) {
    // biome-ignore lint/suspicious/noImplicitAnyLet: initial biome migration
    let title;
    if (!isLabGroup) {
      title = `Not available for collaboration groups`;
    }
    if (isCloud) {
      title = "Only available on Enterprise";
    } else if (!isGroupSeoAllowed) {
      title = "Please contact your system administrator to enable this feature";
    }

    return (
      <>
        {(!isLabGroup || isCloud || !isGroupSeoAllowed) && canManagePublish && (
          <Tooltip title={title} aria-label={title}>
            <div>
              <Button sx={{ margin: "0 0 0.5em 15px" }} variant="outlined" size="small" disabled>
                {props.mode} published seo
              </Button>
            </div>
          </Tooltip>
        )}
      </>
    );
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function SEOButton(props: any) {
    return (
      <>
        {isLabGroup && !isCloud && isGroupSeoAllowed && canManagePublish && (
          <Button sx={{ margin: "0 0 0.5em 15px" }} onClick={props.callback} variant="outlined" size="small">
            {props.mode} published seo
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
      {loaded && !seoAllowedStatus && (
        <>
          <DisabledSEOButton mode="enable" />
          <SEOButton mode="enable" callback={() => setEnableDialogOpen(true)} />
          <Dialog open={enableDialogOpen} onClose={() => setEnableDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle id="group-publication-dialog-title">Enable group-wide SEO for published documents</DialogTitle>
            <DialogContent>
              <DialogContentText>
                Enabling group-wide SEO for published documents will allow non-PI members in the{" "}
                <strong>{groupDisplayName}</strong> group to choose to have their documents indexed by SEO bots. These
                documents will also be shown on the 'Published' page visible to the public.
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons onCancel={() => setEnableDialogOpen(false)} onConfirm={allowGroupSeo} />
            </DialogActions>
          </Dialog>
        </>
      )}
      {loaded && seoAllowedStatus && (
        <>
          <DisabledSEOButton mode="disable" />
          <SEOButton mode="disable" callback={() => setDisableDialogOpen(true)} />
          <Dialog open={disableDialogOpen} onClose={() => setDisableDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle id="group-sharing-dialog-title">Disable group-wide SEO of public documents</DialogTitle>
            <DialogContent>
              <DialogContentText>
                Disabling group-wide SEO of public documents will prevent non PI members of the{" "}
                <strong>{groupDisplayName}</strong> group from allowing SEO bots to index their documents. Their
                published documents will not appear on the 'Published' page visible to the public.
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons onCancel={() => setDisableDialogOpen(false)} onConfirm={disableGroupSeo} />
            </DialogActions>
          </Dialog>
        </>
      )}
    </ThemeProvider>
  );
}

export default GroupSeoManager;
