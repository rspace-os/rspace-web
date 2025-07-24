import React from "react";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../theme";
import FieldmarkImportDialog from "./FieldmarkImportDialog";
import AlertContext from "../../stores/contexts/Alert";
import ConfirmProvider from "../../components/ConfirmProvider";

export function OpenFieldmarkImportDialog() {
  const mockAlertContext = {
    addAlert: () => {},
    removeAlert: () => {},
  };

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <AlertContext.Provider value={mockAlertContext}>
          <ConfirmProvider>
            <FieldmarkImportDialog open={true} onClose={() => {}} />
          </ConfirmProvider>
        </AlertContext.Provider>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

export function ClosedFieldmarkImportDialog() {
  const mockAlertContext = {
    addAlert: () => {},
    removeAlert: () => {},
  };

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <AlertContext.Provider value={mockAlertContext}>
          <ConfirmProvider>
            <FieldmarkImportDialog open={false} onClose={() => {}} />
          </ConfirmProvider>
        </AlertContext.Provider>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
