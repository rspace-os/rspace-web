//@flow

import React, { type Node, useState } from "react";
import TextField from "@mui/material/TextField";
import Grid from "@mui/material/Grid";
import Button from "@mui/material/Button";
import axios from "axios";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import LoadingFade from "../components/LoadingFade";
import Snackbar from "@mui/material/Snackbar";
import SnackbarContent from "@mui/material/SnackbarContent";
import { type FileSystemId } from "./ExportFileStore";

type FileStoreLoginArgs = {|
  hideCancelButton: boolean,
  callBack: () => void,
  fileSystemId: FileSystemId,
|};

export default function FileStoreLogin({
  hideCancelButton,
  callBack,
  fileSystemId,
}: FileStoreLoginArgs): Node {
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
      .post<
        {|
          fileSystemId: typeof fileSystemId,
          nfsusername: string,
          nfspassword: string,
        |},
        string
      >(url, {
        fileSystemId,
        nfsusername: userName,
        nfspassword: password,
      })
      .then((response) => {
        if (
          response.data ===
          "Authentication problem, check your username and password or contact your System Admin"
        ) {
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
        <Grid item xs={12}>
          <FormControl error aria-describedby="name-error-text">
            <TextField
              variant="standard"
              label="Username"
              value={userName}
              onChange={({ target: { value } }) => setUserName(value)}
              autoComplete="nfsUsername"
              data-test-id="username"
            />
            {userNameError && (
              <FormHelperText id="name-error-text">
                Username cannot be blank
              </FormHelperText>
            )}
          </FormControl>
        </Grid>
        <Grid item xs={12}>
          <FormControl error aria-describedby="password-error-text">
            <TextField
              variant="standard"
              label="Password"
              type="password"
              value={password}
              onChange={({ target: { value } }) => setPassword(value)}
              autoComplete="nfsPassword"
              data-test-id="password"
            />
            {passwordError && (
              <FormHelperText>Password cannot be blank</FormHelperText>
            )}
            {loginError && (
              <FormHelperText>
                Authentication problem, check your username and password or
                contact your System Admin
              </FormHelperText>
            )}
            <br />
          </FormControl>
        </Grid>
        {!hideCancelButton && (
          <Button
            variant="contained"
            color="primary"
            disabled={loading}
            data-test-id="cancel-button"
          >
            Cancel
          </Button>
        )}
        <Button
          variant="contained"
          color="primary"
          disabled={loading}
          onClick={login}
          data-test-id="login-button"
        >
          Login
        </Button>
        <Snackbar
          anchorOrigin={{ vertical: "top", horizontal: "right" }}
          open={toast}
          autoHideDuration={6000}
          onClose={() => setToast(false)}
        >
          <SnackbarContent
            onClose={() => setToast(false)}
            message={`Logged in as ${userName}`}
          />
        </Snackbar>
      </Grid>
      <LoadingFade loading={loading} />
    </div>
  );
}
