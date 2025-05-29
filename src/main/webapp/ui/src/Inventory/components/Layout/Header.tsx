import React from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import AppBar from "../../../components/AppBar";
import createAccentedTheme from "../../../accentedTheme";
import { ThemeProvider } from "@mui/material/styles";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/inventory";
import SidebarToggle from "../../../components/AppBar/SidebarToggle";

type HeaderArgs = {
  sidebarId: string;
};

function Header({ sidebarId }: HeaderArgs): React.ReactNode {
  const { uiStore } = useStores();

  const handleToggleOpen = () => {
    uiStore.toggleSidebar();
  };

  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <AppBar
        variant="page"
        currentPage="Inventory"
        sidebarToggle={
          <SidebarToggle
            sidebarId={sidebarId}
            setSidebarOpen={handleToggleOpen}
            sidebarOpen={uiStore.sidebarOpen}
          />
        }
        accessibilityTips={{
          supports2xZoom: true,
        }}
      />
    </ThemeProvider>
  );
}

export default observer(Header);
