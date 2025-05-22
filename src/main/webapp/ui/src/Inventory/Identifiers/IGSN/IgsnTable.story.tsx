import React from "react";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import IgsnTable from "./IgsnTable";
import { type Identifier } from "../../useIdentifiers";

export function SimpleIgsnTable() {
  const [selectedIgsns, setSelectedIgsns] = React.useState<
    ReadonlyArray<Identifier>
  >([]);
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <h1>IGSN Table</h1>
        <IgsnTable
          selectedIgsns={selectedIgsns}
          setSelectedIgsns={setSelectedIgsns}
        />
        <h2>Selected IGSNs</h2>
        <ul aria-label="selected IGSNs">
          {selectedIgsns.map((igsn) => (
            <li key={igsn.id}>{igsn.doi}</li>
          ))}
        </ul>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

export function SingularSelectionIgsnTable() {
  const [selectedIgsns, setSelectedIgsns] = React.useState<
    ReadonlyArray<Identifier>
  >([]);
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <h1>IGSN Table</h1>
        <IgsnTable
          selectedIgsns={selectedIgsns}
          setSelectedIgsns={setSelectedIgsns}
          disableMultipleRowSelection
        />
        <h2>Selected IGSNs</h2>
        <ul aria-label="selected IGSNs">
          {selectedIgsns.map((igsn) => (
            <li key={igsn.id}>{igsn.doi}</li>
          ))}
        </ul>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
