import React from "react";
import ImportDialog from "./ImportDialog";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ACCENT_COLOR } from "../../assets/branding/pubchem";
import createAccentedTheme from "../../accentedTheme";

/**
 * A basic example of how to use the ImportDialog component.
 */
export function ImportDialogStory(): React.ReactNode {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <h1>PubChem import example</h1>
        <ImportDialog open onClose={() => {}} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
