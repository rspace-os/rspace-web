import React from "react";
import type { Theme } from "@mui/material/styles";
import IconButtonWithTooltip from "./IconButtonWithTooltip";
import ClearIcon from "@mui/icons-material/Clear";

type RemoveButtonArgs = {
  onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void;
  title?: string;
  disabled?: boolean;
};

const RemoveButton = ({
  onClick,
  title = "Delete",
  disabled = false,
}: RemoveButtonArgs): React.ReactNode => {
  return (
    <IconButtonWithTooltip
      title={title}
      icon={<ClearIcon />}
      size="small"
      sx={(theme: Theme) => ({
        "&:hover": {
          color: theme.palette.warningRed,
        },
      })}
      onClick={onClick}
      disabled={disabled}
    />
  );
};

export default RemoveButton;
