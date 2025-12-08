import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import Alerts from "@/components/Alerts/Alerts";
import ErrorBoundary from "@/components/ErrorBoundary";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/pubchem";
import ImportDialog from "./ImportDialog";

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
                                        addMenuItem: (_menuItemIdentifier, _options) => {},
                                        addButton: (_buttonIdentifier, _options) => {},
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
