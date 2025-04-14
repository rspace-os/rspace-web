import React from "react";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import IgsnTable from "./IgsnTable";

export default function IgsnTableStory() {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <IgsnTable selectedIgsns={[]} setSelectedIgsns={() => {}} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
