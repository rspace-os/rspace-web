import React from "react";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/chemistry";
import StoichiometryTable from "./table";
import Alerts from "../../components/Alerts/Alerts";

export function StoichiometryTableWithDataStory(): React.ReactNode {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <Alerts>
          <StoichiometryTable
            stoichiometryId={1}
            stoichiometryRevision={1}
            editable
          />
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
