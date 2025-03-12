import React from "react";
import { observer } from "mobx-react-lite";
import CustomTooltip from "./CustomTooltip";
import IconButton from "@mui/material/IconButton";
import { SxProps, Theme } from "@mui/system";

type RemainingIconButtonProps = {
  className?: string;
  onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void;
  "data-test-id"?: string;
  size?: "small" | "medium" | "large";
  color?: "primary" | "secondary" | "standardIcon";
  "aria-haspopup"?: "true" | "menu" | "listbox" | "tree" | "grid" | "dialog";
  sx?: SxProps<Theme>;
  classes?: { [key: string]: string };
  ariaLabel?: string;
  "aria-controls"?: string;
  "aria-expanded"?: "true";
  id?: string;
  tabIndex?: number;
};

type IconButtonWithTooltipArgs = {
  title: string;
  icon: React.ReactNode;
  disabled?: boolean;
} & RemainingIconButtonProps;

const IconButtonWithTooltip = React.forwardRef<
  HTMLButtonElement,
  IconButtonWithTooltipArgs
>(({ title, icon, ariaLabel, ...rest }: IconButtonWithTooltipArgs, ref) => {
  return (
    <CustomTooltip title={title} aria-label="">
      <IconButton
        color="inherit"
        aria-label={ariaLabel ?? title}
        {...rest}
        ref={ref}
      >
        {icon}
      </IconButton>
    </CustomTooltip>
  );
});

IconButtonWithTooltip.displayName = "IconButtonWithTooltip";
/**
 * This components provided a clickable icon button with a tooltip. The tooltip
 * is then used as the aria-label for the button.
 */
export default observer(IconButtonWithTooltip);
