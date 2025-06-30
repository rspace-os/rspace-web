import React from "react";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import styled from "@emotion/styled";

const ToolbarContent = styled.div`
  display: flex;
  padding: 0px 15px;
  width: 100%;
  position: relative;

  button.MuiIconButton-root {
    width: 48px;
    height: 48px;
  }

  a.MuiIconButton-root {
    width: 24px;
    color: white;
  }
`;

export default function BaseToolbar(props: { content: React.ReactNode }) {
  return (
    <AppBar position="relative" elevation={0}>
      <Toolbar style={{ padding: "0px 0px", position: "relative" }}>
        <ToolbarContent>{props.content}</ToolbarContent>
      </Toolbar>
    </AppBar>
  );
}
