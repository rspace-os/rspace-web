import React from "react";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/chemistry";
import StoichiometryTable from "./table";
import Alerts from "@/Inventory/components/Alerts";

export function StoichiometryTableWithDataStory(): React.ReactNode {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <Alerts>
          <StoichiometryTable chemId={12345} />
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
