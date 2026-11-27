import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { ThemeProvider } from "@mui/material/styles";
import React from "react";
import { useTranslation } from "react-i18next";
import Alerts from "@/components/Alerts/Alerts";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import materialTheme from "../../theme";
import OAuthTable from "./OAuthTable";

export default function OAuthTrigger() {
  const { t } = useTranslation("common");
  const [open, setOpen] = React.useState(false);

  const handleOpen = () => {
    setOpen(true);
  };

  return (
    <MuiCssLayerProvider>
      <ThemeProvider theme={materialTheme}>
        <Alerts>
          {!open && (
            <Box sx={{ width: "690px", padding: "0px 15px" }}>
              <Button color="primary" onClick={handleOpen}>
                {t("profile.oauth.createdApps.show")}
              </Button>
            </Box>
          )}
          {open && <OAuthTable />}
        </Alerts>
      </ThemeProvider>
    </MuiCssLayerProvider>
  );
}
