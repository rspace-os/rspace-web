import React from "react";
import ImportDialog from "./ImportDialog";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ACCENT_COLOR } from "../../assets/branding/pubchem";
import createAccentedTheme from "../../accentedTheme";
import ErrorBoundary from "@/components/ErrorBoundary";
import Alerts from "@/components/Alerts/Alerts";

/**
 * A basic example of how to use the ImportDialog component.
 */
export function ImportDialogStory(): React.ReactNode {
  const [open, setOpen] = React.useState(true);
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <ErrorBoundary>
          <Alerts>
            <h1>PubChem import example</h1>
            <button onClick={() => setOpen(true)}>Open</button>
            <ImportDialog
              open={open}
              onClose={() => {
                setOpen(false);
              }}
              editor={{
                id: "rtf_1",
                ui: {
                  registry: {
                    addMenuItem: (menuItemIdentifier, options) => {},
                    addButton: (buttonIdentifier, options) => {},
                  },
                },
                execCommand: (command, ui, value) => {
                  console.log(`Executed command: ${command}`, { ui, value });
                },
              }}
            />
          </Alerts>
        </ErrorBoundary>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
