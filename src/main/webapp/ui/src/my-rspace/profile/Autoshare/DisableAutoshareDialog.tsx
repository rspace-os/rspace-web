import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import { blue } from "@mui/material/colors";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import Switch, { switchClasses } from "@mui/material/Switch";
import { ThemeProvider } from "@mui/material/styles";
import Tooltip from "@mui/material/Tooltip";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React, { useContext } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import TransRichText from "@/modules/common/i18n/TransRichText";
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";
import { getErrorMessage } from "@/util/error";
import materialTheme from "../../../theme";

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
  const { addAlert } = useContext(AlertContext);

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
          addAlert(mkAlert({ message: msg2, variant: "warning", duration: 5000 }));
          return;
        }

        const async = response.data.data.async;
        const msg = async
          ? t("profile.groups.autosharing.disableStarted", { group: group.groupDisplayName })
          : t("profile.groups.autosharing.disableSuccess", { group: group.groupDisplayName });

        setDone(true);
        callback();
        addAlert(mkAlert({ message: msg, variant: "notice", duration: async ? 7000 : 3000 }));
      })
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      .catch((error: any) => {
        addAlert(
          mkAlert({
            message: getErrorMessage(error, t("profile.groups.autosharing.genericError")),
            variant: "warning",
            isInfinite: true,
          }),
        );
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
            <TransRichText
              i18nKey="common:profile.groups.autosharing.disableUserText"
              values={{ username, group: group.groupDisplayName }}
            />
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
