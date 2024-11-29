//@flow

import React, { type Node } from "react";
import { Dialog } from "../../../components/DialogBoundary";
import DialogTitle from "@mui/material/DialogTitle";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import Button from "@mui/material/Button";

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

export function FilestoreLoginDialog({ children }: {| children: Node |}): Node {
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
        <Dialog
          open
          onClose={() => {
            resolve.r(false);
            setResolve(null);
          }}
        >
          <DialogTitle>Filestore Login</DialogTitle>
          <DialogContent>
            Please authenticate to file system <strong>{filesystemName}</strong>
            .
          </DialogContent>
          <DialogActions>
            <Button
              onClick={() => {
                resolve.r(false);
                setResolve(null);
              }}
            >
              Cancel
            </Button>
            <Button
              onClick={() => {
                // do the login
              }}
            >
              Log In
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </>
  );
}
