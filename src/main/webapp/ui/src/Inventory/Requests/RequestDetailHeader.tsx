import Box from "@mui/material/Box";
import { useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";
import BackToListButton from "./BackToListButton";
import RequestsStatusChip from "./RequestsStatusChip";

/**
 * The Requests detail panel's title bar: the request id and requested sample's name, plus the
 * request's current status chip.
 */
export default function RequestDetailHeader({
  id,
  sampleName,
  status,
}: {
  id: number;
  sampleName: string;
  status: string | undefined;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const theme = useTheme();

  return (
    <Box
      sx={{
        p: 2,
        backgroundColor: theme.palette.grey[200],
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 1.5,
      }}
    >
      <Box sx={{ display: "flex", alignItems: "center", gap: 0.5, minWidth: 0 }}>
        <BackToListButton />
        <Typography variant="h5" sx={{ overflowWrap: "anywhere" }}>
          {t("requestsManagement.detail.title", { id, sampleName })}
        </Typography>
      </Box>
      {status && <RequestsStatusChip status={status} />}
    </Box>
  );
}
