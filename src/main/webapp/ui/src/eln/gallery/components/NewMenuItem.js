//@flow

import React, {
  type Node,
  type ComponentType,
  type ElementConfig,
} from "react";
import { styled } from "@mui/material/styles";
import MenuItem from "@mui/material/MenuItem";
import CardHeader from "@mui/material/CardHeader";
import { alpha } from "@mui/system";

type NewMenuItemArgs = {|
  title: string,
  avatar: Node,
  subheader: string,
  foregroundColor:
    | string
    | {| hue: number, saturation: number, lightness: number |},
  backgroundColor:
    | string
    | {| hue: number, saturation: number, lightness: number |},
  onClick?: () => void,
  onKeyDown?: (KeyboardEvent) => void,
  compact?: boolean,
  disabled?: boolean,
  "aria-haspopup"?: "menu" | "dialog",

  /*
   * These properties are dynamically added by the MUI Menu parent component
   */
  autoFocus?: boolean,
  tabIndex?: number,

  ...ElementConfig<typeof CardHeader>,
|};

export default (styled(
  React.forwardRef(
    (
      {
        foregroundColor: _foregroundColor,
        backgroundColor: _backgroundColor,
        compact: _compact,
        className,
        onClick,
        onKeyDown,
        disabled,
        autoFocus,
        tabIndex,
        "aria-haspopup": ariaHasPopup,
        ...props
      }: NewMenuItemArgs,
      ref
    ) => (
      <MenuItem
        ref={ref}
        className={className}
        onKeyDown={onKeyDown}
        onClick={onClick}
        disabled={disabled}
        //eslint-disable-next-line jsx-a11y/no-autofocus
        autoFocus={autoFocus}
        tabIndex={tabIndex}
        aria-haspopup={ariaHasPopup}
      >
        <CardHeader {...props} />
      </MenuItem>
    )
  )
)(({ theme, backgroundColor, foregroundColor, compact }) => {
  const prefersMoreContrast = window.matchMedia(
    "(prefers-contrast: more)"
  ).matches;
  const fg =
    typeof foregroundColor === "string"
      ? foregroundColor
      : `hsl(${foregroundColor.hue}deg, ${foregroundColor.saturation}%, ${foregroundColor.lightness}%, 100%)`;
  const bg =
    typeof backgroundColor === "string"
      ? backgroundColor
      : `hsl(${backgroundColor.hue}deg, ${backgroundColor.saturation}%, ${backgroundColor.lightness}%, 100%)`;
  return {
    margin: theme.spacing(compact ? 0.5 : 1),
    padding: 0,
    borderRadius: "2px",
    border: prefersMoreContrast ? "2px solid #000" : "none",
    backgroundColor: prefersMoreContrast ? "#fff" : alpha(bg, 0.24),
    transition: "background-color ease-in-out .2s",
    "&:hover": {
      backgroundColor: prefersMoreContrast ? "#fff" : alpha(bg, 0.36),
    },
    "& .MuiCardHeader-root": {
      padding: theme.spacing(compact ? 1 : 2),
    },
    "& .MuiCardHeader-avatar": {
      border: `${compact ? 3 : 6}px solid ${bg}`,
      borderRadius: `${compact ? 4 : 6}px`,
    },
    "& .MuiCardMedia-root": {
      width: compact ? 28 : 32,
      height: compact ? 28 : 32,
    },
    "& .MuiSvgIcon-root": {
      width: compact ? 28 : 32,
      height: compact ? 28 : 32,
      background: bg,
      padding: theme.spacing(0.5),
      color: fg,
    },
    "& .MuiTypography-root": {
      color: prefersMoreContrast ? "#000" : fg,
    },
    "& .MuiCardHeader-title": {
      fontSize: "1rem",
      fontWeight: 500,
    },
  };
}): ComponentType<NewMenuItemArgs>);
