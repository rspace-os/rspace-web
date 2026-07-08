import { faCopy } from "@fortawesome/free-regular-svg-icons/faCopy";
import { faPlus } from "@fortawesome/free-solid-svg-icons/faPlus";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import { ThemeProvider } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Tooltip from "@mui/material/Tooltip";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React, { useContext, useRef } from "react";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";
import { getErrorMessage } from "@/util/error";
import materialTheme from "../../theme";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function OAuthDialog(props: any) {
  const [open, setOpen] = React.useState(false);
  const { addAlert } = useContext(AlertContext);
  const [appName, setAppName] = React.useState("");
  const [hasError, setHasError] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState("");
  const [created, setCreated] = React.useState(false);
  const [clientId, setClientId] = React.useState("");
  const [unhashedClientSecret, setUnhashedClientSecret] = React.useState("");
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const clientIdRef = useRef<any>(null);
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const clientSecretRef = useRef<any>(null);

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

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const handleChange = (e: any) => {
    setAppName(e.target.value);
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const handleSubmit = (e: any) => {
    e.preventDefault();
    if (isNameValid()) {
      const url = `/userform/ajax/oAuthApps/${appName}`;
      axios
        .post(url)
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        .then((response: any) => {
          response = response.data;
          if (response.success) {
            setCreated(true);
            setClientId(response.data.clientId);
            setUnhashedClientSecret(response.data.unhashedClientSecret);
            props.addApp({
              clientId: response.data.clientId,
              appName,
            });
          } else if (Object.hasOwn(response, "exceptionMessage")) {
            addAlert(mkAlert({ message: response.exceptionMessage, variant: "warning", isInfinite: true }));
          } else {
            addAlert(
              mkAlert({
                message:
                  "There was a problem while creating your application. Please, try again later or contact support.",
                variant: "warning",
                isInfinite: true,
              }),
            );
          }
        })
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        .catch((error: any) => {
          addAlert(
            mkAlert({
              message: getErrorMessage(error, "There was a problem while creating your application."),
              variant: "warning",
              isInfinite: true,
            }),
          );
        });
    }
  };

  const isNameValid = () => {
    if (!/[\w-\\s]/.test(appName)) {
      setHasError(true);
      setErrorMessage("The application name should only contain alphanumeric symbols.");
      return false;
    }
    if (appName.length >= 100) {
      setHasError(true);
      setErrorMessage("The application name should be less than 100 symbols.");
      return false;
    }
    setHasError(false);
    return true;
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const copyToClipboard = (_e: any, id: number) => {
    if (id === 1) {
      clientIdRef.current.select();
    } else {
      clientSecretRef.current.select();
    }

    try {
      const successful = document.execCommand("copy");
      if (successful) {
        addAlert(mkAlert({ message: "Copied to clipboard", variant: "notice", duration: 3000 }));
      } else {
        addAlert(
          mkAlert({ message: "Couldn't copy to clipboard. Try again manually.", variant: "warning", duration: 5000 }),
        );
      }
    } catch (_err) {
      addAlert(
        mkAlert({ message: "Couldn't copy to clipboard. Try again manually.", variant: "warning", duration: 5000 }),
      );
    }
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const focusInputField = (input: any) => {
    if (input) {
      setTimeout(() => input.focus(), 100);
    }
  };

  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <Tooltip title="Add a new app" enterDelay={100}>
          <IconButton color="inherit" onClick={handleClickOpen}>
            <FontAwesomeIcon icon={faPlus} size="xs" />
          </IconButton>
        </Tooltip>
        <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
          <DialogTitle id="form-dialog-title">
            {created ? "App Successfully Created" : "Create an OAuth application"}
          </DialogTitle>
          <DialogContent>
            <DialogContentText>
              {created && (
                <>
                  Please write down the client secret.{" "}
                  <strong>It will not be available once you close this window.</strong>
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
                  type="text"
                  fullWidth
                  value={appName}
                  onChange={handleChange}
                  slotProps={{
                    htmlInput: { "aria-label": "App name" },
                  }}
                />
              </form>
            )}
            {created && (
              <Grid container>
                <Grid size={12}>
                  <TextField
                    inputRef={clientIdRef}
                    label="Client ID"
                    variant="filled"
                    value={clientId}
                    sx={{ marginRight: "10px", width: "calc(100% - 55px)" }}
                    slotProps={{
                      htmlInput: { "aria-label": "Client ID" },
                    }}
                  />
                  <Tooltip title="Copy" enterDelay={100}>
                    <IconButton color="inherit" onClick={(e) => copyToClipboard(e, 1)}>
                      <FontAwesomeIcon icon={faCopy} />
                    </IconButton>
                  </Tooltip>
                </Grid>
                <Grid sx={{ marginTop: "10px" }} size={12}>
                  <TextField
                    inputRef={clientSecretRef}
                    label="Client Secret"
                    variant="filled"
                    value={unhashedClientSecret}
                    sx={{ marginRight: "10px", width: "calc(100% - 55px)" }}
                    slotProps={{
                      htmlInput: { "aria-label": "Client secret" },
                    }}
                  />
                  <Tooltip title="Copy" enterDelay={100}>
                    <IconButton color="inherit" onClick={(e) => copyToClipboard(e, 2)}>
                      <FontAwesomeIcon icon={faCopy} />
                    </IconButton>
                  </Tooltip>
                </Grid>
              </Grid>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose} sx={{ color: "grey" }}>
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
