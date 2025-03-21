import React from "react";
import IconButton from "@mui/material/IconButton";
import MenuIcon from "@mui/icons-material/Menu";

type SidebarToggleArgs = {
  sidebarOpen: boolean;
  sidebarId: string;
  setSidebarOpen: (open: boolean) => void;
};

/**
 * A hamburger menu icon that sits in the AppBar for toggling the left-hand
 * sidebar.
 */
export default function SidebarToggle({
  sidebarOpen,
  sidebarId,
  setSidebarOpen,
}: SidebarToggleArgs): React.ReactNode {
  return (
    <IconButton
      aria-label={sidebarOpen ? "close sidebar" : "open sidebar"}
      aria-controls={sidebarId}
      aria-expanded={sidebarOpen ? "true" : "false"}
      onClick={() => {
        setSidebarOpen(!sidebarOpen);
      }}
    >
      <MenuIcon />
    </IconButton>
  );
}
