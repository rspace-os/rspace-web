import Box from "@mui/material/Box";
import type React from "react";

/**
 * Simple label for displaying a message about the status of the listing e.g.
 * error messages, indicating that there are no files, etc.
 */
export default function PlaceholderLabel({ children }: { children: React.ReactNode }): React.ReactNode {
  return (
    <Box
      sx={{
        display: "flex",
        justifyContent: "stretch",
        alignItems: "stretch",
        height: "100%",
        "& > *": {
          fontSize: "2rem",
          fontWeight: 700,
          color: window.matchMedia("(prefers-contrast: more)").matches ? "black" : "hsl(190deg, 20%, 29%, 37%)",
          flexGrow: 1,
          textAlign: "center",
          overflowWrap: "anywhere",
          overflow: "hidden",
        },
      }}
    >
      <Box
        sx={{
          p: 1,
          pt: 2,
          pr: 5,
        }}
        role="status"
      >
        {children}
      </Box>
    </Box>
  );
}
