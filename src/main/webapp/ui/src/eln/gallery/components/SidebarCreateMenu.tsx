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
        /*
         * In production builds (-DgenerateReactDist) a re-render during this
         * Menu's exit can cancel react-transition-group's onExited
         * (mui/material-ui#32286), leaving the Modal mounted with its
         * invisible backdrop still intercepting every click -- the page
         * freezes until reload. Since we cannot make onExited fire reliably,
         * make the closed menu click-through instead. pointer-events is
         * inherited and MUI sets it on neither backdrop nor paper, so this
         * root rule covers both; the open menu (anchorEl set) is unaffected.
         */
        ...(anchorEl ? {} : { pointerEvents: "none" }),
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
