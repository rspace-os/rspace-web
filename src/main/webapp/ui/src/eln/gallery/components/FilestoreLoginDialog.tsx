import React from "react";
import { Dialog } from "../../../components/DialogBoundary";
import DialogTitle from "@mui/material/DialogTitle";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import Button from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import DialogContentText from "@mui/material/DialogContentText";
import Stack from "@mui/material/Stack";
import SubmitSpinnerButton from "../../../components/SubmitSpinnerButton";
import axios from "@/common/axios";
import { doNotAwait } from "../../../util/Util";
import useOauthToken from "../../../hooks/auth/useOauthToken";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as Parsers from "../../../util/parsers";
import Result from "../../../util/result";

const FilestoreLoginContext = React.createContext<{
  login: ({
    filesystemName,
    filesystemId,
  }: {
    filesystemName: string;
    filesystemId: number;
  }) => Promise<boolean>;
}>({
  login: () =>
    Promise.reject(
      new Error("FilestoreLoginDialog is not included in the DOM"),
    ),
});

/**
 * Use this hook to trigger a filestore login dialog. Returns true if the login
 * was successful, false if the user cancels the dialog.
 */
export function useFilestoreLogin(): {
  login: ({
    filesystemName,
    filesystemId,
  }: {
    filesystemName: string;
    filesystemId: number;
  }) => Promise<boolean>;
} {
  const { login } = React.useContext(FilestoreLoginContext);
  return {
    login,
  };
}

/**
 * This component is a performance optimisation: We don't want a change to
 * username or password to trigger a re-rendering of the whole page.
 */
const FilestoreLoginDialog = ({
  filesystemName,
  filesystemId,
  onClose,
  onSuccess,
}: {
  filesystemName: string;
  filesystemId: number;
  onClose: () => void;
  onSuccess: () => void;
}): React.ReactNode => {
  const [username, setUsername] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [submitting, setSubmitting] = React.useState(false);
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);

  return (
    <Dialog open onClose={onClose}>
      <form
        onSubmit={doNotAwait(async (e) => {
          e.preventDefault();
          setSubmitting(true);
          try {
            const api = axios.create({
              baseURL: "/api/v1/gallery",
              headers: {
                Authorization: "Bearer " + (await getToken()),
              },
            });
            await api.post<unknown>(`filesystems/${filesystemId}/login`, {
              username,
              password,
            });
            onSuccess();
          } catch (error) {
            console.error(error);
            if (error instanceof Error) {
              const message = Parsers.objectPath(["response", "status"], error)
                .flatMap((status) => {
                  if (status === 403) return Result.Ok("Wrong credentials?");
                  return Parsers.objectPath(
                    ["response", "data", "message"],
                    error,
                  ).flatMap(Parsers.isString);
                })
                .orElse(error.message);
              addAlert(
                mkAlert({
                  variant: "error",
                  title: "Could not authenticate",
                  message,
                }),
              );
            }
          } finally {
            setSubmitting(false);
          }
        })}
      >
        <DialogTitle>Filestore Login</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Please authenticate to the filesystem{" "}
            <strong>{filesystemName}</strong>.
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
          <SubmitSpinnerButton
            type="submit"
            loading={submitting}
            disabled={submitting}
            label="Login"
          />
        </DialogActions>
      </form>
    </Dialog>
  );
};

/**
 * This component provides a context that allows other components to trigger a
 * filestore login dialog. The dialog will prompt the user for a username and
 * password, and then attempt to authenticate with the filestore.
 */
export function FilestoreLoginProvider({
  children,
}: {
  children: React.ReactNode;
}): React.ReactNode {
  const [resolve, setResolve] = React.useState<null | {
    r: (success: boolean) => void;
  }>(null);
  const [filesystemName, setFilesystemName] = React.useState("");
  const [filesystemId, setFilesystemId] = React.useState(0);

  const login = ({
    filesystemName: newFilesystemName,
    filesystemId: newFilesystemId,
  }: {
    filesystemName: string;
    filesystemId: number;
  }): Promise<boolean> => {
    setFilesystemName(newFilesystemName);
    setFilesystemId(newFilesystemId);
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
          filesystemId={filesystemId}
          onClose={() => {
            resolve.r(false);
            setResolve(null);
          }}
          onSuccess={() => {
            resolve.r(true);
            setResolve(null);
          }}
        />
      )}
    </>
  );
}
