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
import axios from "@/common/axios";
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
          ? `Reverting autoshare for ${group.groupDisplayName} started successfully. You will receive a notification once it is complete.`
          : `Autoshare for ${group.groupDisplayName} was disabled successfully.`;

        setDone(true);
        callback();
        addAlert(mkAlert({ message: msg, variant: "notice", duration: async ? 7000 : 3000 }));
      })
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      .catch((error: any) => {
        addAlert(
          mkAlert({
            message: getErrorMessage(error, "Something went wrong. Please, contact support if the issue persists."),
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
          Disable autosharing
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
              slotProps={{ input: { "aria-label": "Disable autosharing" } }}
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
        <DialogTitle id="form-dialog-title">Disable autosharing</DialogTitle>
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
            Cancel
          </Button>
          <Button onClick={handleSubmit} color="primary" disabled={waiting || done}>
            Confirm
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
