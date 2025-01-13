//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import AppBar from "../../../components/AppBar";
import createAccentedTheme from "../../../accentedTheme";
import { ThemeProvider } from "@mui/material/styles";
import { COLOR } from "./Sidebar";

function Header(): Node {
  const { uiStore } = useStores();

  const handleToggleOpen = () => {
    uiStore.toggleSidebar();
  };

  return (
    <ThemeProvider theme={createAccentedTheme(COLOR)}>
      <AppBar
        currentPage="Inventory"
        appliedSearchTerm=""
        setAppliedSearchTerm={() => {}}
        hideSearch={true}
        setDrawerOpen={handleToggleOpen}
        drawerOpen={uiStore.sidebarOpen}
        sidebarId={"foo"}
      />
    </ThemeProvider>
  );
}

export default (observer(Header): ComponentType<{||}>);
