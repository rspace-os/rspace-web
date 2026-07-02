// biome-ignore lint/style/noRestrictedImports: initial biome migration
import { Switch, Tooltip } from "@mui/material";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import { switchClasses } from "@mui/material/Switch";
import { ThemeProvider } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import TransRichText from "@/modules/common/i18n/TransRichText";
import materialTheme from "../../../theme";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const RS: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare function getValidationErrorString(...args: any[]): string;

function EnableAutoshareDialog({
  group,
  username,
  userId,
  callback,
  isSwitch,
  isSwitchDisabled,
  switchDisabledReason,
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
}: any) {
  const { t } = useTranslation("common");
  const [open, setOpen] = React.useState(false);
  const [waiting, setWaiting] = React.useState(false);
  const [done, setDone] = React.useState(false);
  const [folderName, setFolderName] = React.useState("");

  const handleClickOpen = () => {
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
  };

  const handleSubmit = () => {
    setWaiting(true);

    const url = `/userform/ajax/enableAutoshare/${group.groupId}/${userId}`;
    const params =
      folderName !== ""
        ? {
            autoshareFolderName: folderName,
          }
        : {};

    axios
      .post(url, params)
      .then((response) => {
        if (!response.data.success) {
          const msg2 = getValidationErrorString(response.data.error, ",", true);
          RS.confirm(msg2, "warning", 5000, { sticky: true });
          return;
        }
        const async = response.data.data.async;
        const msg = async
          ? t("profile.groups.autosharing.enableAsyncSuccess", { group: group.groupDisplayName })
          : t("profile.groups.autosharing.enableSuccess", { group: group.groupDisplayName });

        setDone(true);
        callback();
        RS.confirm(msg, "notice", async ? 7000 : 3000);
      })
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      .catch((error: any) => {
        RS.confirm(error.response.data || t("profile.groups.autosharing.genericError"), "warning", "infinite");
      })
      .then(() => {
        setWaiting(false);
      });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const handleChange = (e: any) => {
    setFolderName(e.target.value);
  };

  return (
    <>
      {!isSwitch && (
        <Button variant="outlined" size="small" onClick={handleClickOpen}>
          {t("profile.groups.autosharing.enable")}
        </Button>
      )}
      {isSwitch && (
        <Tooltip title={switchDisabledReason} aria-label={switchDisabledReason}>
          <div>
            <Switch
              sx={{ [`& .${switchClasses.switchBase}`]: { color: "#dddddd" } }}
              color="primary"
              checked={false}
              disabled={isSwitchDisabled}
              onChange={handleClickOpen}
              slotProps={{ input: { "aria-label": t("profile.groups.autosharing.enable") } }}
            />
          </div>
        </Tooltip>
      )}
      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <DialogTitle id="form-dialog-title">{t("profile.groups.autosharing.enable")}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            <TransRichText
              i18nKey="common:profile.groups.autosharing.enableUserText"
              values={{ username, group: group.groupDisplayName }}
            />
          </DialogContentText>
          <TextField
            variant="standard"
            label={t("profile.groups.autosharing.folderName")}
            placeholder={username}
            autoFocus={true}
            fullWidth
            size="small"
            onChange={handleChange}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} sx={{ color: "grey" }}>
            {t("actions.cancel")}
          </Button>
          <Button onClick={handleSubmit} color="primary" disabled={waiting || done}>
            {t("actions.confirm")}
            {waiting && <CircularProgress size={20} sx={{ position: "absolute", margin: "0 auto" }} />}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function WrappedEnableAutosharingDialog(props: any) {
  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <EnableAutoshareDialog {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
