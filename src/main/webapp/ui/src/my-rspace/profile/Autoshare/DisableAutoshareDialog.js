"use strict";
import React from "react";
import { Switch, Tooltip } from "@mui/material";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../../../theme";
import Button from "@mui/material/Button";
import axios from "@/common/axios";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import CircularProgress from "@mui/material/CircularProgress";
import { blue } from "@mui/material/colors";

const useStyles = makeStyles()(() => ({
  loading: {
    position: "absolute",
    margin: "0 auto",
  },
}));

const BlueSwitch = withStyles({
  switchBase: {
    color: blue[200],
    "&$checked": {
      color: blue[400],
    },
    "&$checked + $track": {
      backgroundColor: blue[400],
    },
  },
  checked: {},
  track: {},
})(Switch);

function DisableAutoshareDialog({
  group,
  username,
  userId,
  callback,
  isSwitch,
  isSwitchDisabled,
  switchDisabledReason,
}) {
  const { classes } = useStyles();
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
          let msg2 = getValidationErrorString(response.data.error, ",", true);
          RS.confirm(msg2, "warning", 5000, { sticky: true });
          return;
        }

        let async = response.data.data.async;
        let msg = async
          ? `Reverting autoshare for ${group.groupDisplayName} started successfully. You will receive a notification once it is complete.`
          : `Autoshare for ${group.groupDisplayName} was disabled successfully.`;

        setDone(true);
        callback();
        RS.confirm(msg, "notice", async ? 7000 : 3000);
      })
      .catch((error) => {
        RS.confirm(
          error.response.data ||
            "Something went wrong. Please, contact support if the issue persists.",
          "warning",
          "infinite"
        );
      })
      .then(function () {
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
            <BlueSwitch
              color="primary"
              checked={true}
              disabled={isSwitchDisabled}
              onChange={handleClickOpen}
              inputProps={{ "aria-label": "Disable autosharing" }}
            />
          </div>
        </Tooltip>
      )}
      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <DialogTitle id="form-dialog-title">Disable autosharing</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Disabling autosharing will unshare all work for user
            <b> {username}</b> from group <b>{group.groupDisplayName}</b>.<br />
            <br />
            Individual documents and notebooks can still be shared as usual.
            <br />
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} style={{ color: "grey" }}>
            Cancel
          </Button>
          <Button
            onClick={handleSubmit}
            color="primary"
            disabled={waiting || done}
          >
            Confirm
            {waiting && (
              <CircularProgress size={20} className={classes.loading} />
            )}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

/*
 * This is necessary because as of MUI v5 useStyles cannot be used in the same
 * component as the root MuiThemeProvider
 */
export default function WrappedDisableAutoshareDialog(props) {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <DisableAutoshareDialog {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
