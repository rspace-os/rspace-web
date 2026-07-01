import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import Portal from "@mui/material/Portal";
import { ThemeProvider } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import React from "react";
import { useTranslation } from "react-i18next";
import createAccentedTheme from "@/accentedTheme";
import axios from "@/common/axios";
import ValidatingSubmitButton from "@/components/ValidatingSubmitButton";
import TransRichText from "@/modules/common/i18n/TransRichText";
import AnalyticsContext from "@/stores/contexts/Analytics";
import Result from "@/util/result";
import { ACCENT_COLOR } from "../../assets/branding/rspace/workspace";
import Alerts from "../../components/Alerts/Alerts";
import Analytics from "../../components/Analytics";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import ErrorBoundary from "../../components/ErrorBoundary";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";

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
  const { t } = useTranslation(["workspace", "common"]);
  const [open, setOpen] = React.useState(false);
  const [documentId, setDocumentId] = React.useState("");
  const [currentName, setCurrentName] = React.useState("");
  const [newName, setNewName] = React.useState("");
  const { trackEvent } = React.useContext(AnalyticsContext);
  const { addAlert } = React.useContext(AlertContext);
  const [submitting, setSubmitting] = React.useState(false);

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
    setSubmitting(true);
    try {
      const formData = new FormData();
      formData.append("recordId", documentId);
      formData.append("newName", newName);
      const { data } = await axios.post("/workspace/editor/structuredDocument/ajax/rename", formData);
      if (data.data === "Success") {
        addAlert(
          mkAlert({
            variant: "success",
            message: t("toolbar.rename.success"),
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
            message: t("toolbar.rename.error"),
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
            title: t("toolbar.rename.error"),
            message: e.message,
          }),
        );
      return;
    } finally {
      setSubmitting(false);
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
        <DialogTitle>{t("toolbar.rename.title")}</DialogTitle>
        <DialogContent>
          <DialogContentText variant="body2" sx={{ mb: 2 }}>
            <TransRichText ns="workspace" i18nKey="toolbar.rename.prompt" values={{ currentName }} />
          </DialogContentText>
          <TextField
            size="small"
            label={t("toolbar.rename.name")}
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
            {t("common:actions.cancel")}
          </Button>
          <ValidatingSubmitButton
            loading={submitting}
            onClick={() => {
              void handleSubmit();
            }}
            validationResult={
              newName.length === 0 ? Result.Error([new Error(t("toolbar.rename.emptyName"))]) : Result.Ok(null)
            }
          >
            {t("toolbar.rename.title")}
          </ValidatingSubmitButton>
        </DialogActions>
      </form>
    </Dialog>
  );
};
