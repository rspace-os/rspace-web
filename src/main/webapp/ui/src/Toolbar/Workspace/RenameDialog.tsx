import React from "react";
import Portal from "@mui/material/Portal";
import ErrorBoundary from "../../components/ErrorBoundary";
import Alerts from "../../components/Alerts/Alerts";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Button from "@mui/material/Button";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import Analytics from "../../components/Analytics";
import DialogContentText from "@mui/material/DialogContentText";
import TextField from "@mui/material/TextField";
import ValidatingSubmitButton from "@/components/ValidatingSubmitButton";
import Result from "@/util/result";
import AnalyticsContext from "@/stores/contexts/Analytics";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/rspace/workspace";

export default function Wrapper(): React.ReactNode {
  return (
    <Analytics>
      <ErrorBoundary topOfViewport>
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
          <Portal>
            <Alerts>
              <DialogBoundary>
                <RenameDialog />
              </DialogBoundary>
            </Alerts>
          </Portal>
        </ThemeProvider>
      </ErrorBoundary>
    </Analytics>
  );
}

const RenameDialog = () => {
  const [open, setOpen] = React.useState(false);
  const [documentId, setDocumentId] = React.useState("");
  const [currentName, setCurrentName] = React.useState("");
  const [newName, setNewName] = React.useState("");
  const { trackEvent } = React.useContext(AnalyticsContext);
  const { addAlert } = React.useContext(AlertContext);

  React.useEffect(() => {
    function handler(event: Event) {
      // @ts-expect-error there will be a detail
      const { documentId, currentName } = event.detail;
      setOpen(true);
      setDocumentId(documentId);
      setCurrentName(currentName);
      setNewName("");
    }
    window.addEventListener("OPEN_RENAME_DIALOG", handler);
    return () => {
      window.removeEventListener("OPEN_RENAME_DIALOG", handler);
    };
  }, []);

  function handleClose() {
    setOpen(false);
    setDocumentId("");
    setCurrentName("");
    setNewName("");
  }

  async function handleSubmit() {
    try {
      const formData = new FormData();
      formData.append("recordId", documentId);
      formData.append("newName", newName);
      const { data } = await axios.post(
        "/workspace/editor/structuredDocument/ajax/rename",
        formData,
      );
      if (data.data === "Success") {
        addAlert(
          mkAlert({
            variant: "success",
            message: "Successfully renamed document.",
          }),
        );
        trackEvent("user:renames:document:workspace");
        handleClose();
        window.dispatchEvent(
          new CustomEvent("COMPLETE_RENAME", {
            detail: {
              newName,
            },
          }),
        );
      } else {
        addAlert(
          mkAlert({
            variant: "error",
            message: "An error occurred while renaming the document.",
            details: data.error.errorMessages.map((msg: string) => ({
              variant: "error",
              title: msg,
            })),
          }),
        );
      }
    } catch (e) {
      console.error("Error renaming document:", e);
      if (e instanceof Error)
        addAlert(
          mkAlert({
            variant: "error",
            title: "An error occurred while renaming the document.",
            message: e.message,
          }),
        );
      return;
    }
  }

  return (
    <Dialog
      open={open}
      onClose={() => {
        handleClose();
      }}
      onKeyDown={(e) => {
        e.stopPropagation();
      }}
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void handleSubmit();
        }}
      >
        <DialogTitle>Rename</DialogTitle>
        <DialogContent>
          <DialogContentText variant="body2" sx={{ mb: 2 }}>
            Please give a new name for <strong>{currentName}</strong>
          </DialogContentText>
          <TextField
            size="small"
            label="Name"
            value={newName}
            onChange={({ target: { value } }) => setNewName(value)}
          />
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              handleClose();
            }}
          >
            Cancel
          </Button>
          <ValidatingSubmitButton
            loading={false}
            onClick={() => {
              void handleSubmit();
            }}
            validationResult={
              newName.length === 0
                ? Result.Error([new Error("Empty name is not permitted.")])
                : Result.Ok(null)
            }
          >
            Rename
          </ValidatingSubmitButton>
        </DialogActions>
      </form>
    </Dialog>
  );
};
