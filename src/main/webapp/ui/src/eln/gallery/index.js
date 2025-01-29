//@flow

import React from "react";
import { createRoot } from "react-dom/client";
import ErrorBoundary from "../../components/ErrorBoundary";
import { ThemeProvider, styled, lighten, useTheme } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import {
  COLOR,
  SELECTED_OR_FOCUS_BLUE,
  GALLERY_SECTION,
  type GallerySection,
} from "./common";
import AppBar from "../../components/AppBar";
import Sidebar from "./components/Sidebar";
import MainPanel from "./components/MainPanel";
import Box from "@mui/material/Box";
import {
  useGalleryListing,
  idToString,
  type GalleryFile,
  type Id,
} from "./useGalleryListing";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import CssBaseline from "@mui/material/CssBaseline";
import useViewportDimensions from "../../util/useViewportDimensions";
import Alerts from "../../Inventory/components/Alerts";
import { DisableDragAndDropByDefault } from "../../components/useFileImportDragAndDrop";
import Analytics from "../../components/Analytics";
import { GallerySelection } from "./useGallerySelection";
import { BrowserRouter, Navigate } from "react-router-dom";
import { Routes, Route, useParams } from "react-router";
import useUiPreference, {
  PREFERENCES,
  UiPreferences,
} from "../../util/useUiPreference";
import RouterNavigationProvider from "./components/RouterNavigationProvider";
import NavigateContext from "../../stores/contexts/Navigate";
import { CallableImagePreview } from "./components/CallableImagePreview";
import { CallablePdfPreview } from "./components/CallablePdfPreview";
import { CallableAsposePreview } from "./components/CallableAsposePreview";
import { useSearchParamState } from "../../util/useSearchParamState";
import { FilestoreLoginProvider } from "./components/FilestoreLoginDialog";
import OpenFolderProvider from "./components/OpenFolderProvider";
import * as FetchingData from "../../util/fetchingData";
import { useDeploymentProperty } from "../useDeploymentProperty";
import PlaceholderLabel from "./components/PlaceholderLabel";
import AnalyticsContext from "../../stores/contexts/Analytics";
import SidebarToggle from "../../components/AppBar/SidebarToggle";
import BroadcastIcon from "@mui/icons-material/Campaign";
import Alert from "@mui/material/Alert";
import Link from "@mui/material/Link";

