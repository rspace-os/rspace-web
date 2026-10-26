import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Divider from "@mui/material/Divider";
import { darken, useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";

/**
 * The sample owner's approve/reject/prepare/fulfil action row for the Requests detail panel,
 * plus the info box above it and the status-help text below it.
 */
export default function OwnerActionsPanel({
  status,
  isActionableState,
  prepareSampleEnabled,
  skipChooseMethodDialog,
  isProcessingAction,
  infoBoxText,
  statusHelpText,
  onReject,
  onApprove,
  onPrepareOrTransfer,
  onMarkFulfilled,
}: {
  status: string | undefined;
  isActionableState: boolean;
  prepareSampleEnabled: boolean;
  skipChooseMethodDialog: boolean;
  isProcessingAction: boolean;
  infoBoxText: string | null;
  statusHelpText: string | null;
  onReject: () => void;
  onApprove: () => void;
  onPrepareOrTransfer: () => void;
  onMarkFulfilled: () => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const theme = useTheme();

  return (
    <>
      <Box sx={{ p: 1, backgroundColor: theme.palette.grey[100] }}>
        <Typography variant="subtitle1">{t("requestsManagement.detail.sections.approveReject")}</Typography>
      </Box>
      <Divider />
      <Box sx={{ p: 2, display: "flex", flexDirection: "column", gap: 2 }}>
        {isActionableState && infoBoxText && <Alert severity="info">{infoBoxText}</Alert>}
        <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
          <Button
            variant="outlined"
            disabled={!isActionableState}
            sx={
              isActionableState
                ? {
                    "&&": {
                      ...theme.palette.sampleRequestActions.reject,
                      "&:hover": {
                        borderColor: darken(theme.palette.sampleRequestActions.reject.borderColor, 0.2),
                        backgroundColor: theme.palette.sampleRequestStatus.rejected,
                      },
                    },
                  }
                : undefined
            }
            onClick={onReject}
          >
            {t("requestsManagement.detail.rejectButton")}
          </Button>
          <Button
            variant="contained"
            disabled={status !== "PENDING" || isProcessingAction}
            sx={
              status === "PENDING"
                ? {
                    "&&": {
                      ...theme.palette.sampleRequestActions.approve,
                      "&:hover": {
                        backgroundColor: darken(theme.palette.sampleRequestActions.approve.backgroundColor, 0.15),
                      },
                    },
                  }
                : status === "APPROVED"
                  ? { "&&": theme.palette.sampleRequestActions.approvedDisabled }
                  : undefined
            }
            onClick={onApprove}
          >
            {status === "APPROVED"
              ? t("requestsManagement.detail.approvedButton")
              : t("requestsManagement.detail.approveButton")}
          </Button>
          <Button
            variant="contained"
            disabled={!prepareSampleEnabled}
            sx={
              prepareSampleEnabled
                ? {
                    "&&": {
                      ...theme.palette.sampleRequestActions.approve,
                      "&:hover": {
                        backgroundColor: darken(theme.palette.sampleRequestActions.approve.backgroundColor, 0.15),
                      },
                    },
                  }
                : undefined
            }
            onClick={onPrepareOrTransfer}
          >
            {skipChooseMethodDialog
              ? t("requestsManagement.detail.transferSampleButton")
              : t("requestsManagement.detail.prepareSampleButton")}
          </Button>
          <Button
            variant="outlined"
            disabled={status !== "APPROVED"}
            sx={
              status === "APPROVED"
                ? {
                    "&&": {
                      ...theme.palette.sampleRequestActions.markAsFulfilled,
                      "&:hover": {
                        borderColor: darken(theme.palette.sampleRequestActions.markAsFulfilled.borderColor, 0.2),
                        backgroundColor: theme.palette.sampleRequestStatus.fulfilled,
                      },
                    },
                  }
                : undefined
            }
            onClick={onMarkFulfilled}
          >
            {t("requestsManagement.detail.markAsFulfilledButton")}
          </Button>
        </Box>
        {statusHelpText && (
          <Typography variant="body2" color="text.secondary">
            {statusHelpText}
          </Typography>
        )}
      </Box>
    </>
  );
}
