import React from "react";
import { LandmarksProvider, useLandmark } from "./LandmarksContext";
import SkipToContentButton from "./SkipToContentButton";
import { Box, Typography } from "@mui/material";

const TestLandmark = ({ name }: { name: string }) => {
  const ref = useLandmark(name);
  return (
    <Box
      ref={ref}
      tabIndex={-1}
      sx={{
        height: 150,
        border: 1,
        p: 2,
        m: 1,
      }}
    >
      <Typography variant="h6">{name} Content</Typography>
    </Box>
  );
};

/**
 * Basic example with multiple landmarks
 */
export function BasicSkipToContentExample() {
  return (
    <LandmarksProvider>
      <SkipToContentButton />
      <Box sx={{ mt: 5 }}>
        <TestLandmark name="Main Navigation" />
        <TestLandmark name="Main Content" />
        <TestLandmark name="Search Results" />
        <TestLandmark name="Footer" />
      </Box>
    </LandmarksProvider>
  );
}

/**
 * Example with dynamic landmark registration
 */
export function DynamicLandmarksExample() {
  const [showExtraLandmarks, setShowExtraLandmarks] = React.useState(false);

  return (
    <LandmarksProvider>
      <SkipToContentButton />
      <Box sx={{ mt: 5 }}>
        <button onClick={() => setShowExtraLandmarks(!showExtraLandmarks)}>
          {showExtraLandmarks ? 'Hide' : 'Show'} Extra Landmarks
        </button>
        <TestLandmark name="Header" />
        <TestLandmark name="Main Content" />
        {showExtraLandmarks && (
          <>
            <TestLandmark name="Sidebar" />
            <TestLandmark name="Comments" />
          </>
        )}
        <TestLandmark name="Footer" />
      </Box>
    </LandmarksProvider>
  );
}

/**
 * Simple test component for specs
 */
export function SimpleTestExample() {
  return (
    <LandmarksProvider>
      <SkipToContentButton />
      <TestLandmark name="Header" />
      <TestLandmark name="Footer" />
    </LandmarksProvider>
  );
}
