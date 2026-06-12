import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import { createRoot } from "react-dom/client";
import materialTheme from "../../theme";
import ConnectedAppsTable from "./ConnectedAppsTable";

// biome-ignore lint/correctness/noUnusedFunctionParameters: initial biome migration
// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
export default function ConnectedAppsTrigger(props: any) {
  const [open, setOpen] = React.useState(false);

  const handleOpen = () => {
    setOpen(true);
  };

  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        {!open && (
          <Box sx={{ width: "690px", padding: "0px 15px" }}>
            <Button color="primary" onClick={handleOpen}>
              Show Connected Apps
            </Button>
          </Box>
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
