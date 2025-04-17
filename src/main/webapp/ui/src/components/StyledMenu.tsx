import React from "react";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import { withStyles } from "Styles";

export const StyledMenu = withStyles<
  React.ComponentProps<typeof Menu>,
  { paper: string }
>((theme) => ({
  paper: {
    border: theme.borders.menu,
  },
}))((props) => (
  <Menu
    elevation={0}
    anchorOrigin={{
      vertical: "bottom",
      horizontal: "center",
    }}
    transformOrigin={{
      vertical: "top",
      horizontal: "center",
    }}
    {...props}
    keepMounted={false}
  />
));

export const StyledMenuItem = MenuItem;
