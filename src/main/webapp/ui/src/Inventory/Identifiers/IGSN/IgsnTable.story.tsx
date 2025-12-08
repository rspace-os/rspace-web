import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import materialTheme from "../../../theme";
import RsSet from "../../../util/set";
import type { Identifier } from "../../useIdentifiers";
import IgsnTable from "./IgsnTable";

export function SimpleIgsnTable() {
    const [selectedIgsns, setSelectedIgsns] = React.useState<RsSet<Identifier>>(new RsSet([]));
    return (
        <StyledEngineProvider injectFirst>
            <ThemeProvider theme={materialTheme}>
                <h1>IGSN Table</h1>
                <IgsnTable selectedIgsns={selectedIgsns} setSelectedIgsns={setSelectedIgsns} />
                <h2>Selected IGSNs</h2>
                <ul aria-label="selected IGSNs">
                    {[...selectedIgsns].map((igsn) => (
                        <li key={igsn.id}>{igsn.doi}</li>
                    ))}
                </ul>
            </ThemeProvider>
        </StyledEngineProvider>
    );
}

export function SingularSelectionIgsnTable() {
    const [selectedIgsns, setSelectedIgsns] = React.useState<RsSet<Identifier>>(new RsSet([]));
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
                    {[...selectedIgsns].map((igsn) => (
                        <li key={igsn.id}>{igsn.doi}</li>
                    ))}
                </ul>
            </ThemeProvider>
        </StyledEngineProvider>
    );
}

export function IgsnTableWithControlDefaults() {
    const [selectedIgsns, setSelectedIgsns] = React.useState<RsSet<Identifier>>(new RsSet([]));
    return (
        <StyledEngineProvider injectFirst>
            <ThemeProvider theme={materialTheme}>
                <h1>IGSN Table with Control Defaults</h1>
                <IgsnTable
                    selectedIgsns={selectedIgsns}
                    setSelectedIgsns={setSelectedIgsns}
                    controlDefaults={{
                        state: "draft",
                        isAssociated: false,
                        searchTerm: "test",
                    }}
                />
                <h2>Selected IGSNs</h2>
                <ul aria-label="selected IGSNs">
                    {[...selectedIgsns].map((igsn) => (
                        <li key={igsn.id}>{igsn.doi}</li>
                    ))}
                </ul>
            </ThemeProvider>
        </StyledEngineProvider>
    );
}
