import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";

export default function PrepareSampleDialog({
  open,
  selectedSubsampleName,
  onClose,
  onNext,
}: {
  open: boolean;
  selectedSubsampleName: string | null;
  onClose: () => void;
  onNext: () => void;
}): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{t("requestsManagement.detail.prepareDialog.title")}</DialogTitle>
      <DialogContent>
        <Typography variant="body1" sx={{ mb: 1 }}>
          {t("requestsManagement.detail.prepareDialog.body", { subsample: selectedSubsampleName ?? "" })}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t("requestsManagement.detail.prepareDialog.comingSoon")}
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.cancel")}</Button>
        <Button variant="contained" onClick={onNext}>
          {t("common:actions.next")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
