//@flow

import React, { type Node, type ComponentType } from "react";
import { styled } from "@mui/material/styles";
import MenuItem from "@mui/material/MenuItem";
import CardHeader from "@mui/material/CardHeader";
import { alpha } from "@mui/system";

export default (styled(
  ({
    foregroundColor: _foregroundColor,
    backgroundColor: _backgroundColor,
    className,
    ...props
  }) => (
    <MenuItem className={className}>
      <CardHeader {...props} />
    </MenuItem>
  )
)(({ theme, backgroundColor, foregroundColor }) => {
  const fg = `hsl(${foregroundColor.hue}deg, ${foregroundColor.saturation}%, ${foregroundColor.lightness}%, 100%)`;
  const bg = `hsl(${backgroundColor.hue}deg, ${backgroundColor.saturation}%, ${backgroundColor.lightness}%, 100%)`;
  return {
    margin: theme.spacing(1),
    padding: 0,
    borderRadius: "2px",
    backgroundColor: alpha(bg, 0.12),
    transition: "background-color ease-in-out .2s",
    "&:hover": {
      backgroundColor: alpha(bg, 0.24),
    },
    "& .MuiCardHeader-avatar": {
      border: `6px solid ${bg}`,
      borderRadius: "6px",
    },
    "& .MuiCardMedia-root": {
      width: 32,
      height: 32,
    },
    "& .MuiSvgIcon-root": {
      width: 32,
      height: 32,
      background: bg,
      padding: theme.spacing(0.5),
      color: fg,
    },
    "& .MuiTypography-root": {
      color: fg,
    },
    "& .MuiCardHeader-title": {
      fontSize: "1rem",
      fontWeight: 500,
    },
  };
}): ComponentType<{|
  title: string,
  avatar: Node,
  subheader: string,
  foregroundColor: {| hue: number, saturation: number, lightness: number |},
  backgroundColor: {| hue: number, saturation: number, lightness: number |},
  onClick?: () => void,
|}>);
