import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { ThemeProvider } from "@mui/material/styles";
import React from "react";
import { useTranslation } from "react-i18next";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import materialTheme from "../../theme";
import ConnectedAppsTable from "./ConnectedAppsTable";

export default function ConnectedAppsTrigger() {
  const { t } = useTranslation("common");
  const [open, setOpen] = React.useState(false);

  const handleOpen = () => {
    setOpen(true);
  };

  return (
    <MuiCssLayerProvider>
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
    </MuiCssLayerProvider>
  );
}
