import React from "react";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";

export default function BaseToolbar(props: { content: React.ReactNode }) {
  return (
    <AppBar position="relative" elevation={0}>
      <Toolbar
        sx={{
          padding: "0px 0px",
          position: "relative",
          "& button.MuiIconButton-root": {
            width: 48,
            height: 48,
          },
          "& a.MuiIconButton-root": {
            width: 24,
            color: "white",
          },
        }}
      >
        <div
          style={{ display: "flex", padding: "0px 15px", width: "100%", position: "relative" }}
        >
          {props.content}
        </div>
      </Toolbar>
    </AppBar>
  );
}
