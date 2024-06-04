//@flow

import React from "react";
import { createRoot } from "react-dom/client";
import ErrorBoundary from "../../components/ErrorBoundary";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import { COLOR } from "./common";
import AppBar from "./components/AppBar";
import Sidebar from "./components/Sidebar";
import MainPanel from "./components/MainPanel";
import Box from "@mui/material/Box";
import useGalleryListing, { type GalleryFile } from "./useGalleryListing";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import CssBaseline from "@mui/material/CssBaseline";
import useViewportDimensions from "../../util/useViewportDimensions";

function WholePage() {
  const [appliedSearchTerm, setAppliedSearchTerm] = React.useState("");
  const [selectedSection, setSelectedSection] = React.useState("Chemistry");
  const { galleryListing, path, clearPath } = useGalleryListing({
    section: selectedSection,
    searchTerm: appliedSearchTerm,
  });
  const [selectedFile, setSelectedFile] = React.useState<GalleryFile | null>(
    null
  );
  const viewport = useViewportDimensions();
  const [drawerOpen, setDrawerOpen] = React.useState(!viewport.isViewportSmall);

  return (
    <>
      <AppBar
        appliedSearchTerm={appliedSearchTerm}
        setAppliedSearchTerm={setAppliedSearchTerm}
        setDrawerOpen={setDrawerOpen}
        drawerOpen={drawerOpen}
      />
      <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
        <Sidebar
          selectedSection={selectedSection}
          setSelectedSection={setSelectedSection}
          drawerOpen={drawerOpen}
        />
        <Box
          sx={{
            height: "100%",
            display: "flex",
            flexDirection: "column",
            flexGrow: 1,
          }}
        >
          <MainPanel
            selectedSection={selectedSection}
            path={path}
            clearPath={clearPath}
            galleryListing={galleryListing}
            selectedFile={selectedFile}
            setSelectedFile={setSelectedFile}
          />
        </Box>
      </Box>
    </>
  );
}

window.addEventListener("load", () => {
  const domContainer = document.getElementById("app");
  if (domContainer) {
    window.scrollTo(0, 1);

    const root = createRoot(domContainer);
    root.render(
      <React.StrictMode>
        <ErrorBoundary>
          <StyledEngineProvider injectFirst>
            <CssBaseline />
            <ThemeProvider theme={createAccentedTheme(COLOR)}>
              <WholePage />
            </ThemeProvider>
          </StyledEngineProvider>
        </ErrorBoundary>
      </React.StrictMode>
    );
  }
});
