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
import useFilestoresEndpoint, { type Sidecar } from "../useFilestoresEndpoint";
import PlaceholderLabel from "./PlaceholderLabel";

type SidecarDialogArgs = {
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
export default function SidecarDialog({
  open,
  onClose,
  filestoreId,
  folderPath,
  refreshListing,
}: SidecarDialogArgs): React.ReactNode {
  const { t } = useTranslation(["gallery", "common"]);
  const { isViewportVerySmall } = useViewportDimensions();
  const { addAlert } = React.useContext(AlertContext);
  const { previewSidecar, saveSidecar } = useFilestoresEndpoint();
  const [preview, setPreview] = React.useState<FetchingData.Fetched<Sidecar>>({ tag: "loading" });
  const [saving, setSaving] = React.useState(false);

  React.useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setPreview({ tag: "loading" });
    previewSidecar(filestoreId, folderPath)
      .then((sidecar) => !cancelled && setPreview({ tag: "success", value: sidecar }))
      .catch(() => !cancelled && setPreview({ tag: "error", error: t("sidecar.previewFailed") }));
    return () => {
      cancelled = true;
    };
    // Deps exclude the per-render previewSidecar/t: refetch only when the target changes.
  }, [open, filestoreId, folderPath]);

  const composed = FetchingData.getSuccessValue(preview).orElse(null);

  async function save() {
    if (!composed) return;
    setSaving(true);
    try {
      const saved = await saveSidecar(filestoreId, folderPath);
      addAlert(mkAlert({ variant: "success", message: t("sidecar.saveSuccess", { filename: saved.filename }) }));
      void refreshListing();
      onClose();
    } catch {
      addAlert(mkAlert({ variant: "error", message: t("sidecar.saveFailed") }));
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
          {t("sidecar.description")}
        </DialogContentText>
        {FetchingData.match(preview, {
          loading: () => <PlaceholderLabel>{t("sidecar.loading")}</PlaceholderLabel>,
          error: (error) => <PlaceholderLabel>{error}</PlaceholderLabel>,
          success: (sidecar) => (
            <>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                {sidecar.filename}
              </Typography>
              <TextField
                aria-label={t("sidecar.contentLabel")}
                value={sidecar.content}
                multiline
                minRows={8}
                maxRows={20}
                fullWidth
                slotProps={{ input: { readOnly: true, sx: { fontFamily: "monospace", fontSize: "0.8rem" } } }}
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
          label={t("sidecar.save")}
        />
      </DialogActions>
    </Dialog>
  );
}
