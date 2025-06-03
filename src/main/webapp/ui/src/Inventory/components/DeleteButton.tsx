import { match } from "../../util/Util";
import { withStyles } from "../../util/styles";
import DeleteIcon from "@mui/icons-material/Delete";
import React, { useState } from "react";
import IconButtonWithTooltip from "../../components/IconButtonWithTooltip";

const StyledIconButton = withStyles<
  {
    title: string;
    icon: React.ReactNode;
    onClick: () => void;
    disabled: boolean;
  },
  { root: string }
>((theme, { disabled }) => ({
  root: {
    color: disabled ? "initial" : theme.palette.warningRed,
  },
}))((props) => <IconButtonWithTooltip {...props} size="small" />);

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

  const handleClick = () => {
    setRemoved(true);
    onClick();
  };

  return (
    <StyledIconButton
      title={match<void, string>([
        [() => disabled, tooltipWhenDisabled],
        [() => removed, tooltipAfterClicked],
        [() => true, tooltipBeforeClicked],
      ])()}
      onClick={handleClick}
      disabled={disabled || removed}
      icon={<DeleteIcon />}
    />
  );
}
