import React from "react";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../theme";
import FieldmarkImportDialog from "./FieldmarkImportDialog";
import ConfirmProvider from "../../components/ConfirmProvider";
import Alerts from "../../components/Alerts/Alerts";

export function FieldmarkImportDialogStory() {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Alerts>
          <ConfirmProvider>
            <FieldmarkImportDialog open={true} onClose={() => {}} />
          </ConfirmProvider>
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
