import React from "react";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import { iconButtonClasses } from "@mui/material/IconButton";

export default function BaseToolbar(props: { content: React.ReactNode }) {
  return (
    <AppBar position="relative" elevation={0}>
      <Toolbar
        sx={{
          padding: "0 12px !important",
          position: "relative",
          [`& button.${iconButtonClasses.root}`]: {
            width: 48,
            height: 48,
          },
          [`& a.${iconButtonClasses.root}`]: {
            width: 24,
            color: "white",
          },
        }}
      >
        {props.content}
      </Toolbar>
    </AppBar>
  );
}
