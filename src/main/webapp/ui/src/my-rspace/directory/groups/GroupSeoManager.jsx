import React, { useState, useEffect } from "react";
import { DialogContentText } from "@mui/material";
import Button from "@mui/material/Button";
import materialTheme from "../../../theme";
import axios from "@/common/axios";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import CircularProgress from "@mui/material/CircularProgress";
import Tooltip from "@mui/material/Tooltip";
import { ThemeProvider } from "@mui/material/styles";

function GroupSeoManager({
  groupId,
  groupDisplayName,
  isCloud,
  isLabGroup,
  isGroupSeoAllowed,
  canManagePublish,
}) {
  const [seoAllowedStatus, setSeoAllowedStatus] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [waiting, setWaiting] = useState(false);

  const [enableDialogOpen, setEnableDialogOpen] = React.useState(false);
  const [disableDialogOpen, setDisableDialogOpen] = React.useState(false);

  useEffect(() => {
    axios.get(`/groups/ajax/seoAllowedStatus/${groupId}`).then((response) => {
      if (!response.data.success) {
        const msg = getValidationErrorString(response.data.error, ",", true);
        RS.confirm(msg, "warning", 5000, { sticky: true });
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

  function submit(url, isEnabled) {
    setWaiting(true);

    axios.post(url + groupId).then((response) => {
      if (!response.data.success) {
        const msg = getValidationErrorString(response.data.error, ",", true);
        RS.confirm(msg, "warning", 5000, { sticky: true });
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
  function DisabledSEOButton(props) {
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
              <Button
                sx={{ margin: "0 0 0.5em 15px" }}
                variant="outlined"
                size="small"
                disabled
              >
                {props.mode} published seo
              </Button>
            </div>
          </Tooltip>
        )}
      </>
    );
  }

  function SEOButton(props) {
    return (
      <>
        {isLabGroup && !isCloud && isGroupSeoAllowed && canManagePublish && (
          <>
            <Button
              sx={{ margin: "0 0 0.5em 15px" }}
              onClick={props.callback}
              variant="outlined"
              size="small"
            >
              {props.mode} published seo
            </Button>
          </>
        )}
      </>
    );
  }

  function DialogButtons(props) {
    return (
      <>
        <Button onClick={props.onCancel} sx={{ color: "grey" }}>
          Cancel
        </Button>
        <Button onClick={props.onConfirm} color="primary" disabled={waiting}>
          Confirm
          {waiting && (
            <CircularProgress
              size={20}
              sx={{ position: "absolute", margin: "0 auto" }}
            />
          )}
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
          <Dialog
            open={enableDialogOpen}
            onClose={() => setEnableDialogOpen(false)}
            maxWidth="sm"
            fullWidth
          >
            <DialogTitle id="group-publication-dialog-title">
              Enable group-wide SEO for published documents
            </DialogTitle>
            <DialogContent>
              <DialogContentText>
                Enabling group-wide SEO for published documents will allow
                non-PI members in the <strong>{groupDisplayName}</strong> group
                to choose to have their documents indexed by SEO bots. These
                documents will also be shown on the 'Published' page visible to
                the public.
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons
                onCancel={() => setEnableDialogOpen(false)}
                onConfirm={allowGroupSeo}
              />
            </DialogActions>
          </Dialog>
        </>
      )}
      {loaded && seoAllowedStatus && (
        <>
          <DisabledSEOButton mode="disable" />
          <SEOButton
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
              Disable group-wide SEO of public documents
            </DialogTitle>
            <DialogContent>
              <DialogContentText>
                Disabling group-wide SEO of public documents will prevent non PI
                members of the <strong>{groupDisplayName}</strong> group from
                allowing SEO bots to index their documents. Their published
                documents will not appear on the 'Published' page visible to the
                public.
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <DialogButtons
                onCancel={() => setDisableDialogOpen(false)}
                onConfirm={disableGroupSeo}
              />
            </DialogActions>
          </Dialog>
        </>
      )}
    </ThemeProvider>
  );
}

export default GroupSeoManager;
