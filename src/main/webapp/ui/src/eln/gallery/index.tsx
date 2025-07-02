import React from "react";
import { createRoot } from "react-dom/client";
import ErrorBoundary from "../../components/ErrorBoundary";
import { ThemeProvider, styled, lighten, useTheme } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import {
  SELECTED_OR_FOCUS_BLUE,
  GALLERY_SECTION,
  type GallerySection,
  gallerySectionLabel,
} from "./common";
import { ACCENT_COLOR } from "../../assets/branding/rspace/gallery";
import AppBar from "../../components/AppBar";
import Sidebar from "./components/Sidebar";
import MainPanel from "./components/MainPanel";
import Box from "@mui/material/Box";
import {
  useGalleryListing,
  idToString,
  type GalleryFile,
} from "./useGalleryListing";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import CssBaseline from "@mui/material/CssBaseline";
import useViewportDimensions from "../../util/useViewportDimensions";
import Alerts from "../../components/Alerts/Alerts";
import { DisableDragAndDropByDefault } from "../../components/useFileImportDragAndDrop";
import Analytics from "../../components/Analytics";
import { GallerySelection, useGallerySelection } from "./useGallerySelection";
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
import { CallableSnapGenePreview } from "./components/CallableSnapGenePreview";
import { useSearchParamState } from "../../util/useSearchParamState";
import { FilestoreLoginProvider } from "./components/FilestoreLoginDialog";
import OpenFolderProvider from "./components/OpenFolderProvider";
import * as FetchingData from "../../util/fetchingData";
import { useDeploymentProperty } from "../useDeploymentProperty";
import PlaceholderLabel from "./components/PlaceholderLabel";
import AnalyticsContext from "../../stores/contexts/Analytics";
import SidebarToggle from "../../components/AppBar/SidebarToggle";
import GoogleLoginProvider from "../../components/GoogleLoginProvider";
import BroadcastIcon from "@mui/icons-material/Campaign";
import Alert from "@mui/material/Alert";
import Link from "@mui/material/Link";
import * as Parsers from "../../util/parsers";
import axios from "@/common/axios";
import useOauthToken from "../../common/useOauthToken";
import RsSet from "../../util/set";
import docLinks from "../../assets/DocLinks";
import Stack from "@mui/material/Stack";
import * as ArrayUtils from "../../util/ArrayUtils";

