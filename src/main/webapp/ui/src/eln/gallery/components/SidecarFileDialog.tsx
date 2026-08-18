import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import SubmitSpinnerButton from "../../../components/SubmitSpinnerButton";
import useViewportDimensions from "../../../hooks/browser/useViewportDimensions";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as FetchingData from "../../../util/fetchingData";
import useFilestoresEndpoint, { type SidecarFile } from "../useFilestoresEndpoint";
import PlaceholderLabel from "./PlaceholderLabel";

type SidecarFileDialogArgs = {
  open: boolean;
  onClose: () => void;
  /** The writable S3 filestore the folder lives in. */
  filestoreId: number;
  /** Folder the sidecar describes, relative to the filestore root; "" is the root. */
  folderPath: string;
  /** Refreshes the Gallery listing after a save so the new sidecar file appears. */
  refreshListing: () => Promise<void>;
};

/**
 * Previews the auto-composed metadata sidecar for a folder in an S3 filestore, then optionally
 * writes it to the filestore. The preview is read-only in this phase; editing is deferred.
 */
export default function SidecarFileDialog({
  open,
  onClose,
  filestoreId,
  folderPath,
  refreshListing,
}: SidecarFileDialogArgs): React.ReactNode {
  const { t } = useTranslation(["gallery", "common"]);
  const { isViewportVerySmall } = useViewportDimensions();
  const { addAlert } = React.useContext(AlertContext);
  const { previewSidecarFile, saveSidecarFile } = useFilestoresEndpoint();
  const [preview, setPreview] = React.useState<FetchingData.Fetched<SidecarFile>>({ tag: "loading" });
  const [saving, setSaving] = React.useState(false);

  React.useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setPreview({ tag: "loading" });
    previewSidecarFile(filestoreId, folderPath)
      .then((sidecarFile) => !cancelled && setPreview({ tag: "success", value: sidecarFile }))
      .catch((e) => {
        console.error("SidecarFile preview failed", e);
        if (!cancelled) setPreview({ tag: "error", error: t("sidecarFile.previewFailed") });
      });
    return () => {
      cancelled = true;
    };
    // Deps exclude the per-render previewSidecarFile/t: refetch only when the target changes.
  }, [open, filestoreId, folderPath]);

  const composed = FetchingData.getSuccessValue(preview).orElse(null);

  async function save() {
    if (!composed) return;
    setSaving(true);
    try {
      const saved = await saveSidecarFile(filestoreId, folderPath);
      addAlert(mkAlert({ variant: "success", message: t("sidecarFile.saveSuccess", { filename: saved.filename }) }));
      void refreshListing();
      onClose();
    } catch (e) {
      console.error("SidecarFile save failed", e);
      addAlert(mkAlert({ variant: "error", message: t("sidecarFile.saveFailed") }));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      onKeyDown={(e) => e.stopPropagation()}
      scroll="paper"
      fullWidth
      maxWidth="md"
      fullScreen={isViewportVerySmall}
    >
      <DialogTitle>{t("actionsMenu.generateDataRecord")}</DialogTitle>
      <DialogContent>
        <DialogContentText variant="body2" sx={{ mb: 2 }}>
          {t("sidecarFile.description")}
        </DialogContentText>
        {FetchingData.match(preview, {
          loading: () => <PlaceholderLabel>{t("sidecarFile.loading")}</PlaceholderLabel>,
          error: (error) => <PlaceholderLabel>{error}</PlaceholderLabel>,
          success: (sidecarFile) => (
            <>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                {sidecarFile.filename}
              </Typography>
              <TextField
                value={sidecarFile.content}
                multiline
                minRows={8}
                maxRows={20}
                fullWidth
                slotProps={{
                  input: { readOnly: true, sx: { fontFamily: "monospace", fontSize: "0.8rem" } },
                  htmlInput: { "aria-label": t("sidecarFile.contentLabel") },
                }}
              />
            </>
          ),
        })}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.cancel")}</Button>
        <SubmitSpinnerButton
          onClick={() => void save()}
          disabled={composed === null || saving}
          loading={saving}
          label={t("sidecarFile.save")}
        />
      </DialogActions>
    </Dialog>
  );
}
