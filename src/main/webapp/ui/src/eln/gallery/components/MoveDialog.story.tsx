import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { ThemeProvider } from "@mui/material/styles";
import React from "react";
import MoveDialog from "./MoveDialog";

export function MoveDialogStory() {
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <MoveDialog
        open={true}
        onClose={() => {}}
        section="Images"
        refreshListing={() => Promise.resolve()}
      />
    </ThemeProvider>
  );
}
