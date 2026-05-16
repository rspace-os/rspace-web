"use strict";
import React from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../../theme";
import Button from "@mui/material/Button";
import ConnectedAppsTable from "./ConnectedAppsTable";
import { createRoot } from "react-dom/client";

export default function ConnectedAppsTrigger(props) {
  const [open, setOpen] = React.useState(false);

  const handleOpen = () => {
    setOpen(true);
  };

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        {!open && (
          <div style={{ width: "690px", padding: "0px 15px" }}>
            <Button color="primary" onClick={handleOpen}>
              Show Connected Apps
            </Button>
          </div>
        )}
        {open && <ConnectedAppsTable />}
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

const domContainer = document.getElementById("connected-apps");

if (domContainer) {
  const root = createRoot(domContainer);
  root.render(<ConnectedAppsTrigger />);
}