const WholePage = styled(
  ({
    listingOf,
    setSelectedSection,
    setPath,
    autoSelect,
  }: {
    listingOf:
      | {
          tag: "section";
          section: GallerySection;
          path: ReadonlyArray<GalleryFile>;
        }
      | { tag: "folder"; folderId: number };
    setSelectedSection: ({ mediaType }: { mediaType: GallerySection }) => void;
    setPath: (path: ReadonlyArray<GalleryFile>) => void;
    autoSelect?: ReadonlyArray<number>;
  }) => {
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

    const selection = useGallerySelection();
    React.useEffect(() => {
      try {
        FetchingData.getSuccessValue(galleryListing).do((listing) => {
          if (listing.tag === "empty") return;
          for (const f of new RsSet(listing.list).intersectionMap(
            ({ id }) => idToString(id).elseThrow(),
            new RsSet(autoSelect ?? []).map((id) => `${id}`)
          )) {
            selection.append(f);
          }
        });
      } catch {
        /*
         * This will throw when processing files from external filestores that
         * do not have an id, but that's fine as external filestores cannot be
         * encoded in the URL and so cannot be autoselected.
         */
      }
      /* eslint-disable-next-line react-hooks/exhaustive-deps --
       * - selection should be allowed to change with re-triggering this
       */
    }, [autoSelect, galleryListing]);

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

    const { trackEvent } = React.useContext(AnalyticsContext);
    React.useEffect(() => {
      trackEvent("user:load:page:gallery", { section: selectedSection });
      /* eslint-disable-next-line react-hooks/exhaustive-deps --
       * - selectedSection may change but we only care about on-mount
       */
    }, []);

    React.useEffect(() => {
      if (listingOf.tag === "folder") {
        document.title = `${FetchingData.getSuccessValue(path)
          .flatMap(ArrayUtils.last)
          .map((file) => `${file.name} `)
          .orElse("")}| RSpace Gallery`;
      } else {
        if (listingOf.path.length > 0) {
          document.title = `${
            ArrayUtils.last(listingOf.path).elseThrow().name
          } | RSpace Gallery`;
        } else {
          document.title = `${
            gallerySectionLabel[listingOf.section]
          } | RSpace Gallery`;
        }
      }
    }, [listingOf, path]);

    return (
      <>
        <CallableImagePreview>
          <CallablePdfPreview>
            <CallableAsposePreview>
              <CallableSnapGenePreview>
                <OpenFolderProvider
                  setPath={(newPath) => {
                    FetchingData.getSuccessValue(selectedSection).do(
                      (section) => {
                        if (section === GALLERY_SECTION.NETWORKFILES) {
                          setPath(newPath);
                          return;
                        }
                        if (newPath.length > 0) {
                          navigate(
                            `/gallery/${idToString(
                              newPath[newPath.length - 1].id
                            ).elseThrow()}`
                          );
                        } else {
                          try {
                            navigate(`/gallery?mediaType=${section}`);
                          } catch {
                            // do nothing
                          }
                        }
                      }
                    );
                  }}
                >
                  <Stack sx={{ height: "100%" }}>
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
                    <Box sx={{ borderBottom: theme.borders.card }}>
                      <Alert
                        icon={<BroadcastIcon fontSize="inherit" />}
                        severity="info"
                      >
                        Welcome to the{" "}
                        <Link href={docLinks.gallery}>new Gallery!</Link> If you
                        encounter any issues, please let us know by emailing{" "}
                        <Link href="mailto:support@researchspace.com">
                          support
                        </Link>{" "}
                        and using the{" "}
                        <Link target="_self" href="/oldGallery">
                          old Gallery
                        </Link>{" "}
                        in the meantime.
                      </Alert>
                    </Box>
                    <Box
                      sx={{ display: "flex", minHeight: "0", flexGrow: 1 }}
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
                  </Stack>
                </OpenFolderProvider>
              </CallableSnapGenePreview>
            </CallableAsposePreview>
          </CallablePdfPreview>
        </CallableImagePreview>
      </>
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
  const [searchParams, setSelectedSection] = useSearchParamState<{
    mediaType: (typeof GALLERY_SECTION)[keyof typeof GALLERY_SECTION];
  }>({
    mediaType: GALLERY_SECTION.IMAGES,
  });
  const selectedSection = searchParams.mediaType;
  const [path, setPath] = React.useState<ReadonlyArray<GalleryFile>>([]);
  const filestoresEnabled = useDeploymentProperty("netfilestores.enabled");
  return FetchingData.match(filestoresEnabled, {
    loading: () => null,
    error: () => (
      <PlaceholderLabel>
        Error checking if filestores are enabled.
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

  return Parsers.isNotBottom(folderId)
    .flatMap(Parsers.parseInteger)
    .map((fId) => {
      return (
        <WholePage
          key={"whole page"}
          listingOf={{ tag: "folder", folderId: fId }}
          setSelectedSection={({ mediaType }) => {
            navigate(`/gallery?mediaType=${mediaType}`);
          }}
          setPath={() => {}}
        />
      );
    })
    .orElse(null);
}

function GalleryFileInFolder() {
  const { fileId: fileIdParam } = useParams();
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();
  const [folderId, setFolderId] = React.useState<FetchingData.Fetched<number>>({
    tag: "loading",
  });
  const { getToken } = useOauthToken();

  async function fetchFileDetails() {
    try {
      const token = await getToken();
      const { data } = await axios.get<unknown>(
        `/api/v1/files/${fileIdParam}`,
        {
          headers: {
            Authorization: "Bearer " + token,
          },
        }
      );
      setFolderId(
        Parsers.objectPath(["parentFolderId"], data)
          .flatMap(Parsers.isNumber)
          .map((id) => ({ tag: "success" as const, value: id }))
          .orElseGet(([e]) => ({ tag: "error", error: e.message }))
      );
    } catch (error) {
      console.error("Error fetching file details", error);
      if (error instanceof Error) {
        setFolderId({ tag: "error", error: error.message });
      }
    }
  }

  React.useEffect(() => {
    void fetchFileDetails();
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - fetchFileDetails will not meaningfully change
     */
  }, []);

  return FetchingData.match<number, React.ReactNode>(folderId, {
    loading: () => "Loading...",
    error: (error) => `Error: ${error}`,
    success: (fId) => (
      <WholePage
        listingOf={{ tag: "folder", folderId: fId }}
        setSelectedSection={({ mediaType }) => {
          navigate(`/gallery/?mediaType=${mediaType}`);
        }}
        setPath={() => {}}
        autoSelect={Parsers.isNotBottom(fileIdParam)
          .flatMap(Parsers.parseInteger)
          .map((fileId) => [fileId])
          .orElse([])}
      />
    ),
  });
}

window.addEventListener("load", () => {
  const domContainer = document.getElementById("app");
  if (domContainer) {
    window.scrollTo(0, 1);

    const root = createRoot(domContainer);
    root.render(
      <React.StrictMode>
        <Analytics>
          <ErrorBoundary>
            <BrowserRouter>
              <GoogleLoginProvider />
              <StyledEngineProvider injectFirst>
                <CssBaseline />
                <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
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
                          path="gallery/item/:fileId"
                          element={
                            <Alerts>
                              <RouterNavigationProvider>
                                <GallerySelection>
                                  <FilestoreLoginProvider>
                                    <GalleryFileInFolder />
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
                </ThemeProvider>
              </StyledEngineProvider>
            </BrowserRouter>
          </ErrorBoundary>
        </Analytics>
      </React.StrictMode>
    );

    const meta = document.createElement("meta");
    meta.name = "theme-color";
    meta.content = `hsl(${ACCENT_COLOR.background.hue}, ${ACCENT_COLOR.background.saturation}%, ${ACCENT_COLOR.background.lightness}%)`;
    document.head?.appendChild(meta);
  }
});
