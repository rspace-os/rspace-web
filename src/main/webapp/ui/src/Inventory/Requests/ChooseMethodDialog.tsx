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
import type { PreparationMethod } from "./types";

export default function ChooseMethodDialog({
  open,
  preparationMethod,
  onMethodChange,
  warnings,
  onClose,
  onProceed,
}: {
  open: boolean;
  preparationMethod: PreparationMethod | null;
  onMethodChange: (method: PreparationMethod) => void;
  /**
   * Transfer-specific consequences (multiple subsamples, other active requests) to show the owner
   * before they proceed - already worded and ordered by the caller (see
   * RequestDetailPanel's chooseMethodTransferWarnings). Combined into one alert rather than one
   * each: both warn about consequences of the same "transfer" choice, so showing them as separate
   * boxes would read as more alarming than warranted.
   */
  warnings: Array<string>;
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
        <RadioGroup value={preparationMethod ?? ""} onChange={(_, value) => onMethodChange(value as PreparationMethod)}>
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
        {warnings.length > 0 && (
          <Alert severity="warning" sx={{ mt: 2 }}>
            {warnings.map((warning) => (
              <Typography key={warning} variant="body2">
                {warning}
              </Typography>
            ))}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.cancel")}</Button>
        <Button
          variant="contained"
          color="callToAction"
          disableElevation
          disabled={preparationMethod === null}
          onClick={onProceed}
        >
          {t("requestsManagement.detail.chooseMethodDialog.proceedButton")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
