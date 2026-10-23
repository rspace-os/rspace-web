import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import { darken, useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";

export default function FulfilRequestDialog({
  open,
  isProcessingAction,
  onClose,
  onConfirm,
}: {
  open: boolean;
  isProcessingAction: boolean;
  onClose: () => void;
  onConfirm: () => void;
}): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const theme = useTheme();

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{t("requestsManagement.detail.fulfilDialog.title")}</DialogTitle>
      <DialogContent>
        <Typography variant="body2">{t("requestsManagement.detail.fulfilDialog.message")}</Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.cancel")}</Button>
        <Button
          variant="outlined"
          disabled={isProcessingAction}
          sx={{
            "&&": {
              ...theme.palette.sampleRequestActions.markAsFulfilled,
              "&:hover": {
                borderColor: darken(theme.palette.sampleRequestActions.markAsFulfilled.borderColor, 0.2),
                backgroundColor: theme.palette.sampleRequestStatus.fulfilled,
              },
            },
          }}
          onClick={onConfirm}
        >
          {t("requestsManagement.detail.fulfilDialog.fulfilButton")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
