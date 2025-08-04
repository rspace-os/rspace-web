import React, { useState, useRef } from "react";
import Box from "@mui/material/Box";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import { useLandmarksList } from "./LandmarksContext";
import useOneDimensionalRovingTabIndex from "./useOneDimensionalRovingTabIndex";

const SkipToContentButton: React.FC = () => {
  const { landmarks } = useLandmarksList();
  const [isVisible, setIsVisible] = useState(false);

  const { getTabIndex, getRef, eventHandlers } =
    useOneDimensionalRovingTabIndex<HTMLDivElement>({
      max: landmarks.length - 1,
      direction: "column",
    });

  const handleFocus = () => {
    setIsVisible(true);
  };

  const handleSkipToLandmark = (ref: React.RefObject<HTMLElement>) => {
    if (ref.current) {
      // Ensure the element can receive focus
      if (ref.current.tabIndex === -1) {
        ref.current.tabIndex = -1;
      }
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
        backgroundColor: "background.paper",
        border: 1,
        borderColor: "divider",
        borderRadius: 1,
        boxShadow: 2,
        minWidth: 200,
      }}
      onFocus={eventHandlers.onFocus}
      onBlur={eventHandlers.onBlur}
      onKeyDown={handleKeyDown}
    >
      <List
        dense
        sx={{ opacity: isVisible ? 1 : 0 }}
        role="menu"
        aria-label="Skip to content navigation"
      >
        {landmarks.map((landmark, index) => (
          <ListItem key={landmark.name} disablePadding role="menuitem">
            <ListItemButton
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
              <ListItemText primary={`Skip to ${landmark.name}`} />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
    </Box>
  );
};

export default SkipToContentButton;
