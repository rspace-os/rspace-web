//@flow

import React, { type Node } from "react";
import { Dialog } from "../../../components/DialogBoundary";
import DialogTitle from "@mui/material/DialogTitle";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import Button from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import DialogContentText from "@mui/material/DialogContentText";
import Stack from "@mui/material/Stack";

const FilestoreLoginContext = React.createContext<{|
  login: ({|
    filesystemName: string,
  |}) => Promise<boolean>,
|}>({
  login: () =>
    Promise.reject(
      new Error("FilestoreLoginDialog is not included in the DOM")
    ),
});

export function useFilestoreLogin(): {|
  login: ({|
    filesystemName: string,
  |}) => Promise<boolean>,
|} {
  const { login } = React.useContext(FilestoreLoginContext);
  return {
    login,
  };
}

/*
 * This component is a performance optimisation: We don't want a change to
 * username or password to trigger a re-rendering of the whole page.
 */
const FilestoreLoginDialog = ({
  filesystemName,
  onClose,
}: {|
  filesystemName: string,
  onClose: () => void,
|}): Node => {
  const [username, setUsername] = React.useState("");
  const [password, setPassword] = React.useState("");

  return (
    <Dialog open onClose={onClose}>
      <DialogTitle>Filestore Login</DialogTitle>
      <DialogContent>
        <DialogContentText>
          Please authenticate to file system <strong>{filesystemName}</strong>.
        </DialogContentText>
        <Stack spacing={2} sx={{ mt: 2 }}>
          <TextField
            size="small"
            label="Username"
            value={username}
            onChange={({ target: { value } }) => {
              setUsername(value);
            }}
          />
          <TextField
            size="small"
            label="Password"
            type="password"
            value={password}
            onChange={({ target: { value } }) => {
              setPassword(value);
            }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          onClick={() => {
            // do the login
          }}
        >
          Log In
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export function FilestoreLoginContextualDialog({
  children,
}: {|
  children: Node,
|}): Node {
  const [resolve, setResolve] = React.useState<null | {|
    r: (boolean) => void,
  |}>(null);
  const [filesystemName, setFilesystemName] = React.useState("");

  const login = ({
    filesystemName: newFilesystemName,
  }: {|
    filesystemName: string,
  |}): Promise<boolean> => {
    setFilesystemName(newFilesystemName);
    return new Promise((r) => {
      setResolve({ r });
    });
  };

  return (
    <>
      <FilestoreLoginContext.Provider value={{ login }}>
        {children}
      </FilestoreLoginContext.Provider>
      {resolve !== null && (
        <FilestoreLoginDialog
          filesystemName={filesystemName}
          onClose={() => {
            resolve.r(false);
            setResolve(null);
          }}
        />
      )}
    </>
  );
}
