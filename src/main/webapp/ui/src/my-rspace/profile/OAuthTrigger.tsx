import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import { createRoot } from "react-dom/client";
import Alerts from "@/components/Alerts/Alerts";
import materialTheme from "../../theme";
import OAuthTable from "./OAuthTable";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function OAuthTrigger(_props: any) {
  const [open, setOpen] = React.useState(false);

  const handleOpen = () => {
    setOpen(true);
  };

  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <Alerts>
          {!open && (
            <Box sx={{ width: "690px", padding: "0px 15px" }}>
              <Button color="primary" onClick={handleOpen}>
                Show Created OAuth Apps
              </Button>
            </Box>
          )}
          {open && <OAuthTable />}
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

const domContainer = document.getElementById("oAuthApps");

if (domContainer) {
  const root = createRoot(domContainer);
  root.render(<OAuthTrigger />);
}
