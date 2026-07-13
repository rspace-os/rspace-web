import Box from "@mui/material/Box";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import useOneDimensionalRovingTabIndex from "../hooks/ui/useOneDimensionalRovingTabIndex";
import { useLandmarksList } from "./LandmarksContext";

const SkipToContentButton: React.FC = () => {
  const { t } = useTranslation("common");
  const { landmarks } = useLandmarksList();
  const [isVisible, setIsVisible] = useState(false);

  const { getTabIndex, getRef, eventHandlers } = useOneDimensionalRovingTabIndex<HTMLDivElement>({
    max: landmarks.length - 1,
    direction: "column",
  });

  const handleFocus = () => {
    setIsVisible(true);
  };

  const handleSkipToLandmark = (ref: React.RefObject<HTMLElement | null>) => {
    if (ref.current) {
      /*
       * Setting tabIndex to -1 (even if already -1) forces the browser to
       * re-evaluate focusability. This is necessary for reliable programmatic
       * focus due to React/MUI/browser quirks.
       */
      ref.current.tabIndex = -1;
      ref.current.focus({ preventScroll: false });
      setIsVisible(false);
    }
  };

  const handleClose = () => {
    setIsVisible(false);
    document.body.focus();
  };

  if (landmarks.length === 0) {
    return null;
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLElement>) => {
    if (e.key === "Escape") {
      e.preventDefault();
      handleClose();
    } else {
      // Pass through to the roving tab index handler
      eventHandlers.onKeyDown(e);
    }
  };

  return (
    <Box
      sx={{
        position: "absolute",
        top: (theme) => theme.spacing(1),
        left: (theme) => theme.spacing(1),
        height: "initial !important",
        zIndex: 9999,
        transform: isVisible ? "translateY(0)" : "translateY(-100%)",
        opacity: isVisible ? 1 : 0,
        transition: "transform 0.2s ease-in-out, opacity 0.2s ease-in-out",
        "@media (prefers-reduced-motion: reduce)": {
          transition: "none",
        },
        backgroundColor: "background.paper",
        border: 1,
        borderColor: "divider",
        // Match the ListItemButton radius (8px, set by the theme) so the
        // container and item corners line up.
        borderRadius: "8px",
        boxShadow: 2,
        minWidth: 200,
      }}
      onFocus={eventHandlers.onFocus}
      onBlur={eventHandlers.onBlur}
      onKeyDown={handleKeyDown}
    >
      <List
        dense
        disablePadding
        role="menu"
        aria-label={t("accessibilityTips.skipToContent.navigation")}
        sx={{ display: "flex", flexDirection: "column", gap: 1 }}
      >
        {landmarks.map((landmark, index) => (
          <ListItem key={landmark.name} disablePadding role="menuitem">
            <ListItemButton
              // The app theme adds a border to every ListItemButton; the skip
              // menu already has its own container border, so drop it here.
              sx={{ border: "none" }}
              onClick={() => {
                handleSkipToLandmark(landmark.ref);
              }}
              tabIndex={getTabIndex(index)}
              ref={getRef(index)}
              onFocus={handleFocus}
              onKeyDown={(e: React.KeyboardEvent<HTMLElement>) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  handleSkipToLandmark(landmark.ref);
                } else if (e.key === "Escape") {
                  e.preventDefault();
                  handleClose();
                }
              }}
            >
              <ListItemText
                primary={t("accessibilityTips.skipToContent.skipToLandmark", { landmark: landmark.name })}
              />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
    </Box>
  );
};

export default SkipToContentButton;
