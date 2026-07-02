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
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import materialTheme from "../../../theme";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const RS: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const getValidationErrorString: (...args: any[]) => string;

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
function GroupSeoManager({ groupId, groupDisplayName, isCloud, isLabGroup, isGroupSeoAllowed, canManagePublish }: any) {
  const { t } = useTranslation("common");
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
  }, [groupId]);

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
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function DisabledSEOButton(props: any) {
    // biome-ignore lint/suspicious/noImplicitAnyLet: initial biome migration
    let title;
    if (!isLabGroup) {
      title = t("profile.groups.manager.notAvailableForCollaboration");
    }
    if (isCloud) {
      title = t("profile.groups.manager.onlyEnterprise");
    } else if (!isGroupSeoAllowed) {
      title = t("profile.groups.manager.contactAdmin");
    }

    const label =
      props.mode === "enable" ? t("profile.groups.seo.enable.button") : t("profile.groups.seo.disable.button");

    return (
      <>
        {(!isLabGroup || isCloud || !isGroupSeoAllowed) && canManagePublish && (
          <Tooltip title={title} aria-label={title}>
            <div>
              <Button sx={{ margin: "0 0 0.5em 15px" }} variant="outlined" size="small" disabled>
                {label}
              </Button>
            </div>
          </Tooltip>
        )}
      </>
    );
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function SEOButton(props: any) {
    const label =
      props.mode === "enable" ? t("profile.groups.seo.enable.button") : t("profile.groups.seo.disable.button");
    return (
      <>
        {isLabGroup && !isCloud && isGroupSeoAllowed && canManagePublish && (
          <Button sx={{ margin: "0 0 0.5em 15px" }} onClick={props.callback} variant="outlined" size="small">
            {label}
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
          {t("profile.groups.manager.cancel")}
        </Button>
        <Button onClick={props.onConfirm} color="primary" disabled={waiting}>
          {t("profile.groups.manager.confirm")}
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
            <DialogTitle id="group-publication-dialog-title">{t("profile.groups.seo.enable.title")}</DialogTitle>
            <DialogContent>
              <DialogContentText>{t("profile.groups.seo.enable.text", { groupDisplayName })}</DialogContentText>
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
            <DialogTitle id="group-sharing-dialog-title">{t("profile.groups.seo.disable.title")}</DialogTitle>
            <DialogContent>
              <DialogContentText>{t("profile.groups.seo.disable.text", { groupDisplayName })}</DialogContentText>
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
