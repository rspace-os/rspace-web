import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";

export default function ChooseMethodDialog({
  open,
  preparationMethod,
  onMethodChange,
  subSampleCount,
  otherActiveRequestsCount,
  requesterFullName,
  onClose,
  onProceed,
}: {
  open: boolean;
  preparationMethod: "wizard" | "transfer" | null;
  onMethodChange: (method: "wizard" | "transfer") => void;
  subSampleCount: number | null;
  otherActiveRequestsCount: number | null;
  requesterFullName: string;
  onClose: () => void;
  onProceed: () => void;
}): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{t("requestsManagement.detail.chooseMethodDialog.title")}</DialogTitle>
      <DialogContent>
        <Typography variant="body1" sx={{ mb: 2 }}>
          {t("requestsManagement.detail.chooseMethodDialog.body")}
        </Typography>
        <RadioGroup
          value={preparationMethod ?? ""}
          onChange={(_, value) => onMethodChange(value as "wizard" | "transfer")}
        >
          <FormControlLabel
            value="wizard"
            control={<Radio />}
            label={t("requestsManagement.detail.chooseMethodDialog.wizardOption")}
          />
          <FormControlLabel
            value="transfer"
            control={<Radio />}
            label={t("requestsManagement.detail.chooseMethodDialog.transferOption")}
          />
        </RadioGroup>
        {preparationMethod === "transfer" && subSampleCount !== null && subSampleCount > 1 && (
          <Alert severity="warning" sx={{ mt: 2 }}>
            {t("requestsManagement.detail.chooseMethodDialog.transferWarning", {
              count: subSampleCount,
              requester: requesterFullName,
            })}
          </Alert>
        )}
        {preparationMethod === "transfer" && otherActiveRequestsCount !== null && otherActiveRequestsCount > 0 && (
          <Alert severity="warning" sx={{ mt: 2 }}>
            {t("requestsManagement.detail.chooseMethodDialog.otherActiveRequestsWarning")}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.cancel")}</Button>
        <Button variant="contained" disabled={preparationMethod === null} onClick={onProceed}>
          {t("requestsManagement.detail.chooseMethodDialog.proceedButton")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
