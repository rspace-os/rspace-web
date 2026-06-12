import Badge, { badgeClasses } from "@mui/material/Badge";
import ListItem from "@mui/material/ListItem";
import ListItemButton, { listItemButtonClasses } from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import { alpha, darken } from "@mui/system";
import React from "react";

type DrawerTabProps = {
  drawerOpen: boolean;
  icon: React.ReactNode;
  label: React.ReactNode;
  index: number;
  selected: boolean;
  onClick: () => void;
  tabIndex: number;
  badge?: React.ReactNode;
};
const DrawerTab = React.forwardRef<HTMLDivElement, DrawerTabProps>(
  ({ icon, label, index, selected, onClick, tabIndex, badge, drawerOpen }, ref) => (
    <ListItem disablePadding sx={{ position: "static" }}>
      <ListItemButton
        selected={selected}
        onClick={onClick}
        tabIndex={tabIndex}
        ref={ref}
        sx={(theme) => ({
          "&:hover": {
            backgroundColor: alpha(theme.palette.primary.background, 0.25),
          },
          [`&.${listItemButtonClasses.selected}`]: {
            "&:hover": {
              backgroundColor: darken(theme.palette.primary.background, 0.1),
            },
          },
        })}
      >
        <ListItemIcon>{icon}</ListItemIcon>
        <ListItemText
          primary={label}
          sx={{
            transition: window.matchMedia("(prefers-reduced-motion: reduce)").matches
              ? "none"
              : "all .2s cubic-bezier(0.4, 0, 0.2, 1)",
            opacity: drawerOpen ? 1 : 0,
            transform: drawerOpen ? "unset" : "translateX(-20px)",
            textTransform: "uppercase",
            transitionDelay: `${(index + 1) * 0.02}s !important`,
          }}
        />
        {badge !== undefined && badge !== null && badge !== 0 && (
          <Badge
            badgeContent={badge}
            color="primary"
            max={999}
            sx={{
              marginLeft: "auto",
              transition: window.matchMedia("(prefers-reduced-motion: reduce)").matches
                ? "none"
                : "all .2s cubic-bezier(0.4, 0, 0.2, 1)",
              opacity: drawerOpen ? 1 : 0,
              transform: drawerOpen ? "unset" : "translateX(20px)",
              [`& .${badgeClasses.badge}`]: {
                position: "static",
                transform: "none",
                minWidth: "20px",
                height: "20px",
                fontSize: "0.75rem",
              },
            }}
          />
        )}
      </ListItemButton>
    </ListItem>
  ),
);
DrawerTab.displayName = "DrawerTab";

export default DrawerTab;
export type { DrawerTabProps };
