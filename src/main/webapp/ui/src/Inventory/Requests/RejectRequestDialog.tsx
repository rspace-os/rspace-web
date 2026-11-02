import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import { useTheme } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useId } from "react";
import { useTranslation } from "react-i18next";
import { rejectButtonSx } from "./actionButtonStyles";

export default function RejectRequestDialog({
  open,
  reason,
  onReasonChange,
  isProcessingAction,
  onClose,
  onConfirm,
}: {
  open: boolean;
  reason: string;
  onReasonChange: (value: string) => void;
  isProcessingAction: boolean;
  onClose: () => void;
  onConfirm: () => void;
}): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const theme = useTheme();
  const reasonFieldId = useId();

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{t("requestsManagement.detail.rejectDialog.title")}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 1 }}>
          {/* component="label" + htmlFor makes this a real, programmatically linked label for
              the TextField below, rather than inert text sitting above it - with no visual
              change, since Typography's variant/styling applies regardless of the rendered tag. */}
          <Typography component="label" htmlFor={reasonFieldId} variant="body2">
            {t("requestsManagement.detail.rejectDialog.reasonLabel")}
          </Typography>
          <TextField
            id={reasonFieldId}
            multiline
            minRows={3}
            fullWidth
            value={reason}
            onChange={({ target: { value } }) => onReasonChange(value)}
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.cancel")}</Button>
        <Button
          variant="outlined"
          disabled={!reason.trim() || isProcessingAction}
          sx={reason.trim() ? rejectButtonSx(theme) : undefined}
          onClick={onConfirm}
        >
          {t("requestsManagement.detail.rejectDialog.rejectRequestButton")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
