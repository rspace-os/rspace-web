//@flow

import React, { type Node, type ComponentType } from "react";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import IconButton from "@mui/material/IconButton";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import MenuIcon from "@mui/icons-material/Menu";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import AccessibilityTips from "../../../components/AccessibilityTips";
import HelpDocs from "../../../components/Help/HelpDocs";
import HelpIcon from "@mui/icons-material/Help";
import { observer } from "mobx-react-lite";

type GalleryAppBarArgs = {|
  setDrawerOpen: (boolean) => void,
  drawerOpen: boolean,
  sidebarId: string,
|};

function GalleryAppBar({
  setDrawerOpen,
  drawerOpen,
  sidebarId,
}: GalleryAppBarArgs): Node {
  return (
    <AppBar position="relative" open={true} aria-label="page header">
      <Toolbar variant="dense">
        <IconButton
          aria-label={drawerOpen ? "close drawer" : "open drawer"}
          aria-controls={sidebarId}
          aria-expanded={drawerOpen ? "true" : "false"}
          onClick={() => {
            setDrawerOpen(!drawerOpen);
          }}
        >
          <MenuIcon />
        </IconButton>
        <Typography variant="h6" noWrap component="h2">
          Gallery
        </Typography>
        <Box flexGrow={1}></Box>
        <Box ml={1}>
          <AccessibilityTips
            elementType="dialog"
            supportsReducedMotion
            supportsHighContrastMode
            supports2xZoom
          />
        </Box>
        <Box ml={1}>
          <HelpDocs
            Action={({ onClick, disabled }) => (
              <IconButtonWithTooltip
                size="small"
                onClick={onClick}
                icon={<HelpIcon />}
                title="Open Help"
                disabled={disabled}
              />
            )}
          />
        </Box>
      </Toolbar>
    </AppBar>
  );
}

/**
 * GalleryAppBar is the header bar for the gallery page.
 */
export default (observer(GalleryAppBar): ComponentType<GalleryAppBarArgs>);
