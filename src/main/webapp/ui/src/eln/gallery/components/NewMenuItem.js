//@flow

import React, { type Node, type ComponentType } from "react";
import { styled } from "@mui/material/styles";
import MenuItem from "@mui/material/MenuItem";
import CardHeader from "@mui/material/CardHeader";

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
  const color = `hsl(${foregroundColor.hue}deg, ${foregroundColor.saturation}%, ${foregroundColor.lightness}%, 100%)`;
  return {
    margin: theme.spacing(1),
    padding: 0,
    borderRadius: "2px",
    backgroundColor: `hsl(${backgroundColor.hue}deg, ${backgroundColor.saturation}%, ${backgroundColor.lightness}%, 12%)`,
    transition: "background-color ease-in-out .2s",
    "&:hover": {
      backgroundColor: `hsl(${backgroundColor.hue}deg, ${backgroundColor.saturation}%, ${backgroundColor.lightness}%, 24%)`,
    },
    "& .MuiCardHeader-avatar": {
      border: `6px solid ${`hsl(${backgroundColor.hue}deg, ${backgroundColor.saturation}%, ${backgroundColor.lightness}%, 100%)`}`,
      borderRadius: "6px",
    },
    "& .MuiCardMedia-root": {
      width: 32,
      height: 32,
    },
    "& .MuiSvgIcon-root": {
      width: 32,
      height: 32,
      background: `hsl(${backgroundColor.hue}deg, ${backgroundColor.saturation}%, ${backgroundColor.lightness}%, 100%)`,
      padding: theme.spacing(0.5),
      color,
    },
    "& .MuiTypography-root": {
      color,
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