const WholePage = styled(
  ({
    listingOf,
    setSelectedSection,
    setPath,
  }: {|
    listingOf:
      | {|
          tag: "section",
          section: GallerySection,
          path: $ReadOnlyArray<GalleryFile>,
        |}
      | {| tag: "folder", folderId: Id |},
    setSelectedSection: ({| mediaType: GallerySection |}) => void,
    setPath: ($ReadOnlyArray<GalleryFile>) => void,
  |}) => {
    const theme = useTheme();
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
    const { galleryListing, folderId, path, refreshListing, selectedSection } =
      useGalleryListing({
        listingOf,
        searchTerm: appliedSearchTerm,
        orderBy,
        sortOrder,
      });
    const { isViewportSmall } = useViewportDimensions();

    const [largerViewportSidebarOpenState, setLargerViewportSidebarOpenState] =
      useUiPreference<boolean>(PREFERENCES.GALLERY_SIDEBAR_OPEN, {
        defaultValue: true,
      });
    const [smallViewportSidebarOpenState, setSmallViewportSidebarOpenState] =
      React.useState(false);
    const drawerOpen = isViewportSmall
      ? smallViewportSidebarOpenState
      : largerViewportSidebarOpenState;
    const setDrawerOpen = isViewportSmall
      ? setSmallViewportSidebarOpenState
      : setLargerViewportSidebarOpenState;

    const sidebarId = React.useId();
    const { useNavigate } = React.useContext(NavigateContext);
    const navigate = useNavigate();

    const [showAlert, setShowAlert] = React.useState(true);

    const { trackEvent } = React.useContext(AnalyticsContext);
    React.useEffect(() => {
      trackEvent("user:load:page:gallery", { section: selectedSection });
      /* eslint-disable-next-line react-hooks/exhaustive-deps --
       * - selectedSection may change but we only care about on-mount
       */
    }, []);

    return (
      <CallableImagePreview>
        <CallablePdfPreview>
          <CallableAsposePreview>
            <OpenFolderProvider
              setPath={(newPath) => {
                if (newPath.length > 0) {
                  navigate(
                    `/gallery/${idToString(newPath[newPath.length - 1].id)}`
                  );
                } else {
                  try {
                    const section =
                      FetchingData.getSuccessValue(selectedSection).elseThrow();
                    navigate(`/gallery/?mediaType=${section}`);
                  } catch {
                    // do nothing
                  }
                }
              }}
            >
              <AppBar
                variant="page"
                currentPage="Gallery"
                sidebarToggle={
                  <SidebarToggle
                    setSidebarOpen={setDrawerOpen}
                    sidebarOpen={drawerOpen}
                    sidebarId={sidebarId}
                  />
                }
                accessibilityTips={{
                  supportsHighContrastMode: true,
                  supportsReducedMotion: true,
                  supports2xZoom: true,
                }}
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
                    and using the{" "}
                    <Link target="_self" href="/oldGallery">
                      old Gallery
                    </Link>{" "}
                    in the meantime.
                  </Alert>
                </Box>
              )}
              <Box
                sx={{ display: "flex", height: "calc(100% - 48px)" }}
                component="main"
              >
                <Sidebar
                  selectedSection={FetchingData.getSuccessValue(
                    selectedSection
                  ).orElse(null)}
                  setSelectedSection={(mediaType) => {
                    setSelectedSection({ mediaType });
                    setPath([]);
                    setAppliedSearchTerm("");
                    trackEvent("user:change:section:gallery", {
                      section: mediaType,
                    });
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
                    selectedSection={FetchingData.getSuccessValue(
                      selectedSection
                    ).orElse(null)}
                    path={FetchingData.getSuccessValue(path).orElse(null)}
                    setSelectedSection={(mediaType) => {
                      setSelectedSection({ mediaType });
                      setPath([]);
                      setAppliedSearchTerm("");
                    }}
                    galleryListing={galleryListing}
                    folderId={folderId}
                    refreshListing={refreshListing}
                    key={null}
                    sortOrder={sortOrder}
                    orderBy={orderBy}
                    setSortOrder={setSortOrder}
                    setOrderBy={setOrderBy}
                    appliedSearchTerm={appliedSearchTerm}
                    setAppliedSearchTerm={(newTerm) => {
                      FetchingData.getSuccessValue(path).do((p) => {
                        if (p.length > 0) {
                          trackEvent("user:search:folder:gallery");
                        } else {
                          trackEvent("user:search:section:gallery");
                        }
                      });
                      setAppliedSearchTerm(newTerm);
                    }}
                  />
                </Box>
              </Box>
            </OpenFolderProvider>
          </CallableAsposePreview>
        </CallablePdfPreview>
      </CallableImagePreview>
    );
  }
)(() => ({
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

/**
 * This component is responsible for rendering the gallery when no folder is
 * specified in the URL. When a `mediaType` (i.e. gallery section) is specified
 * then the gallery will show that section. If no section is specified, then the
 * gallery will show the images section.
 */
function LandingPage() {
  const [searchParams, setSelectedSection] = useSearchParamState({
    mediaType: GALLERY_SECTION.IMAGES,
  });
  const selectedSection = searchParams.mediaType;
  const [path, setPath] = React.useState<$ReadOnlyArray<GalleryFile>>([]);
  const filestoresEnabled = useDeploymentProperty("netfilestores.enabled");
  return FetchingData.match(filestoresEnabled, {
    loading: () => null,
    error: () => (
      <PlaceholderLabel>
        Erorr checking if filestores are enabled.
      </PlaceholderLabel>
    ),
    success: (fsEnabled) => {
      const validGallerySections = new Set([
        "Images",
        "Audios",
        "Videos",
        "Documents",
        "Chemistry",
        "DMPs",
        "Snippets",
        "Miscellaneous",
        ...(fsEnabled === true ? ["NetworkFiles"] : []),
        "PdfDocuments",
      ]);
      if (!validGallerySections.has(selectedSection))
        return (
          <PlaceholderLabel>Not a valid Gallery section.</PlaceholderLabel>
        );
      return (
        <WholePage
          listingOf={{ tag: "section", section: selectedSection, path }}
          setSelectedSection={setSelectedSection}
          setPath={setPath}
        />
      );
    },
  });
}

/**
 * This component is responsible for rendering the gallery when a folder is
 * specified by id in the URL.
 */
function GalleryFolder() {
  const { folderId } = useParams();
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();

  return (
    <WholePage
      listingOf={{ tag: "folder", folderId }}
      setSelectedSection={({ mediaType }) => {
        navigate(`/gallery/?mediaType=${mediaType}`);
      }}
      setPath={() => {}}
    />
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
                                    <LandingPage />
                                  </FilestoreLoginProvider>
                                </GallerySelection>
                              </RouterNavigationProvider>
                            </Alerts>
                          }
                        />
                        <Route
                          path="gallery/:folderId"
                          element={
                            <Alerts>
                              <RouterNavigationProvider>
                                <GallerySelection>
                                  <FilestoreLoginProvider>
                                    <GalleryFolder />
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
