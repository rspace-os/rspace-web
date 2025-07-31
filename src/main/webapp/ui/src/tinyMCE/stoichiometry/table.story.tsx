import React from "react";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../theme";
import StoichiometryTable from "./table";
import Alerts from "@/Inventory/components/Alerts";

export function StoichiometryTableWithDataStory(): React.ReactNode {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Alerts>
          <StoichiometryTable chemId={12345} />
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
