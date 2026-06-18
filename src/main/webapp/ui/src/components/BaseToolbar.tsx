import AppBar from "@mui/material/AppBar";
import { iconButtonClasses } from "@mui/material/IconButton";
import Toolbar from "@mui/material/Toolbar";
import type React from "react";

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
