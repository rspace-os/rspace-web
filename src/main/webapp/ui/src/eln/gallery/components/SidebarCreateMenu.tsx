import { paperClasses } from "@mui/material/Paper";
import type React from "react";
import { useTranslation } from "react-i18next";
import { Menu } from "../../../components/DialogBoundary";

type SidebarCreateMenuArgs = {
  anchorEl: HTMLElement | null;
  onClose: () => void;
  children: React.ReactNode;
};

/**
 * The Gallery sidebar's create menu.
 */
export default function SidebarCreateMenu({ anchorEl, onClose, children }: SidebarCreateMenuArgs): React.ReactNode {
  const { t } = useTranslation("common");

  return (
    <Menu
      open={Boolean(anchorEl)}
      anchorEl={anchorEl}
      onClose={onClose}
      sx={{
        [`& .${paperClasses.root}`]: {
          ...(anchorEl ? { transform: "translate(-4px, 4px) !important" } : {}),
        },
      }}
      slotProps={{
        list: {
          "aria-label": t("actions.create"),
          disablePadding: true,
        },
      }}
    >
      {children}
    </Menu>
  );
}
