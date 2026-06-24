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
import React, { useRef } from "react";
import { Trans, useTranslation } from "react-i18next";
import axios from "@/common/axios";
import materialTheme from "../../theme";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const RS: any;

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function OAuthDialog(props: any) {
  const { t } = useTranslation("common");
  const [open, setOpen] = React.useState(false);
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
            RS.confirm(response.exceptionMessage, "warning", "infinite");
          } else {
            RS.confirm(t("profile.oauth.dialog.createError"), "warning", "infinite");
          }
        })
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        .catch((error: any) => {
          RS.confirm(error.response.data, "warning", "infinite");
        });
    }
  };

  const isNameValid = () => {
    if (!/[\w-\\s]/.test(appName)) {
      setHasError(true);
      setErrorMessage(t("profile.oauth.dialog.validation.alphanumeric"));
      return false;
    }
    if (appName.length >= 100) {
      setHasError(true);
      setErrorMessage(t("profile.oauth.dialog.validation.maxLength"));
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
        RS.confirm(t("profile.oauth.dialog.copySuccess"), "notice", 3000);
      } else {
        RS.confirm(t("profile.oauth.dialog.copyError"), "warning", 5000);
      }
    } catch (_err) {
      RS.confirm(t("profile.oauth.dialog.copyError"), "warning", 5000);
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
        <Tooltip title={t("profile.oauth.dialog.addNewApp")} enterDelay={100}>
          <IconButton color="inherit" onClick={handleClickOpen}>
            <FontAwesomeIcon icon={faPlus} size="xs" />
          </IconButton>
        </Tooltip>
        <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
          <DialogTitle id="form-dialog-title">
            {created ? t("profile.oauth.dialog.createdTitle") : t("profile.oauth.dialog.createTitle")}
          </DialogTitle>
          <DialogContent>
            <DialogContentText>
              {created && <Trans i18nKey="profile.oauth.dialog.secretWarning" ns="common" />}
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
                  placeholder={t("profile.oauth.dialog.appName")}
                  type="text"
                  fullWidth
                  value={appName}
                  onChange={handleChange}
                  slotProps={{
                    htmlInput: { "aria-label": t("profile.oauth.dialog.appName") },
                  }}
                />
              </form>
            )}
            {created && (
              <Grid container>
                <Grid size={12}>
                  <TextField
                    inputRef={clientIdRef}
                    label={t("profile.oauth.table.clientId")}
                    variant="filled"
                    value={clientId}
                    sx={{ marginRight: "10px", width: "calc(100% - 55px)" }}
                    slotProps={{
                      htmlInput: { "aria-label": t("profile.oauth.table.clientId") },
                    }}
                  />
                  <Tooltip title={t("profile.oauth.dialog.copy")} enterDelay={100}>
                    <IconButton color="inherit" onClick={(e) => copyToClipboard(e, 1)}>
                      <FontAwesomeIcon icon={faCopy} />
                    </IconButton>
                  </Tooltip>
                </Grid>
                <Grid sx={{ marginTop: "10px" }} size={12}>
                  <TextField
                    inputRef={clientSecretRef}
                    label={t("profile.oauth.dialog.clientSecret")}
                    variant="filled"
                    value={unhashedClientSecret}
                    sx={{ marginRight: "10px", width: "calc(100% - 55px)" }}
                    slotProps={{
                      htmlInput: { "aria-label": t("profile.oauth.dialog.clientSecret") },
                    }}
                  />
                  <Tooltip title={t("profile.oauth.dialog.copy")} enterDelay={100}>
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
              {created ? t("actions.close") : t("actions.cancel")}
            </Button>
            {!created && (
              <Button onClick={handleSubmit} color="primary">
                {t("actions.create")}
              </Button>
            )}
          </DialogActions>
        </Dialog>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
