import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { ThemeProvider } from "@mui/material/styles";
import React from "react";
import AddFilestoreDialog from "./AddFilestoreDialog";

export function AddFilestoreDialogStory() {
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <AddFilestoreDialog open={true} onClose={() => {}} />
    </ThemeProvider>
  );
}
