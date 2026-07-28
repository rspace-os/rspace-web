import Box from "@mui/material/Box";
import CssBaseline from "@mui/material/CssBaseline";
import Stack from "@mui/material/Stack";
import { lighten, ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import { createRoot } from "react-dom/client";
import { useTranslation } from "react-i18next";
import { BrowserRouter, Navigate, Route, Routes, useParams } from "react-router";
import axios from "@/common/axios";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/rspace/gallery";
import Alerts from "../../components/Alerts/Alerts";
import Analytics from "../../components/Analytics";
import AppBar from "../../components/AppBar";
import SidebarToggle from "../../components/AppBar/SidebarToggle";
import ErrorBoundary from "../../components/ErrorBoundary";
import GoogleLoginProvider from "../../components/GoogleLoginProvider";
import { LandmarksProvider } from "../../components/LandmarksContext";
import LoaderCircular from "../../components/LoadingCircular";
import SkipToContentMenu from "../../components/SkipToContentMenu";
import { useDeploymentProperty } from "../../hooks/api/useDeploymentProperty";
import useUiPreference, { PREFERENCES, UiPreferences } from "../../hooks/api/useUiPreference";
import useOauthToken from "../../hooks/auth/useOauthToken";
import { useSearchParamState } from "../../hooks/browser/useSearchParamState";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
import { DisableDragAndDropByDefault } from "../../hooks/ui/useFileImportDragAndDrop";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import AnalyticsContext from "../../stores/contexts/Analytics";
import NavigateContext from "../../stores/contexts/Navigate";
import * as FetchingData from "../../util/fetchingData";
import * as Parsers from "../../util/parsers";
import RsSet from "../../util/set";
import { GALLERY_SECTION, type GallerySection, SELECTED_OR_FOCUS_BLUE, translateGallerySectionLabel } from "./common";
import { CallableAsposePreview } from "./components/CallableAsposePreview";
import { CallableImagePreview } from "./components/CallableImagePreview";
import { CallablePdfPreview } from "./components/CallablePdfPreview";
import { CallableSnapGenePreview } from "./components/CallableSnapGenePreview";
import { CallableSnippetPreview } from "./components/CallableSnippetPreview";
import { FilestoreLoginProvider } from "./components/FilestoreLoginDialog";
import MainPanel from "./components/MainPanel";
import OpenFolderProvider from "./components/OpenFolderProvider";
import PinnedVersionNotice from "./components/PinnedVersionNotice";
import PlaceholderLabel from "./components/PlaceholderLabel";
import RouterNavigationProvider from "./components/RouterNavigationProvider";
import Sidebar from "./components/Sidebar";
import { fetchVersionHistory } from "./galleryVersionHistory";
import { HistoricalGalleryFile } from "./historicalGalleryFile";
import { type GalleryFile, idToString, useGalleryListing } from "./useGalleryListing";
import { GallerySelection, useGallerySelection } from "./useGallerySelection";

/**
 * We use this constant to represent an empty path in the gallery to avoid
 * unnecessary re-renders and the triggering of excessive network calls. React
 * only performs shallow equality checks on arrays, so we need to use a constant
 * to ensure that the reference stays the same when the path is set to be empty
 * when it is already empty, otherwise changing the gallery section results in
 * the path being set to a new empty state (which triggers a network call for
 * the current gallery section) and then another network call to actually fetch
 * the new gallery section.
 */
const EMPTY_PATH = Object.freeze([]) as ReadonlyArray<GalleryFile>;

const WholePage = ({
  listingOf,
  setSelectedSection,
  setPath,
  autoSelect,
  title,
  decorateFile,
  notice,
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
  title: ({ path, section }: { path: ReadonlyArray<GalleryFile>; section: GallerySection }) => string;

  /**
   * Applied to every file in the listing before anything sees it, so the grid
   * tile, the selection, the InfoPanel and the Actions menu all act on one
   * object. Must be referentially stable, as the listing is memoised on it.
   */
  decorateFile?: (file: GalleryFile) => GalleryFile;

  /**
   * Rendered above the listing, for page-level state that no individual file can
   * convey.
   */
  notice?: React.ReactNode;
}) => {
  const { t } = useTranslation("gallery");
  const [appliedSearchTerm, setAppliedSearchTerm] = React.useState("");
  const [orderBy, setOrderBy] = useUiPreference<"name" | "modificationDate">(PREFERENCES.GALLERY_SORT_BY, {
    defaultValue: "modificationDate",
  });
  const [sortOrder, setSortOrder] = useUiPreference<"DESC" | "ASC">(PREFERENCES.GALLERY_SORT_ORDER, {
    defaultValue: "DESC",
  });
  const {
    galleryListing: liveListing,
    folderId,
    path,
    refreshListing,
    selectedSection,
  } = useGalleryListing({
    listingOf,
    searchTerm: appliedSearchTerm,
    orderBy,
    sortOrder,
  });

  /*
   * Decorating here, rather than at the point of selection, is what keeps the
   * whole page consistent: everything downstream reads the listing.
   */
  const galleryListing = React.useMemo(() => {
    if (!decorateFile) return liveListing;
    if (liveListing.tag !== "success" || liveListing.value.tag === "empty") return liveListing;
    return {
      tag: "success" as const,
      value: {
        ...liveListing.value,
        list: liveListing.value.list.map(decorateFile),
      },
    };
  }, [liveListing, decorateFile]);

  const { isViewportSmall } = useViewportDimensions();

  const selection = useGallerySelection();
  React.useEffect(() => {
    try {
      FetchingData.getSuccessValue(galleryListing).do((listing) => {
        if (listing.tag === "empty") return;
        for (const f of new RsSet(listing.list).intersectionMap(
          ({ id }) => idToString(id).elseThrow(),
          new RsSet(autoSelect ?? []).map((id) => `${id}`),
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
  }, [autoSelect, galleryListing]);

  const [largerViewportSidebarOpenState, setLargerViewportSidebarOpenState] = useUiPreference<boolean>(
    PREFERENCES.GALLERY_SIDEBAR_OPEN,
    {
      defaultValue: true,
    },
  );
  const [smallViewportSidebarOpenState, setSmallViewportSidebarOpenState] = React.useState(false);
  const drawerOpen = isViewportSmall ? smallViewportSidebarOpenState : largerViewportSidebarOpenState;
  const setDrawerOpen = isViewportSmall ? setSmallViewportSidebarOpenState : setLargerViewportSidebarOpenState;

  const sidebarId = React.useId();
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();

  const { trackEvent } = React.useContext(AnalyticsContext);
  React.useEffect(() => {
    trackEvent("user:load:page:gallery", { section: selectedSection });
  }, []);

  const pageTitle = React.useMemo(() => {
    try {
      return FetchingData.getSuccessValue(path)
        .flatMap((p) =>
          FetchingData.getSuccessValue(selectedSection).map((s) =>
            t("pageTitleWithContext", { pageContext: title({ path: p, section: s }) }),
          ),
        )
        .orElse(null);
    } catch (e) {
      console.error("Error computing document title", e);
      return t("pageTitle");
    }
  }, [path, selectedSection, t, title]);

  return (
    <Box
      sx={{
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
        height: "100%",
      }}
    >
      {pageTitle !== null && <title>{pageTitle}</title>}
      <CallableImagePreview>
        <CallablePdfPreview>
          <CallableAsposePreview>
            <CallableSnapGenePreview>
              <CallableSnippetPreview>
                <OpenFolderProvider
                  setPath={(newPath) => {
                    FetchingData.getSuccessValue(selectedSection).do((section) => {
                      if (section === GALLERY_SECTION.NETWORKFILES) {
                        setPath(newPath);
                        return;
                      }
                      if (newPath.length > 0) {
                        navigate(`/gallery/${idToString(newPath[newPath.length - 1].id).elseThrow()}`);
                      } else {
                        try {
                          navigate(`/gallery?mediaType=${section}`);
                        } catch {
                          // do nothing
                        }
                      }
                    });
                  }}
                >
                  <Stack sx={{ height: "100%" }}>
                    <AppBar
                      variant="page"
                      currentPage="gallery"
                      ambientI18n
                      sidebarToggle={
                        <SidebarToggle setSidebarOpen={setDrawerOpen} sidebarOpen={drawerOpen} sidebarId={sidebarId} />
                      }
                      accessibilityTips={{
                        supportsHighContrastMode: true,
                        supportsReducedMotion: true,
                        supports2xZoom: true,
                        supportsSkipToContent: true,
                      }}
                    />
                    <Box sx={{ display: "flex", minHeight: "0", flexGrow: 1 }} component="main">
                      <Sidebar
                        selectedSection={FetchingData.getSuccessValue(selectedSection).orElse(null)}
                        setSelectedSection={(mediaType) => {
                          setSelectedSection({ mediaType });
                          setPath(EMPTY_PATH);
                          setAppliedSearchTerm("");
                          trackEvent("user:change:section:gallery", {
                            section: mediaType,
                          });
                        }}
                        drawerOpen={drawerOpen}
                        setDrawerOpen={setDrawerOpen}
                        folderId={folderId}
                        path={FetchingData.getSuccessValue(path).orElse(null)}
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
                        {notice}
                        <MainPanel
                          selectedSection={FetchingData.getSuccessValue(selectedSection).orElse(null)}
                          path={FetchingData.getSuccessValue(path).orElse(null)}
                          setSelectedSection={(mediaType) => {
                            setSelectedSection({ mediaType });
                            setPath(EMPTY_PATH);
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
              </CallableSnippetPreview>
            </CallableSnapGenePreview>
          </CallableAsposePreview>
        </CallablePdfPreview>
      </CallableImagePreview>
    </Box>
  );
};

/**
 * This component is responsible for rendering the gallery when no folder is
 * specified in the URL. When a `mediaType` (i.e. gallery section) is specified
 * then the gallery will show that section. If no section is specified, then the
 * gallery will show the images section.
 */
function LandingPage() {
  const { t } = useTranslation("gallery");
  const [searchParams, setSelectedSection] = useSearchParamState<{
    mediaType: (typeof GALLERY_SECTION)[keyof typeof GALLERY_SECTION];
  }>({
    mediaType: GALLERY_SECTION.IMAGES,
  });
  const selectedSection = searchParams.mediaType;
  const [path, setPath] = React.useState<ReadonlyArray<GalleryFile>>(EMPTY_PATH);
  const filestoresEnabled = useDeploymentProperty("netfilestores.enabled");
  const listingOf = React.useMemo(() => {
    return {
      tag: "section" as const,
      section: selectedSection,
      path,
    };
  }, [selectedSection, path]);
  return FetchingData.match(filestoresEnabled, {
    loading: () => null,
    error: () => <PlaceholderLabel>{t("landingPage.filestoreEnabledError")}</PlaceholderLabel>,
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
      if (!validGallerySections.has(selectedSection)) {
        return <PlaceholderLabel>{t("landingPage.invalidSection")}</PlaceholderLabel>;
      }
      return (
        <WholePage
          listingOf={listingOf}
          setSelectedSection={setSelectedSection}
          setPath={setPath}
          title={({ path, section }) => path.at(-1)?.name ?? translateGallerySectionLabel(section, t)}
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
          title={({ path }) => {
            const file = path.at(-1);
            if (!file) throw new Error("Gallery folder path should never be empty.");
            return file.name;
          }}
        />
      );
    })
    .orElse(null);
}

/**
 * Where a file has been resolved to, once the URL has been read. A version in
 * the URL that is already the live one resolves to a redirect rather than a
 * pinned view: there is nothing historical to show, and a locked page would
 * misrepresent an editable item.
 */
type FileResolution =
  | { tag: "live"; folderId: number }
  | {
      tag: "pinned";
      folderId: number;
      version: number;
      size: number;
      modificationDate: Date | undefined;
      name: string | null;
      description: string | null;
    }
  | { tag: "redirectToLive" };

/**
 * This component is responsible for rendering the gallery when an item is
 * specified by id in the URL, either live (`/gallery/item/42`) or pinned to a
 * past version (`/gallery/item/42/2`).
 */
function GalleryFileInFolder() {
  const { fileId: fileIdParam, version: versionParam } = useParams();
  const { t } = useTranslation("gallery");
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();
  const [resolution, setResolution] = React.useState<FetchingData.Fetched<FileResolution>>({
    tag: "loading",
  });
  const [fileName, setFileName] = React.useState<string | null>(null);
  const { getToken } = useOauthToken();

  async function fetchFileDetails() {
    try {
      const token = await getToken();
      const { data } = await axios.get<unknown>(`/api/v1/files/${fileIdParam}`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      setFileName(Parsers.objectPath(["name"], data).flatMap(Parsers.isString).orElse(null));
      const folderId = Parsers.objectPath(["parentFolderId"], data).flatMap(Parsers.isNumber).elseThrow();

      if (typeof versionParam === "undefined") {
        setResolution({ tag: "success", value: { tag: "live", folderId } });
        return;
      }

      /*
       * From here on a bad version is reported rather than worked around.
       * Falling back to the live item would show content other than the version
       * the link named, which is a trust failure for anyone citing that link.
       */
      const requestedVersion = Parsers.parseInteger(versionParam)
        .mapError(() => new Error(t("pinnedVersion.invalid", { version: versionParam })))
        .elseThrow();
      const liveVersion = Parsers.objectPath(["version"], data).flatMap(Parsers.isNumber).orElse(null);
      if (requestedVersion === liveVersion) {
        setResolution({ tag: "success", value: { tag: "redirectToLive" } });
        return;
      }

      const versions = await fetchVersionHistory(String(fileIdParam), t("actionsMenu.versionHistory.loadFailed"));
      const pinned = versions.find(({ version }) => version === requestedVersion);
      if (!pinned) throw new Error(t("pinnedVersion.notFound", { version: requestedVersion }));

      setResolution({
        tag: "success",
        value: {
          tag: "pinned",
          folderId,
          version: pinned.version,
          size: pinned.size ?? 0,
          modificationDate: Parsers.isNotBottom(pinned.lastModified).flatMap(Parsers.parseDate).orElse(undefined),
          name: pinned.name,
          description: pinned.description,
        },
      });
    } catch (error) {
      console.error("Error fetching file details", error);
      if (error instanceof Error) {
        setResolution({ tag: "error", error: error.message });
      }
    }
  }

  React.useEffect(() => {
    void fetchFileDetails();
  }, [fileIdParam, versionParam]);

  const autoSelect = React.useMemo(
    () =>
      Parsers.isNotBottom(fileIdParam)
        .flatMap(Parsers.parseInteger)
        .map((fileId) => [fileId])
        .orElse([]),
    [fileIdParam],
  );

  /*
   * Only the item named in the URL is pinned. The rest of the folder it happens
   * to sit in is live, and stays editable.
   */
  const decorateFile = React.useMemo(() => {
    if (resolution.tag !== "success" || resolution.value.tag !== "pinned") return undefined;
    const { version, size, modificationDate, name, description } = resolution.value;
    const [pinnedId] = autoSelect;
    return (file: GalleryFile): GalleryFile =>
      file.id === pinnedId
        ? new HistoricalGalleryFile({ file, version, size, modificationDate, name, description })
        : file;
  }, [resolution, autoSelect]);

  return FetchingData.match<FileResolution, React.ReactNode>(resolution, {
    loading: () => t("landingPage.loading"),
    error: (error) => t("landingPage.error", { error }),
    success: (resolved) => {
      if (resolved.tag === "redirectToLive") return <Navigate to={`/gallery/item/${fileIdParam}`} replace />;
      return (
        <WholePage
          listingOf={{ tag: "folder", folderId: resolved.folderId }}
          setSelectedSection={({ mediaType }) => {
            navigate(`/gallery/?mediaType=${mediaType}`);
          }}
          setPath={() => {}}
          autoSelect={autoSelect}
          decorateFile={decorateFile}
          notice={
            resolved.tag === "pinned" ? (
              <PinnedVersionNotice version={resolved.version} fileId={fileIdParam ?? null} />
            ) : null
          }
          /* the pinned version's own name, which need not be the live one */
          title={() => (resolved.tag === "pinned" ? resolved.name : null) ?? fileName ?? t("landingPage.loading")}
        />
      );
    },
  });
}

const queryClient = new QueryClient();

export function Gallery() {
  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <CssBaseline />
      <meta
        name="theme-color"
        content={`hsl(${ACCENT_COLOR.background.hue}, ${ACCENT_COLOR.background.saturation}%, ${ACCENT_COLOR.background.lightness}%)`}
      />
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <Alerts>
          <QueryClientProvider client={queryClient}>
            <Analytics>
              <LandmarksProvider>
                <ErrorBoundary>
                  <SkipToContentMenu />
                  <GoogleLoginProvider />
                  <UiPreferences>
                    <DisableDragAndDropByDefault>
                      <Routes>
                        <Route
                          path="/gallery"
                          element={
                            <RouterNavigationProvider>
                              <GallerySelection>
                                <FilestoreLoginProvider>
                                  <LandingPage />
                                </FilestoreLoginProvider>
                              </GallerySelection>
                            </RouterNavigationProvider>
                          }
                        />
                        <Route
                          path="gallery/:folderId"
                          element={
                            <RouterNavigationProvider>
                              <GallerySelection>
                                <FilestoreLoginProvider>
                                  <GalleryFolder />
                                </FilestoreLoginProvider>
                              </GallerySelection>
                            </RouterNavigationProvider>
                          }
                        />
                        {/* The optional trailing segment pins the item to a past version. */}
                        <Route
                          path="gallery/item/:fileId/:version?"
                          element={
                            <RouterNavigationProvider>
                              <GallerySelection>
                                <FilestoreLoginProvider>
                                  <GalleryFileInFolder />
                                </FilestoreLoginProvider>
                              </GallerySelection>
                            </RouterNavigationProvider>
                          }
                        />
                        <Route path="*" element={<Navigate to="/gallery" replace />} />
                      </Routes>
                    </DisableDragAndDropByDefault>
                  </UiPreferences>
                </ErrorBoundary>
              </LandmarksProvider>
            </Analytics>
          </QueryClientProvider>
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

window.addEventListener("load", () => {
  const domContainer = document.getElementById("app");
  if (domContainer) {
    window.scrollTo(0, 1);

    const root = createRoot(domContainer);
    root.render(
      <React.StrictMode>
        <I18nRoot namespaces={["gallery", "common", "about"]} fallback={<LoaderCircular />}>
          <BrowserRouter>
            <Gallery />
          </BrowserRouter>
        </I18nRoot>
      </React.StrictMode>,
    );
  }
});
