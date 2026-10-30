import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import { darken, useTheme } from "@mui/material/styles";
import type { TFunction } from "i18next";
import type React from "react";
import { useTranslation } from "react-i18next";

const STATUS_LABEL_KEY = {
  PENDING: "requestsManagement.status.pending",
  APPROVED: "requestsManagement.status.approved",
  FULFILLED: "requestsManagement.status.fulfilled",
  REJECTED: "requestsManagement.status.rejected",
  CANCELLED: "requestsManagement.status.cancelled",
} as const;

export function requestStatusLabel(status: string, t: TFunction<"inventory">): string {
  return status in STATUS_LABEL_KEY ? t(STATUS_LABEL_KEY[status as keyof typeof STATUS_LABEL_KEY]) : status;
}

const STATUS_PALETTE_KEY: Record<string, "warning" | "success" | "error" | "info"> = {
  PENDING: "warning",
  APPROVED: "success",
  FULFILLED: "info",
  REJECTED: "error",
};

// Maps a status onto the matching key of theme.palette.sampleRequestStatus - CANCELLED isn't
// part of that namespace at all, since it already renders with theme.palette.grey directly.
const STATUS_BACKGROUND_KEY: Record<string, "pending" | "approved" | "fulfilled" | "rejected"> = {
  PENDING: "pending",
  APPROVED: "approved",
  FULFILLED: "fulfilled",
  REJECTED: "rejected",
};

const STATUS_DOT_COLOR: Record<string, string> = {
  PENDING: "#E07B00",
  APPROVED: "#3E5866",
};

// Pending text colour explicitly specified to meet WCAG guidelines.
export const PENDING_TEXT_COLOR = "rgb(150, 99, 25)";

function ChipLabel({ status }: { status: string }): React.ReactNode {
  const { t } = useTranslation("inventory");
  const dotColor = STATUS_DOT_COLOR[status];
  if (!dotColor) return requestStatusLabel(status, t);
  return (
    <Box sx={{ display: "flex", alignItems: "center", gap: 0.75 }}>
      <Box component="span" sx={{ width: 8, height: 8, borderRadius: "50%", backgroundColor: dotColor }} />
      {requestStatusLabel(status, t)}
    </Box>
  );
}

/**
 * A chip for a SampleRequest's status. Display-only unless an `onClick`
 * is provided. Pass `bordered` to add a thin outline, e.g. for use
 * against a similarly-coloured background.
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
  const { t } = useTranslation("inventory");
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
            backgroundColor: theme.palette.sampleRequestStatus.pending,
            color: PENDING_TEXT_COLOR,
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
        label={requestStatusLabel(status, t)}
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
  const backgroundKey = STATUS_BACKGROUND_KEY[status];

  return (
    <Chip
      size="small"
      label={<ChipLabel status={status} />}
      {...clickableProps}
      sx={{
        fontWeight: theme.typography.fontWeightMedium,
        "&&&": {
          backgroundColor: backgroundKey ? theme.palette.sampleRequestStatus[backgroundKey] : undefined,
          color: darken(paletteColor.dark, 0.3),
          border,
        },
      }}
    />
  );
}
