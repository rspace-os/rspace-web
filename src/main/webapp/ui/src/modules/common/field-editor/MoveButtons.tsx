import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import VerticalAlignBottomIcon from "@mui/icons-material/VerticalAlignBottom";
import VerticalAlignTopIcon from "@mui/icons-material/VerticalAlignTop";
import Button from "@mui/material/Button";
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

/** Moves a field to the top, up, down or to the bottom of the list. */
export default function MoveButtons({ position, count, onMove, fieldName }: MoveButtonsProps): ReactNode {
  const { t } = useTranslation("common");
  const buttons = [
    {
      label: t("fieldEditor.card.moveToTop", { field: fieldName }),
      tooltip: t("fieldEditor.card.moveToTopTooltip"),
      icon: <VerticalAlignTopIcon />,
      target: 0,
      disabled: position === 0,
    },
    {
      label: t("fieldEditor.card.moveUp", { field: fieldName }),
      tooltip: t("fieldEditor.card.moveUpTooltip"),
      icon: <ArrowUpwardIcon />,
      target: position - 1,
      disabled: position === 0,
    },
    {
      label: t("fieldEditor.card.moveDown", { field: fieldName }),
      tooltip: t("fieldEditor.card.moveDownTooltip"),
      icon: <ArrowDownwardIcon />,
      target: position + 1,
      disabled: position >= count - 1,
    },
    {
      label: t("fieldEditor.card.moveToBottom", { field: fieldName }),
      tooltip: t("fieldEditor.card.moveToBottomTooltip"),
      icon: <VerticalAlignBottomIcon />,
      target: count - 1,
      disabled: position >= count - 1,
    },
  ];
  return (
    <Stack direction="row" spacing={0.5} role="group" aria-label={t("fieldEditor.card.move")}>
      {buttons.map(({ label, tooltip, icon, target, disabled }) => (
        <Tooltip key={label} title={tooltip} enterDelay={200}>
          {/* A span keeps the tooltip working on a disabled button. */}
          <span style={{ flexGrow: 1, display: "flex" }}>
            <Button
              variant="outlined"
              aria-label={label}
              disabled={disabled}
              onClick={() => onMove(target)}
              sx={{ flexGrow: 1, minWidth: 0 }}
            >
              {icon}
            </Button>
          </span>
        </Tooltip>
      ))}
    </Stack>
  );
}
