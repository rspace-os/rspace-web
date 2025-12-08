import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useEffect, useState } from "react";
import useStores from "../../../stores/use-stores";
import DataciteCard from "./DataciteCard";

type SettingsDialogArgs = {
    open: boolean;
    setOpen: (open: boolean) => void;
};

function SettingsDialog({ open, setOpen }: SettingsDialogArgs): React.ReactNode {
    const { authStore } = useStores();

    const [fetchingSystemSettings, setFetchingSystemSettings] = useState<
        null | { state: "loading" } | { state: "loaded" } | { state: "error"; error: string }
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
                if (e instanceof Error) setFetchingSystemSettings({ state: "error", error: e.message });
            }
        }
        void initialiseSettings();
    }, [authStore.getSystemSettings]);

    const handleClose = () => {
        setOpen(false);
    };

    if (!authStore.systemSettings) return null;

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

export default observer(SettingsDialog);
