//@flow

import React from "react";
import { createRoot } from "react-dom/client";
import ErrorBoundary from "../../components/ErrorBoundary";
import { ThemeProvider, styled, lighten, useTheme } from "@mui/material/styles";
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
import RouterNavigationProvider from "./components/RouterNavigationProvider";
import { CallableImagePreview } from "./components/CallableImagePreview";
import { CallablePdfPreview } from "./components/CallablePdfPreview";
import { CallableAsposePreview } from "./components/CallableAsposePreview";
import { useSearchParamState } from "../../util/useSearchParamState";
import { FilestoreLoginProvider } from "./components/FilestoreLoginDialog";
import OpenFolderProvider from "./components/OpenFolderProvider";
import BroadcastIcon from "@mui/icons-material/Campaign";
import Alert from "@mui/material/Alert";
import Link from "@mui/material/Link";

const WholePage = styled(() => {
  const theme = useTheme();
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
  const { galleryListing, path, setPath, folderId, refreshListing } =
    useGalleryListing({
      section: selectedSection,
      searchTerm: appliedSearchTerm,
      path: [],
      orderBy,
      sortOrder,
    });
  const { isViewportSmall } = useViewportDimensions();
  const [drawerOpen, setDrawerOpen] = React.useState(!isViewportSmall);
  const sidebarId = React.useId();
  const [showAlert, setShowAlert] = React.useState(true);

  return (
    <CallableImagePreview>
      <CallablePdfPreview>
        <CallableAsposePreview>
          <OpenFolderProvider setPath={setPath}>
            <AppBar
              setDrawerOpen={setDrawerOpen}
              drawerOpen={drawerOpen}
              sidebarId={sidebarId}
            />
            {showAlert && (
              <Box sx={{ borderBottom: theme.borders.card }}>
                <Alert
                  icon={<BroadcastIcon fontSize="inherit" />}
                  severity="info"
                  onClose={() => setShowAlert(false)}
                >
                  Welcome to the new Gallery! Whilst we hope everything is
                  working as expected, please be aware that this is newly
                  released and there may be some bugs. If you encounter any
                  issues, please let us know by emailing{" "}
                  <Link href="mailto:support@researchspace.com">support</Link>{" "}
                  and using the <Link href="/oldGallery">old Gallery</Link> in
                  the meantime.
                </Alert>
              </Box>
            )}
            <Box
              sx={{ display: "flex", height: "calc(100% - 48px)" }}
              component="main"
            >
              <Sidebar
                selectedSection={selectedSection}
                setSelectedSection={(mediaType) => {
                  setSelectedSection({ mediaType });
                  setPath([]);
                  setAppliedSearchTerm("");
                }}
                drawerOpen={drawerOpen}
                setDrawerOpen={setDrawerOpen}
                folderId={folderId}
                refreshListing={refreshListing}
                id={sidebarId}
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
                  clearPath={() => setPath([])}
                  galleryListing={galleryListing}
                  folderId={folderId}
                  refreshListing={refreshListing}
                  key={null}
                  sortOrder={sortOrder}
                  orderBy={orderBy}
                  setSortOrder={setSortOrder}
                  setOrderBy={setOrderBy}
                  appliedSearchTerm={appliedSearchTerm}
                  setAppliedSearchTerm={setAppliedSearchTerm}
                />
              </Box>
            </Box>
          </OpenFolderProvider>
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
                          path="/gallery"
                          element={
                            <Alerts>
                              <RouterNavigationProvider>
                                <GallerySelection>
                                  <FilestoreLoginProvider>
                                    <WholePage />
                                  </FilestoreLoginProvider>
                                </GallerySelection>
                              </RouterNavigationProvider>
                            </Alerts>
                          }
                        />
                        <Route
                          path="*"
                          element={<Navigate to="/gallery" replace />}
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
