import React from "react";
import ListItem from "@mui/material/ListItem";
import ListItemButton, {
  listItemButtonClasses,
} from "@mui/material/ListItemButton";
import ListItemText, { listItemTextClasses } from "@mui/material/ListItemText";
import ListItemIcon from "@mui/material/ListItemIcon";
import Badge, { badgeClasses } from "@mui/material/Badge";
import { darken, alpha } from "@mui/system";

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
  (
    {
      icon,
      label,
      index,
      selected,
      onClick,
      tabIndex,
      badge,
      drawerOpen,
    },
    ref,
  ) => (
    <ListItem
      disablePadding
      sx={(theme) => ({
        position: "static",
        [`& .${listItemTextClasses.root}`]: {
          transition: window.matchMedia("(prefers-reduced-motion: reduce)").matches
            ? "none"
            : "all .2s cubic-bezier(0.4, 0, 0.2, 1)",
          opacity: drawerOpen ? 1 : 0,
          transform: drawerOpen ? "unset" : "translateX(-20px)",
          textTransform: "uppercase",
        },
        [`& .${listItemButtonClasses.root}`]: {
          "&:hover": {
            backgroundColor: alpha(theme.palette.primary.background, 0.25),
          },
          [`&.${listItemButtonClasses.selected}`]: {
            "&:hover": {
              backgroundColor: darken(theme.palette.primary.background, 0.1),
            },
          },
        },
      })}
    >
      <ListItemButton selected={selected} onClick={onClick} tabIndex={tabIndex} ref={ref}>
        <ListItemIcon>{icon}</ListItemIcon>
        <ListItemText
          primary={label}
          sx={{ transitionDelay: `${(index + 1) * 0.02}s !important` }}
        />
        {badge !== undefined && badge !== null && badge !== 0 && (
          <Badge
            badgeContent={badge}
            color="primary"
            max={999}
            sx={{
              marginLeft: "auto",
              transition: window.matchMedia("(prefers-reduced-motion: reduce)")
                .matches
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
