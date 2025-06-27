import React from "react";
import ImportDialog from "./ImportDialog";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ACCENT_COLOR } from "../../assets/branding/pubchem";
import createAccentedTheme from "../../accentedTheme";
import ErrorBoundary from "@/components/ErrorBoundary";

/**
 * A basic example of how to use the ImportDialog component.
 */
export function ImportDialogStory(): React.ReactNode {
  const [open, setOpen] = React.useState(true);
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <ErrorBoundary>
          <h1>PubChem import example</h1>
          <ImportDialog
            open={open}
            onClose={() => {
              setOpen(false);
            }}
          />
        </ErrorBoundary>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
