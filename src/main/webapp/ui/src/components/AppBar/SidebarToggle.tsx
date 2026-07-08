import MenuIcon from "@mui/icons-material/Menu";
import IconButton from "@mui/material/IconButton";
import type React from "react";
import { useTranslation } from "react-i18next";

type SidebarToggleArgs = {
  sidebarOpen: boolean;
  sidebarId: string;
  setSidebarOpen: (open: boolean) => void;
};

/**
 * A hamburger menu icon that sits in the AppBar for toggling the left-hand
 * sidebar.
 */
export default function SidebarToggle({ sidebarOpen, sidebarId, setSidebarOpen }: SidebarToggleArgs): React.ReactNode {
  const { t } = useTranslation("common");
  const label = sidebarOpen ? t("appBar.closeSidebar") : t("appBar.openSidebar");
  return (
    <IconButton
      aria-label={label}
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
