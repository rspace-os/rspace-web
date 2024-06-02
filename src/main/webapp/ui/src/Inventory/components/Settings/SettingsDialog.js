//@flow
import React, {
  useState,
  useEffect,
  type Node,
  type ComponentType,
} from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import DataciteCard from "./DataciteCard";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";

type SettingsDialogArgs = {|
  open: boolean,
  setOpen: (boolean) => void,
|};

function SettingsDialog({ open, setOpen }: SettingsDialogArgs): Node {
  const { authStore } = useStores();

  const [fetchingSystemSettings, setFetchingSystemSettings] = useState<
    | null
    | {| state: "loading" |}
    | {| state: "loaded" |}
    | {| state: "error", error: string |}
  >(null);

  /**
   * the settings endpoint is only called when the dialog is actually rendered
   * ie for users who are not sysAdmin it won't be called at all
   */
  useEffect(() => {
    async function initialiseSettings() {
      setFetchingSystemSettings({ state: "loading" });
      try {
        await authStore.getSystemSettings();
        setFetchingSystemSettings({ state: "loaded" });
      } catch (e) {
        setFetchingSystemSettings({ state: "error", error: e.message });
      }
    }
    void initialiseSettings();
  }, []);

  const handleClose = () => {
    setOpen(false);
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>Configure Inventory (for System Administrators)</DialogTitle>
      <DialogContent>
        {fetchingSystemSettings?.state === "loading" && <>Loading</>}
        {fetchingSystemSettings?.state === "error" && (
          <Alert severity="error">
            <AlertTitle>Error</AlertTitle>
            {fetchingSystemSettings.error}
          </Alert>
        )}
        {fetchingSystemSettings?.state === "loaded" ? (
          <FormControl component="fieldset" fullWidth>
            <DataciteCard currentSettings={authStore.systemSettings.datacite} />
          </FormControl>
        ) : null}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}

export default (observer(SettingsDialog): ComponentType<SettingsDialogArgs>);
