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
 *
 * A closed menu renders nothing, so a cancelled exit transition
 * (mui/material-ui#32286) cannot leave it painted and undismissable.
 * See PRT-1118 and PRT-1135.
 *
 * ponytail: this gives up the close animation.
 */
export default function SidebarCreateMenu({ anchorEl, onClose, children }: SidebarCreateMenuArgs): React.ReactNode {
  if (!anchorEl) return null;
  return (
    <Menu
      open
      anchorEl={anchorEl}
      onClose={onClose}
      sx={{
        [`& .${paperClasses.root}`]: {
          transform: "translate(-4px, 4px) !important",
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
