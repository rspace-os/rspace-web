import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { ThemeProvider } from "@mui/material/styles";
import React from "react";
import AddFilestoreDialog from "./AddFilestoreDialog";
import { LandmarksProvider } from "@/components/LandmarksContext";

export function AddFilestoreDialogStory() {
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <LandmarksProvider>
        <AddFilestoreDialog open={true} onClose={() => {}} />
      </LandmarksProvider>
    </ThemeProvider>
  );
}
