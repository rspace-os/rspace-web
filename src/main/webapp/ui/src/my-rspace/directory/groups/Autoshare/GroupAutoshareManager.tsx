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
import { Trans, useTranslation } from "react-i18next";
import axios from "@/common/axios";
import materialTheme from "../../../../theme";
import AdditionalInfo from "./AdditionalInfo";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const RS: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const getValidationErrorString: (...args: any[]) => string;

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
function GroupAutoshareManager({ groupId, groupDisplayName, isCloud, isLabGroup, isGroupAutoshareAllowed }: any) {
  const { t } = useTranslation("common");
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
      RS.confirm(t("profile.groups.autosharing.settingInProgress"), "notice", 5000);
    });
  }

  /* Autosharing is not supported on collaboration groups and the community version */
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function DisabledAutoshareButton(props: any) {
    // biome-ignore lint/suspicious/noImplicitAnyLet: initial biome migration
    let title;

    if (!isLabGroup) {
      title = t("profile.groups.autosharing.onlyLabGroups");
    } else if (isCloud) {
      title = t("profile.groups.manager.onlyEnterprise");
    } else if (!isGroupAutoshareAllowed) {
      title = t("profile.groups.manager.contactAdmin");
    }

    const label =
      props.mode === "enable"
        ? t("profile.groups.autosharing.enableGroup.button")
        : t("profile.groups.autosharing.disableGroup.button");

    return (
      <>
        {(!isLabGroup || isCloud || !isGroupAutoshareAllowed) && (
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
  function AutoshareButton(props: any) {
    const label =
      props.mode === "enable"
        ? t("profile.groups.autosharing.enableGroup.button")
        : t("profile.groups.autosharing.disableGroup.button");

    return (
      <>
        {isLabGroup && !isCloud && isGroupAutoshareAllowed && (
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
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        {loaded && !autoshareStatus && (
          <>
            <DisabledAutoshareButton mode="enable" />
            <AutoshareButton mode="enable" callback={() => setEnableDialogOpen(true)} />
            <Dialog open={enableDialogOpen} onClose={() => setEnableDialogOpen(false)} maxWidth="sm" fullWidth>
              <DialogTitle id="group-sharing-dialog-title">
                {t("profile.groups.autosharing.enableGroup.title")}
              </DialogTitle>
              <DialogContent>
                <DialogContentText>
                  <Trans
                    i18nKey="profile.groups.autosharing.enableGroup.text"
                    ns="common"
                    values={{ groupDisplayName }}
                  />
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
              <DialogTitle id="group-sharing-dialog-title">
                {t("profile.groups.autosharing.disableGroup.title")}
              </DialogTitle>
              <DialogContent>
                <DialogContentText>
                  <Trans
                    i18nKey="profile.groups.autosharing.disableGroup.text"
                    ns="common"
                    values={{ groupDisplayName }}
                  />
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

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function WrappedGroupAutoshareManager(props: any) {
  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <GroupAutoshareManager {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
