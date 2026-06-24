import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import { createRoot } from "react-dom/client";
import { useTranslation } from "react-i18next";
import materialTheme from "../../theme";
import ConnectedAppsTable from "./ConnectedAppsTable";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function ConnectedAppsTrigger(_props: any) {
  const { t } = useTranslation("common");
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
              {t("profile.oauth.connectedApps.show")}
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
