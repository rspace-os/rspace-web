import DeleteIcon from "@mui/icons-material/Delete";
import { useTheme } from "@mui/material/styles";
import type React from "react";
import { useState } from "react";
import IconButtonWithTooltip from "../../components/IconButtonWithTooltip";
import { match } from "../../util/Util";

type DeleteButtonArgs = {
  onClick: () => void;
  disabled: boolean;
  tooltipAfterClicked: string;
  tooltipBeforeClicked: string;
  tooltipWhenDisabled: string;
};

/**
 * This component is a one-shot button designed for cases where the user can
 * perform some delete action that is only persisted at a later point in time.
 */
export default function DeleteButton({
  onClick,
  disabled,
  tooltipAfterClicked,
  tooltipBeforeClicked,
  tooltipWhenDisabled,
}: DeleteButtonArgs): React.ReactNode {
  const [removed, setRemoved] = useState(false);
  const theme = useTheme();
  const isDisabled = disabled || removed;

  const handleClick = () => {
    setRemoved(true);
    onClick();
  };

  return (
    <IconButtonWithTooltip
      title={match<void, string>([
        [() => disabled, tooltipWhenDisabled],
        [() => removed, tooltipAfterClicked],
        [() => true, tooltipBeforeClicked],
      ])()}
      onClick={handleClick}
      disabled={isDisabled}
      icon={<DeleteIcon />}
      size="small"
      sx={{ color: isDisabled ? "initial" : theme.palette.warningRed }}
    />
  );
}
