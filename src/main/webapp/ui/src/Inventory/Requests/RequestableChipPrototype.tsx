/** @prototype Storybook-only UI exploration; not production-ready. */
import OutboxIcon from "@mui/icons-material/Outbox";
import Chip from "@mui/material/Chip";
import type React from "react";
import { useTranslation } from "react-i18next";

/**
 * Marks a sample the owner has opened up for requests. Shown next to the name
 * in list rows and in the record header so requestable material is
 * identifiable at a glance.
 */
export default function RequestableChipPrototype({ size = "small" }: { size?: "small" | "medium" }): React.ReactNode {
  const { t } = useTranslation("inventory");
  return (
    <Chip
      size={size}
      variant="outlined"
      icon={<OutboxIcon />}
      label={t("requests.requestableChip")}
      sx={(theme) => ({
        borderColor: theme.palette.record.sample.bg,
        fontWeight: theme.typography.fontWeightMedium,
        "& .MuiChip-icon": { color: theme.palette.record.sample.bg },
      })}
    />
  );
}
