"use strict";
import React, { useRef } from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../../theme";
import Button from "@mui/material/Button";
import axios from "axios";
import TextField from "@mui/material/TextField";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import Tooltip from "@mui/material/Tooltip";
import IconButton from "@mui/material/IconButton";
import Grid from "@mui/material/Grid";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faCopy } from "@fortawesome/free-regular-svg-icons";
import { faPlus } from "@fortawesome/free-solid-svg-icons";
library.add(faCopy, faPlus);

export default function OAuthDialog(props) {
  const [open, setOpen] = React.useState(false);
  const [appName, setAppName] = React.useState("");
  const [hasError, setHasError] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState("");
  const [created, setCreated] = React.useState(false);
  const [clientId, setClientId] = React.useState("");
  const [unhashedClientSecret, setUnhashedClientSecret] = React.useState("");
  const clientIdRef = useRef(null);
  const clientSecretRef = useRef(null);

  const handleClickOpen = () => {
    setOpen(true);
  };

  const resetForm = () => {
    setHasError(false);
    setErrorMessage("");
    setCreated(false);
    setClientId("");
    setUnhashedClientSecret("");
    setAppName("");
  };

  const handleClose = () => {
    setOpen(false);
    resetForm();
  };

  const handleChange = (e) => {
    setAppName(e.target.value);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (isNameValid()) {
      let url = `/userform/ajax/oAuthApps/${appName}`;
      axios
        .post(url)
        .then((response) => {
          response = response.data;
          if (response.success) {
            setCreated(true);
            setClientId(response.data.clientId);
            setUnhashedClientSecret(response.data.unhashedClientSecret);
            props.addApp({
              clientId: response.data.clientId,
              appName: appName,
            });
          } else if (response.hasOwnProperty("exceptionMessage")) {
            RS.confirm(response.exceptionMessage, "warning", "infinite");
          } else {
            RS.confirm(
              "There was a problem while creating your application. Please, try again later or contact support.",
              "warning",
              "infinite"
            );
          }
        })
        .catch((error) => {
          RS.confirm(error.response.data, "warning", "infinite");
        });
    }
  };

  const isNameValid = () => {
    if (!/[\w-\\s]/.test(appName)) {
      setHasError(true);
      setErrorMessage(
        "The application name should only contain alphanumeric symbols."
      );
      return false;
    } else if (appName.length >= 100) {
      setHasError(true);
      setErrorMessage("The application name should be less than 100 symbols.");
      return false;
    } else {
      setHasError(false);
      return true;
    }
  };

  const copyToClipboard = (e, id) => {
    if (id === 1) {
      clientIdRef.current.select();
    } else {
      clientSecretRef.current.select();
    }

    try {
      var successful = document.execCommand("copy");
      if (successful) {
        RS.confirm("Copied to clipboard", "notice", 3000);
      } else {
        RS.confirm(
          "Couldn't copy to clipboard. Try again manually.",
          "warning",
          5000
        );
      }
    } catch (err) {
      RS.confirm(
        "Couldn't copy to clipboard. Try again manually.",
        "warning",
        5000
      );
    }
  };

  const focusInputField = (input) => {
    if (input) {
      setTimeout(() => input.focus(), 100);
    }
  };

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Tooltip title="Add a new app" enterDelay={100}>
          <IconButton color="inherit" onClick={handleClickOpen}>
            <FontAwesomeIcon icon="plus" size="xs" />
          </IconButton>
        </Tooltip>
        <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
          <DialogTitle id="form-dialog-title">
            {created
              ? "App Successfully Created"
              : "Create an OAuth application"}
          </DialogTitle>
          <DialogContent>
            <DialogContentText>
              {created && (
                <>
                  Please write down the client secret.{" "}
                  <b>It will not be available once you close this window.</b>
                </>
              )}
            </DialogContentText>
            {!created && (
              <form onSubmit={handleSubmit}>
                <TextField
                  variant="standard"
                  inputRef={focusInputField}
                  error={hasError}
                  helperText={errorMessage}
                  autoFocus
                  id="name"
                  placeholder="App name"
                  inputProps={{ "aria-label": "App name" }}
                  type="text"
                  fullWidth
                  value={appName}
                  onChange={handleChange}
                />
              </form>
            )}
            {created && (
              <Grid container>
                <Grid item xs={12}>
                  <TextField
                    inputRef={clientIdRef}
                    label="Client ID"
                    variant="filled"
                    value={clientId}
                    style={{ marginRight: "10px", width: "calc(100% - 55px)" }}
                    inputProps={{ "aria-label": "Client ID" }}
                  />
                  <Tooltip title="Copy" enterDelay={100}>
                    <IconButton
                      color="inherit"
                      onClick={(e) => copyToClipboard(e, 1)}
                    >
                      <FontAwesomeIcon icon={["far", "copy"]} />
                    </IconButton>
                  </Tooltip>
                </Grid>
                <Grid item xs={12} style={{ marginTop: "10px" }}>
                  <TextField
                    inputRef={clientSecretRef}
                    label="Client Secret"
                    variant="filled"
                    value={unhashedClientSecret}
                    style={{ marginRight: "10px", width: "calc(100% - 55px)" }}
                    inputProps={{ "aria-label": "Client secret" }}
                  />
                  <Tooltip title="Copy" enterDelay={100}>
                    <IconButton
                      color="inherit"
                      onClick={(e) => copyToClipboard(e, 2)}
                    >
                      <FontAwesomeIcon icon={["far", "copy"]} />
                    </IconButton>
                  </Tooltip>
                </Grid>
              </Grid>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose} style={{ color: "grey" }}>
              {created ? "Close" : "Cancel"}
            </Button>
            {!created && (
              <Button onClick={handleSubmit} color="primary">
                Create
              </Button>
            )}
          </DialogActions>
        </Dialog>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
