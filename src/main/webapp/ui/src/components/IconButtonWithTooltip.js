// @flow strict

import React, { type Node, type ComponentType, forwardRef } from "react";
import { observer } from "mobx-react-lite";
import CustomTooltip from "./CustomTooltip";
import IconButton from "@mui/material/IconButton";
import { type Sx } from "../util/styles";

type RemainingIconButtonProps = {|
  className?: string,
  onClick?: (Event & { currentTarget: EventTarget, ... }) => void,
  "data-test-id"?: string,
  size?: "small" | "medium" | "large",
  color?: "primary" | "secondary" | "standardIcon",
  "aria-haspopup"?: "true" | "menu" | "listbox" | "tree" | "grid" | "dialog",
  sx?: Sx,
  classes?: { [string]: string },
  ariaLabel?: string,
  "aria-haspopup"?: "menu",
  "aria-controls"?: string,
  "aria-expanded"?: "true",
  id?: string,
  tabIndex?: number,
|};

type IconButtonWithTooltipArgs = {|
  title: string,
  icon: Node,
  disabled?: boolean,
  ...RemainingIconButtonProps,
|};

const IconButtonWithTooltip = forwardRef<
  IconButtonWithTooltipArgs,
  typeof IconButton
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
export default (observer(
  IconButtonWithTooltip
): ComponentType<IconButtonWithTooltipArgs>);
