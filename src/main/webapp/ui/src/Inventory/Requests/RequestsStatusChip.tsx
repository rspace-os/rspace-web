import Chip from "@mui/material/Chip";
import { darken, lighten, useTheme } from "@mui/material/styles";
import type React from "react";

function toTitleCase(status: string): string {
  return status.charAt(0) + status.slice(1).toLowerCase();
}

const STATUS_PALETTE_KEY: Record<string, "warning" | "success" | "error"> = {
  PENDING: "warning",
  APPROVED: "success",
  FULFILLED: "success",
  REJECTED: "error",
  CANCELLED: "error",
};

/**
 * A display-only chip for a SampleRequest's status: "Pending" in orange,
 * "Approved"/"Fulfilled" in green, "Rejected"/"Cancelled" in red.
 */
export default function RequestsStatusChip({ status }: { status: string }): React.ReactNode {
  const theme = useTheme();
  const paletteColor = theme.palette[STATUS_PALETTE_KEY[status] ?? "warning"];

  return (
    <Chip
      size="small"
      label={toTitleCase(status)}
      sx={{
        backgroundColor: lighten(paletteColor.light, 0.5),
        color: darken(paletteColor.dark, 0.3),
        fontWeight: theme.typography.fontWeightMedium,
      }}
    />
  );
}
