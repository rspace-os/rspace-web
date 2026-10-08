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

/**
 * The translated display text for a SampleRequest's status (e.g. "Pending").
 * Falls back to the raw status itself for any value outside the five known
 * statuses, rather than throwing - this is just a display label, not validation.
 */
export function requestStatusLabel(status: string, t: TFunction<"inventory">): string {
  return status in STATUS_LABEL_KEY ? t(STATUS_LABEL_KEY[status as keyof typeof STATUS_LABEL_KEY]) : status;
}

// Text colour only - independent of STATUS_BACKGROUND below, and not matched to it: APPROVED's
// background is blue-gray but this gives it green ("success") text, and FULFILLED's background
// is green but this gives it blue ("info") text. That's the actual, current rendering, not a typo
// to "fix" by swapping these two - changing it would change the chips' appearance, which is out
// of scope here. Flagging it so it doesn't look like unexplained drift next time someone reads
// this against the doc comment on RequestsStatusChip below.
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
 * A chip for a SampleRequest's status: "Pending" has an orange background
 * and text, "Approved" has a blue-gray background with green text,
 * "Fulfilled" has a green background with blue text, "Rejected" has a red
 * background and text, and "Cancelled" has a gray background and text. The
 * Approved/Fulfilled text colours not matching their own backgrounds is the
 * current, deliberate state of STATUS_PALETTE_KEY below, not a documentation
 * slip to "correct" back into matching - see the comment there. "Pending"
 * and "Approved" also show a coloured dot. Display-only unless an `onClick`
 * is provided, in which case it becomes clickable. Pass `bordered` to add a
 * thin outline, e.g. for use against a similarly-coloured background.
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
