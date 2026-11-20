import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import VerticalAlignBottomIcon from "@mui/icons-material/VerticalAlignBottom";
import VerticalAlignTopIcon from "@mui/icons-material/VerticalAlignTop";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import Tooltip from "@mui/material/Tooltip";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

type MoveButtonsProps = {
  /** The field's position, from 0. */
  position: number;
  count: number;
  onMove: (newPosition: number) => void;
  fieldName: string;
};

/** Compact icon buttons that move a field to the top, up, down or to the bottom of the list. */
export default function MoveButtons({ position, count, onMove, fieldName }: MoveButtonsProps): ReactNode {
  const { t } = useTranslation("common");
  const buttons = [
    {
      label: t("fieldEditor.card.moveToTop", { field: fieldName }),
      tooltip: t("fieldEditor.card.moveToTopTooltip"),
      icon: <VerticalAlignTopIcon fontSize="small" />,
      target: 0,
      disabled: position === 0,
    },
    {
      label: t("fieldEditor.card.moveUp", { field: fieldName }),
      tooltip: t("fieldEditor.card.moveUpTooltip"),
      icon: <ArrowUpwardIcon fontSize="small" />,
      target: position - 1,
      disabled: position === 0,
    },
    {
      label: t("fieldEditor.card.moveDown", { field: fieldName }),
      tooltip: t("fieldEditor.card.moveDownTooltip"),
      icon: <ArrowDownwardIcon fontSize="small" />,
      target: position + 1,
      disabled: position >= count - 1,
    },
    {
      label: t("fieldEditor.card.moveToBottom", { field: fieldName }),
      tooltip: t("fieldEditor.card.moveToBottomTooltip"),
      icon: <VerticalAlignBottomIcon fontSize="small" />,
      target: count - 1,
      disabled: position >= count - 1,
    },
  ];
  return (
    <Stack direction="row" role="group" aria-label={t("fieldEditor.card.move")}>
      {buttons.map(({ label, tooltip, icon, target, disabled }) => (
        <Tooltip key={label} title={tooltip} enterDelay={200}>
          {/* A span keeps the tooltip working on a disabled button. */}
          <span style={{ display: "flex" }}>
            {/* 30px square, above the 24px minimum target; the outline keeps keyboard focus plain to see */}
            <IconButton
              size="small"
              aria-label={label}
              disabled={disabled}
              onClick={() => onMove(target)}
              sx={(theme) => ({
                "&.Mui-focusVisible": { outline: `2px solid ${theme.palette.primary.main}`, outlineOffset: -2 },
              })}
            >
              {icon}
            </IconButton>
          </span>
        </Tooltip>
      ))}
    </Stack>
  );
}
