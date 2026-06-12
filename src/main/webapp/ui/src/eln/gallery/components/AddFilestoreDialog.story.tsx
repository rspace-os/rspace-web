import { ThemeProvider } from "@mui/material/styles";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { LandmarksProvider } from "@/components/LandmarksContext";
import AddFilestoreDialog from "./AddFilestoreDialog";

export function AddFilestoreDialogStory() {
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <LandmarksProvider>
        <AddFilestoreDialog open={true} onClose={() => {}} />
      </LandmarksProvider>
    </ThemeProvider>
  );
}
