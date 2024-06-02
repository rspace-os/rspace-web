"use strict";
import React from "react";
import { Switch, Tooltip } from "@mui/material";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../../../theme";
import Button from "@mui/material/Button";
import axios from "axios";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import TextField from "@mui/material/TextField";
import CircularProgress from "@mui/material/CircularProgress";

const useStyles = makeStyles()(() => ({
  loading: {
    position: "absolute",
    margin: "0 auto",
  },
}));

const GreySwitch = withStyles({
  switchBase: {
    color: "#dddddd",
  },
})(Switch);

function EnableAutoshareDialog({
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
    let params =
      folderName !== ""
        ? {
            autoshareFolderName: folderName,
          }
        : {};

    axios
      .post(url, params)
      .then((response) => {
        if (!response.data.success) {
          let msg2 = getValidationErrorString(response.data.error, ",", true);
          RS.confirm(msg2, "warning", 5000, { sticky: true });
          return;
        }
        let async = response.data.data.async;
        let msg = async
          ? `Autoshare for ${group.groupDisplayName} was enabled successfully. You will receive a notification once it is complete.`
          : `Autoshare for ${group.groupDisplayName} was enabled successfully.`;

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

  const handleChange = (e) => {
    setFolderName(e.target.value);
  };

  return (
    <>
      {!isSwitch && (
        <Button variant="outlined" size="small" onClick={handleClickOpen}>
          Enable autosharing
        </Button>
      )}
      {isSwitch && (
        <Tooltip title={switchDisabledReason} aria-label={switchDisabledReason}>
          <div>
            <GreySwitch
              color="primary"
              checked={false}
              disabled={isSwitchDisabled}
              onChange={handleClickOpen}
              inputProps={{ "aria-label": "Enable autosharing" }}
            />
          </div>
        </Tooltip>
      )}
      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <DialogTitle id="form-dialog-title">Enable autosharing</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Autosharing work will ensure that all current and future documents
            and notebooks for user <b>{username}</b> will be shared with group
            <b> {group.groupDisplayName}</b> with the READ permission.
            <br />
            <br />
            The EDIT permission can be granted or items can be unshared from the
            "Manage Shared Documents" section as usual.
            <br />
            <br />
            Please enter a name for the folder that the work will be shared
            into.
          </DialogContentText>
          <TextField
            variant="standard"
            label="Folder name"
            placeholder={username}
            autoFocus={true}
            variant="outlined"
            fullWidth
            size="small"
            onChange={handleChange}
          />
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
export default function WrappedEnableAutosharingDialog(props) {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <EnableAutoshareDialog {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
