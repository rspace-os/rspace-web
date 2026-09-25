import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import { darken, useTheme } from "@mui/material/styles";
import type React from "react";

function toTitleCase(status: string): string {
  return status.charAt(0) + status.slice(1).toLowerCase();
}

const STATUS_PALETTE_KEY: Record<string, "warning" | "success" | "error" | "info"> = {
  PENDING: "warning",
  APPROVED: "success",
  FULFILLED: "info",
  REJECTED: "error",
};

export const STATUS_BACKGROUND: Record<string, string> = {
  PENDING: "#FDF0DF",
  APPROVED: "#E7EEF2",
  FULFILLED: "#E6F1E8",
  REJECTED: "#FBEAE8",
};

const STATUS_DOT_COLOR: Record<string, string> = {
  PENDING: "#E07B00",
  APPROVED: "#3E5866",
};

function ChipLabel({ status }: { status: string }): React.ReactNode {
  const dotColor = STATUS_DOT_COLOR[status];
  if (!dotColor) return toTitleCase(status);
  return (
    <Box sx={{ display: "flex", alignItems: "center", gap: 0.75 }}>
      <Box component="span" sx={{ width: 8, height: 8, borderRadius: "50%", backgroundColor: dotColor }} />
      {toTitleCase(status)}
    </Box>
  );
}

/**
 * A chip for a SampleRequest's status: "Pending" in orange, "Approved" in
 * blue-gray, "Fulfilled" in green, "Rejected" in red, "Cancelled" in gray.
 * "Pending" and "Approved" also show a coloured dot. Display-only unless an
 * `onClick` is provided, in which case it becomes clickable. Pass `bordered`
 * to add a thin outline, e.g. for use against a similarly-coloured background.
 */
export default function RequestsStatusChip({
  status,
  onClick,
  bordered = false,
}: {
  status: string;
  onClick?: () => void;
  bordered?: boolean;
}): React.ReactNode {
  const theme = useTheme();
  const clickableProps = onClick ? { onClick, clickable: true } : {};
  const border = bordered ? `1px solid ${theme.palette.divider}` : undefined;

  if (status === "PENDING") {
    return (
      <Chip
        size="small"
        label={<ChipLabel status={status} />}
        {...clickableProps}
        sx={{
          fontWeight: theme.typography.fontWeightMedium,
          // The Inventory accented theme's MuiChip override targets the compound
          // `.MuiChip-root.MuiChip-filled` class (and, when clickable, an even more
          // specific `.MuiChip-root.MuiChip-filled.MuiChip-clickable`), which out-specify
          // a plain sx class. "&&&" repeats this rule's own selector three times to match
          // (and beat) that specificity regardless of whether this chip is clickable.
          "&&&": {
            backgroundColor: STATUS_BACKGROUND.PENDING,
            color: "rgb(183, 121, 31)",
            border,
          },
        }}
      />
    );
  }

  if (status === "CANCELLED") {
    return (
      <Chip
        size="small"
        label={toTitleCase(status)}
        {...clickableProps}
        sx={{
          fontWeight: theme.typography.fontWeightMedium,
          "&&&": {
            backgroundColor: theme.palette.grey[300],
            color: theme.palette.grey[700],
            border,
          },
        }}
      />
    );
  }

  const paletteColor = theme.palette[STATUS_PALETTE_KEY[status] ?? "warning"];

  return (
    <Chip
      size="small"
      label={<ChipLabel status={status} />}
      {...clickableProps}
      sx={{
        fontWeight: theme.typography.fontWeightMedium,
        "&&&": {
          backgroundColor: STATUS_BACKGROUND[status],
          color: darken(paletteColor.dark, 0.3),
          border,
        },
      }}
    />
  );
}
