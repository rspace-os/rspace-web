import React from "react";
import { styled } from "@mui/material/styles";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import ListItemIcon from "@mui/material/ListItemIcon";
import Badge from "@mui/material/Badge";
import { darken } from "@mui/system";
import { ACCENT_COLOR } from "../assets/branding/rspace/gallery";

type DrawerTabProps = {
  drawerOpen: boolean;
  icon: React.ReactNode;
  label: React.ReactNode;
  index: number;
  className?: string;
  selected: boolean;
  onClick: () => void;
  tabIndex: number;
  badge?: React.ReactNode;
};

const DrawerTab = styled(
  React.forwardRef<HTMLDivElement, DrawerTabProps>(
    (
      {
        icon,
        label,
        index,
        className,
        selected,
        onClick,
        tabIndex,
        badge,
        drawerOpen,
      },
      ref: React.ForwardedRef<HTMLDivElement>,
    ) => (
      <ListItem disablePadding className={className}>
        <ListItemButton
          selected={selected}
          onClick={onClick}
          tabIndex={tabIndex}
          ref={ref}
        >
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
                transition: window.matchMedia(
                  "(prefers-reduced-motion: reduce)",
                ).matches
                  ? "none"
                  : "all .2s cubic-bezier(0.4, 0, 0.2, 1)",
                opacity: drawerOpen ? 1 : 0,
                transform: drawerOpen ? "unset" : "translateX(20px)",
                "& .MuiBadge-badge": {
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
  ),
)(({ drawerOpen }) => ({
  position: "static",
  "& .MuiListItemText-root": {
    transition: window.matchMedia("(prefers-reduced-motion: reduce)").matches
      ? "none"
      : "all .2s cubic-bezier(0.4, 0, 0.2, 1)",
    opacity: drawerOpen ? 1 : 0,
    transform: drawerOpen ? "unset" : "translateX(-20px)",
    textTransform: "uppercase",
  },
  "& .MuiListItemButton-root": {
    "&:hover": {
      backgroundColor: darken(
        `hsl(${ACCENT_COLOR.background.hue}deg, ${ACCENT_COLOR.background.saturation}%, 100%)`,
        0.05,
      ),
    },
    "&.Mui-selected": {
      "&:hover": {
        backgroundColor: darken(
          `hsl(${ACCENT_COLOR.background.hue}deg, ${ACCENT_COLOR.background.saturation}%, 100%)`,
          0.05,
        ),
      },
    },
  },
}));

export default DrawerTab;
export type { DrawerTabProps };
