//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import AppBar from "../../../components/AppBar";
import createAccentedTheme from "../../../accentedTheme";
import { ThemeProvider } from "@mui/material/styles";
import { COLOR } from "./Sidebar";
import SidebarToggle from "../../../components/AppBar/SidebarToggle";

type HeaderArgs = {|
  sidebarId: string,
|};

function Header({ sidebarId }: HeaderArgs): Node {
  const { uiStore } = useStores();

  const handleToggleOpen = () => {
    uiStore.toggleSidebar();
  };

  return (
    <ThemeProvider theme={createAccentedTheme(COLOR)}>
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

export default (observer(Header): ComponentType<HeaderArgs>);
