import Button from "@mui/material/Button";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import Grid from "@mui/material/Grid";
import Snackbar from "@mui/material/Snackbar";
import SnackbarContent from "@mui/material/SnackbarContent";
import TextField from "@mui/material/TextField";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import LoadingFade from "../components/LoadingFade";
import type { FileSystemId } from "./common";

type FileStoreLoginArgs = {
  hideCancelButton: boolean;
  callBack: () => void;
  fileSystemId: FileSystemId;
};

export default function FileStoreLogin({
  hideCancelButton,
  callBack,
  fileSystemId,
}: FileStoreLoginArgs): React.ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const [userName, setUserName] = useState("");
  const [userNameError, setUserNameError] = useState(false);

  const [password, setPassword] = useState("");
  const [passwordError, setPasswordError] = useState(false);

  const [loginError, setLoginError] = useState(false);
  const [loading, setLoading] = useState(false);
  const [toast, setToast] = useState(false);

  const validationCheck = () => {
    const userNameError = userName === "";
    const passwordError = password === "";
    setUserNameError(userNameError);
    setPasswordError(passwordError);
    return !userNameError && !passwordError;
  };

  const login = () => {
    setLoginError(false);
    if (!validationCheck()) return;

    const url = "/netFiles/ajax/nfsLoginJson";
    setLoading(true);

    axios
      .post<string>(url, {
        fileSystemId,
        nfsusername: userName,
        nfspassword: password,
      })
      .then((response) => {
        if (response.data === "Authentication problem, check your username and password or contact your System Admin") {
          setLoginError(true);
        } else {
          setLoginError(false);
          setToast(true);
          callBack();
        }
        setLoading(false);
      })
      .catch((error) => {
        setLoading(false);
        console.log(error);
      });
  };

  return (
    <div>
      <Grid container>
        <Grid size={12}>
          <FormControl error aria-describedby="name-error-text">
            <TextField
              variant="standard"
              label={t("export.fileStore.login.usernameLabel")}
              value={userName}
              onChange={({ target: { value } }) => setUserName(value)}
              autoComplete="nfsUsername"
              data-test-id="username"
            />
            {userNameError && (
              <FormHelperText id="name-error-text">{t("export.fileStore.login.usernameBlank")}</FormHelperText>
            )}
          </FormControl>
        </Grid>
        <Grid size={12}>
          <FormControl error aria-describedby="password-error-text">
            <TextField
              variant="standard"
              label={t("export.fileStore.login.passwordLabel")}
              type="password"
              value={password}
              onChange={({ target: { value } }) => setPassword(value)}
              autoComplete="nfsPassword"
              data-test-id="password"
            />
            {passwordError && <FormHelperText>{t("export.fileStore.login.passwordBlank")}</FormHelperText>}
            {loginError && <FormHelperText>{t("export.fileStore.login.authProblem")}</FormHelperText>}
            <br />
          </FormControl>
        </Grid>
        {!hideCancelButton && (
          <Button variant="contained" color="primary" disabled={loading} data-test-id="cancel-button">
            {t("common:actions.cancel")}
          </Button>
        )}
        <Button variant="contained" color="primary" disabled={loading} onClick={login} data-test-id="login-button">
          {t("export.fileStore.login.submitButton")}
        </Button>
        <Snackbar
          anchorOrigin={{ vertical: "top", horizontal: "right" }}
          open={toast}
          autoHideDuration={6000}
          onClose={() => setToast(false)}
        >
          <SnackbarContent message={t("export.fileStore.login.loggedInToast", { userName })} />
        </Snackbar>
      </Grid>
      <LoadingFade loading={loading} />
    </div>
  );
}
