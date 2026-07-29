import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import Alerts from "@/components/Alerts/Alerts";
import ErrorBoundary from "@/components/ErrorBoundary";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/pyrat";
import PyratDialog from "./PyratDialog";

/**
 * Renders the PyRAT dialog exactly as the TinyMCE plugin does, minus the
 * plugin glue. With a single configured server the dialog auto-selects it and
 * mounts the animal listing, which is where the pagination logic lives.
 */
export function PyratDialogStory(): React.ReactNode {
  const [open, setOpen] = React.useState(true);
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <ErrorBoundary>
          <Alerts>
            <button type="button" onClick={() => setOpen(true)}>
              {"Open"}
            </button>
            <PyratDialog
              open={open}
              onClose={() => {
                setOpen(false);
              }}
              editor={{
                id: "rtf_1",
                ui: {
                  registry: {
                    addButton: () => {},
                    addMenuItem: () => {},
                  },
                },
                execCommand: (command: string, ui: boolean, value?: string) => {
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
