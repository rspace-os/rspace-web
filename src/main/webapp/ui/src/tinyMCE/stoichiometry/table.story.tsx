import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import type React from "react";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/chemistry";
import Alerts from "../../components/Alerts/Alerts";
import StoichiometryTable from "./table";

export function StoichiometryTableWithDataStory(): React.ReactNode {
    return (
        <StyledEngineProvider injectFirst>
            <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
                <Alerts>
                    <StoichiometryTable stoichiometryId={1} stoichiometryRevision={1} editable />
                </Alerts>
            </ThemeProvider>
        </StyledEngineProvider>
    );
}
