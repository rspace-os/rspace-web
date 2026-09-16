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
};

/**
 * A chip for a SampleRequest's status: "Pending" in orange, "Approved"/"Fulfilled"
 * in green, "Rejected" in red, "Cancelled" in gray. Display-only unless an
 * `onClick` is provided, in which case it becomes clickable.
 */
export default function RequestsStatusChip({
  status,
  onClick,
}: {
  status: string;
  onClick?: () => void;
}): React.ReactNode {
  const theme = useTheme();
  const clickableProps = onClick ? { onClick, clickable: true } : {};

  if (status === "PENDING") {
    // Matches the "Request pending" decoration in the Sample form's "Request this sample" box.
    return (
      <Chip
        size="small"
        label={toTitleCase(status)}
        {...clickableProps}
        sx={{
          fontWeight: theme.typography.fontWeightMedium,
          // The Inventory accented theme's MuiChip override targets the compound
          // `.MuiChip-root.MuiChip-filled` class, which out-specifies a plain sx class;
          // "&&" repeats this rule's own selector to match that specificity and win.
          "&&": {
            backgroundColor: "rgb(251, 241, 222)",
            color: "rgb(183, 121, 31)",
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
          "&&": {
            backgroundColor: theme.palette.grey[300],
            color: theme.palette.grey[700],
          },
        }}
      />
    );
  }

  const paletteColor = theme.palette[STATUS_PALETTE_KEY[status] ?? "warning"];

  return (
    <Chip
      size="small"
      label={toTitleCase(status)}
      {...clickableProps}
      sx={{
        fontWeight: theme.typography.fontWeightMedium,
        "&&": {
          backgroundColor: lighten(paletteColor.light, 0.5),
          color: darken(paletteColor.dark, 0.3),
        },
      }}
    />
  );
}
