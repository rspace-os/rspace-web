//@flow

import React from "react";
import { createRoot } from "react-dom/client";
import ErrorBoundary from "../../components/ErrorBoundary";
import { ThemeProvider, styled, lighten } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import {
  COLOR,
  SELECTED_OR_FOCUS_BLUE,
  parseGallerySectionFromUrlSearchParams,
  GALLERY_SECTION,
} from "./common";
import AppBar from "./components/AppBar";
import Sidebar from "./components/Sidebar";
import MainPanel from "./components/MainPanel";
import Box from "@mui/material/Box";
import { useGalleryListing, type GalleryFile } from "./useGalleryListing";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import CssBaseline from "@mui/material/CssBaseline";
import useViewportDimensions from "../../util/useViewportDimensions";
import Alerts from "../../Inventory/components/Alerts";
import { DisableDragAndDropByDefault } from "../../components/useFileImportDragAndDrop";
import Analytics from "../../components/Analytics";
import { GallerySelection } from "./useGallerySelection";
import { BrowserRouter, Navigate, useSearchParams } from "react-router-dom";
import { Routes, Route } from "react-router";

const WholePage = styled(() => {
  const [searchParams] = useSearchParams();
  const [selectedSection, setSelectedSection] = React.useState(
    parseGallerySectionFromUrlSearchParams(searchParams).orElse(
      GALLERY_SECTION.IMAGES
    )
  );
  React.useEffect(() => {
    parseGallerySectionFromUrlSearchParams(searchParams).do((mediaType) => {
      setSelectedSection(mediaType);
    });
  }, [searchParams]);

  const [appliedSearchTerm, setAppliedSearchTerm] = React.useState("");
  const [orderBy, setOrderBy] = React.useState("name");
  const [sortOrder, setSortOrder] = React.useState("ASC");
  const { galleryListing, path, clearPath, folderId, refreshListing } =
    useGalleryListing({
      section: selectedSection,
      searchTerm: appliedSearchTerm,
      path: [],
      orderBy,
      sortOrder,
    });
  const [selectedFile, setSelectedFile] = React.useState<GalleryFile | null>(
    null
  );
  const viewport = useViewportDimensions();
  const [drawerOpen, setDrawerOpen] = React.useState(!viewport.isViewportSmall);

  return (
    <Alerts>
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
          setDrawerOpen={setDrawerOpen}
          path={path}
          folderId={folderId}
          refreshListing={refreshListing}
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
            folderId={folderId}
            refreshListing={refreshListing}
            key={null}
            sortOrder={sortOrder}
            orderBy={orderBy}
            setSortOrder={setSortOrder}
            setOrderBy={setOrderBy}
          />
        </Box>
      </Box>
    </Alerts>
  );
})(() => ({
  "@keyframes drop": {
    "0%": {
      borderColor: lighten(SELECTED_OR_FOCUS_BLUE, 0.6),
    },
    "50%": {
      borderColor: lighten(SELECTED_OR_FOCUS_BLUE, 0.8),
    },
    "100%": {
      borderColor: lighten(SELECTED_OR_FOCUS_BLUE, 0.6),
    },
  },
}));

window.addEventListener("load", () => {
  const domContainer = document.getElementById("app");
  if (domContainer) {
    window.scrollTo(0, 1);

    const root = createRoot(domContainer);
    root.render(
      <React.StrictMode>
        <ErrorBoundary>
          <BrowserRouter>
            <StyledEngineProvider injectFirst>
              <CssBaseline />
              <ThemeProvider theme={createAccentedTheme(COLOR)}>
                <Analytics>
                  <DisableDragAndDropByDefault>
                    <Routes>
                      <Route
                        path="/newGallery"
                        element={
                          <GallerySelection>
                            <WholePage />
                          </GallerySelection>
                        }
                      />
                      <Route
                        path="*"
                        element={<Navigate to="/newGallery" replace />}
                      />
                    </Routes>
                  </DisableDragAndDropByDefault>
                </Analytics>
              </ThemeProvider>
            </StyledEngineProvider>
          </BrowserRouter>
        </ErrorBoundary>
      </React.StrictMode>
    );
  }
});
