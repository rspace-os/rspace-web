import React, { useState, useRef } from "react";
import {
  Button,
  Box,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
} from "@mui/material";
import { useLandmarksList } from "./LandmarksContext";

const SkipToContentButton: React.FC = () => {
  const { landmarks } = useLandmarksList();
  const [isVisible, setIsVisible] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const handleFocus = () => {
    setIsVisible(true);
  };

  const handleBlur = (e: React.FocusEvent) => {
    // Only hide if focus is moving outside the container
    // if (!containerRef.current?.contains(e.relatedTarget as Node)) {
    //   setIsVisible(false);
    // }
  };

  const handleSkipToLandmark = (ref: React.RefObject<HTMLElement>) => {
    console.debug(
      "Skipping to landmark:",
      ref.current?.id || "unknown landmark",
    );
    if (ref.current) {
      // Ensure the element can receive focus
      if (ref.current.tabIndex === -1) {
        ref.current.tabIndex = -1;
      }
      ref.current.focus({ preventScroll: false });
      setIsVisible(false);
    }
  };

  if (landmarks.length === 0) {
    return null;
  }

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
    >
      <Button
        onFocus={handleFocus}
        onBlur={handleBlur}
        sx={{
          position: "absolute",
          top: isVisible ? 0 : -40,
          left: 0,
          width: "100%",
          opacity: 0,
          pointerEvents: isVisible ? "auto" : "none",
        }}
        tabIndex={0}
      >
        Skip to content
      </Button>

      {isVisible && (
        <List dense>
          {landmarks.map((landmark) => (
            <ListItem key={landmark.name} disablePadding>
              <ListItemButton
                onClick={() => {
                  handleSkipToLandmark(landmark.ref);
                }}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    handleSkipToLandmark(landmark.ref);
                  }
                }}
              >
                <ListItemText primary={`Skip to ${landmark.name}`} />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
      )}
    </Box>
  );
};

export default SkipToContentButton;
