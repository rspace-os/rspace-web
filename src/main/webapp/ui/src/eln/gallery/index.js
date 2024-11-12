//@flow

import React from "react";
import { createRoot } from "react-dom/client";
import ErrorBoundary from "../../components/ErrorBoundary";
import { ThemeProvider, styled, lighten } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import { COLOR, SELECTED_OR_FOCUS_BLUE, GALLERY_SECTION } from "./common";
import AppBar from "./components/AppBar";
import Sidebar from "./components/Sidebar";
import MainPanel from "./components/MainPanel";
import Box from "@mui/material/Box";
import { useGalleryListing } from "./useGalleryListing";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import CssBaseline from "@mui/material/CssBaseline";
import useViewportDimensions from "../../util/useViewportDimensions";
import Alerts from "../../Inventory/components/Alerts";
import { DisableDragAndDropByDefault } from "../../components/useFileImportDragAndDrop";
import Analytics from "../../components/Analytics";
import { GallerySelection } from "./useGallerySelection";
import { BrowserRouter, Navigate } from "react-router-dom";
import { Routes, Route } from "react-router";
import useUiPreference, {
  PREFERENCES,
  UiPreferences,
} from "../../util/useUiPreference";
import RouterNavigationContext from "./components/RouterNavigationContext";
import { CallableImagePreview } from "./components/CallableImagePreview";
import { CallablePdfPreview } from "./components/CallablePdfPreview";
import { CallableAsposePreview } from "./components/CallableAsposePreview";
import { useSearchParamState } from "../../util/useSearchParamState";

const WholePage = styled(() => {
  const [searchParams, setSelectedSection] = useSearchParamState({
    mediaType: GALLERY_SECTION.IMAGES,
  });
  const selectedSection = searchParams.mediaType;

  const [appliedSearchTerm, setAppliedSearchTerm] = React.useState("");
  const [orderBy, setOrderBy] = useUiPreference<"name" | "modificationDate">(
    PREFERENCES.GALLERY_SORT_BY,
    {
      defaultValue: "modificationDate",
    }
  );
  const [sortOrder, setSortOrder] = useUiPreference<"DESC" | "ASC">(
    PREFERENCES.GALLERY_SORT_ORDER,
    {
      defaultValue: "DESC",
    }
  );
  const { galleryListing, path, clearPath, folderId, refreshListing } =
    useGalleryListing({
      section: selectedSection,
      searchTerm: appliedSearchTerm,
      path: [],
      orderBy,
      sortOrder,
    });
  const { isViewportSmall } = useViewportDimensions();
  const [drawerOpen, setDrawerOpen] = React.useState(!isViewportSmall);

  return (
    <CallableImagePreview>
      <CallablePdfPreview>
        <CallableAsposePreview>
          <AppBar
            appliedSearchTerm={appliedSearchTerm}
            setAppliedSearchTerm={setAppliedSearchTerm}
            setDrawerOpen={setDrawerOpen}
            drawerOpen={drawerOpen}
          />
          <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
            <Sidebar
              selectedSection={selectedSection}
              setSelectedSection={(mediaType) => {
                setSelectedSection({ mediaType });
                clearPath();
                setAppliedSearchTerm("");
              }}
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
                minWidth: 0,
              }}
            >
              <MainPanel
                selectedSection={selectedSection}
                path={path}
                clearPath={clearPath}
                galleryListing={galleryListing}
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
        </CallableAsposePreview>
      </CallablePdfPreview>
    </CallableImagePreview>
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
                  <UiPreferences>
                    <DisableDragAndDropByDefault>
                      <Routes>
                        <Route
                          path="/newGallery"
                          element={
                            <Alerts>
                              <RouterNavigationContext>
                                <GallerySelection>
                                  <WholePage />
                                </GallerySelection>
                              </RouterNavigationContext>
                            </Alerts>
                          }
                        />
                        <Route
                          path="*"
                          element={<Navigate to="/newGallery" replace />}
                        />
                      </Routes>
                    </DisableDragAndDropByDefault>
                  </UiPreferences>
                </Analytics>
              </ThemeProvider>
            </StyledEngineProvider>
          </BrowserRouter>
        </ErrorBoundary>
      </React.StrictMode>
    );
  }
});
