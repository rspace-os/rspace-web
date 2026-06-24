// biome-ignore lint/style/noRestrictedImports: initial biome migration
import { Switch, Tooltip } from "@mui/material";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import { blue } from "@mui/material/colors";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import { switchClasses } from "@mui/material/Switch";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import materialTheme from "../../../theme";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const RS: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare function getValidationErrorString(...args: any[]): string;

function DisableAutoshareDialog({
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

  const handleClickOpen = () => {
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
  };

  const handleSubmit = () => {
    setWaiting(true);

    const url = `/userform/ajax/disableAutoshare/${group.groupId}/${userId}`;

    axios
      .post(url)
      .then((response) => {
        if (!response.data.success) {
          const msg2 = getValidationErrorString(response.data.error, ",", true);
          RS.confirm(msg2, "warning", 5000, { sticky: true });
          return;
        }

        const async = response.data.data.async;
        const msg = async
          ? t("profile.groups.autosharing.disableStarted", { group: group.groupDisplayName })
          : t("profile.groups.autosharing.disableSuccess", { group: group.groupDisplayName });

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

  return (
    <>
      {!isSwitch && (
        <Button variant="outlined" size="small" onClick={handleClickOpen}>
          {t("profile.groups.autosharing.disable")}
        </Button>
      )}
      {isSwitch && (
        <Tooltip title={switchDisabledReason} aria-label={switchDisabledReason}>
          <div>
            <Switch
              color="primary"
              checked={true}
              disabled={isSwitchDisabled}
              onChange={handleClickOpen}
              slotProps={{ input: { "aria-label": t("profile.groups.autosharing.disable") } }}
              sx={{
                [`& .${switchClasses.switchBase}`]: {
                  color: blue[200],
                  [`&.${switchClasses.checked}`]: {
                    color: blue[400],
                  },
                  [`&.${switchClasses.checked} + .${switchClasses.track}`]: {
                    backgroundColor: blue[400],
                  },
                },
              }}
            />
          </div>
        </Tooltip>
      )}
      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <DialogTitle id="form-dialog-title">{t("profile.groups.autosharing.disable")}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Disabling autosharing will unshare all work for user <strong>{username}</strong> from group{" "}
            <strong>{group.groupDisplayName}</strong>.<br />
            <br />
            Individual documents and notebooks can still be shared as usual.
            <br />
          </DialogContentText>
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
export default function WrappedDisableAutoshareDialog(props: any) {
  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <DisableAutoshareDialog {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
