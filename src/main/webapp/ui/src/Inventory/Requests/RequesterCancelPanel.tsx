import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Divider from "@mui/material/Divider";
import { useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";

/**
 * Matches the "Cancel" button behaviour in the Sample form's "Request this sample" box: shown to
 * the requester (never the owner) while the request is still PENDING.
 */
export default function RequesterCancelPanel({
  isProcessingAction,
  onCancel,
}: {
  isProcessingAction: boolean;
  onCancel: () => void;
}): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const theme = useTheme();

  return (
    <>
      <Box sx={{ p: 1, backgroundColor: theme.palette.grey[100] }}>
        <Typography variant="subtitle1">{t("requestsManagement.detail.sections.approveReject")}</Typography>
      </Box>
      <Divider />
      <Box sx={{ p: 2, display: "flex", flexDirection: "column", gap: 2 }}>
        <Typography variant="body2">{t("requestsManagement.detail.cancelRequestHint")}</Typography>
        <Box sx={{ display: "flex", gap: 2 }}>
          <Button
            variant="outlined"
            disabled={isProcessingAction}
            sx={{
              "&&": {
                color: theme.palette.grey[700],
                borderColor: theme.palette.grey[500],
                "&:hover": {
                  borderColor: theme.palette.grey[700],
                  backgroundColor: theme.palette.grey[100],
                },
              },
            }}
            onClick={onCancel}
          >
            {t("common:actions.cancel")}
          </Button>
        </Box>
      </Box>
    </>
  );
}
