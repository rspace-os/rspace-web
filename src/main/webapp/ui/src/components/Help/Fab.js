//@flow strict

import React, { type Node } from "react";
import HelpDocs from "./HelpDocs";
import Fab from "@mui/material/Fab";
import HelpOutlineIcon from "@mui/icons-material/HelpOutline";
import Zoom from "@mui/material/Zoom";
import { useTheme } from "@mui/material/styles";

/**
 * This components provides a Floating Action Button (FAB) for opening
 * the HelpDocs help panel. Must be rendered inside of an Analytics
 * context.
 */
export default function HelpFab(): Node {
  const theme = useTheme();

  return (
    <HelpDocs
      Action={({ onClick, disabled }) => (
        <Zoom
          in={!disabled}
          timeout={{
            enter: window.matchMedia("(prefers-reduced-motion: reduce)").matches
              ? "0s"
              : theme.transitions.duration.enteringScreen,
          }}
          unmountOnExit
        >
          <Fab
            onClick={onClick}
            disabled={disabled}
            aria-label="Open help panel"
            color="primary"
            // positioned to be under the close button when the popup is open
            sx={{ position: "fixed", bottom: 28, right: 28, zIndex: 1400 }}
          >
            <HelpOutlineIcon fontSize="large" />
          </Fab>
        </Zoom>
      )}
    />
  );
}
