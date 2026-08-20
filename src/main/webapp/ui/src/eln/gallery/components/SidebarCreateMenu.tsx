import { paperClasses } from "@mui/material/Paper";
import type React from "react";
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
          disablePadding: true,
        },
      }}
    >
      {children}
    </Menu>
  );
}
