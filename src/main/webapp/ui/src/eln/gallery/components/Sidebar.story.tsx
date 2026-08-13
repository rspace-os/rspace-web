import Button from "@mui/material/Button";
import CssBaseline from "@mui/material/CssBaseline";
import MenuItem from "@mui/material/MenuItem";
import { type Theme, ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import { useTranslation } from "react-i18next";
import { BrowserRouter } from "react-router";
import createAccentedTheme from "@/accentedTheme";
import Alerts from "@/components/Alerts/Alerts";
import Analytics from "@/components/Analytics";
import ErrorBoundary from "@/components/ErrorBoundary";
import { LandmarksProvider } from "@/components/LandmarksContext";
import DMPToolAccentMenuItem from "@/eln-dmp-integration/DMPTool/DMPToolAccentMenuItem";
import { UiPreferences } from "@/hooks/api/useUiPreference";
import { DisableDragAndDropByDefault } from "@/hooks/ui/useFileImportDragAndDrop";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/gallery";
import { dummyId, Filestore, type GalleryFile, type Id } from "../useGalleryListing";
import Sidebar from "./Sidebar";
import SidebarCreateMenu from "./SidebarCreateMenu";

function GalleryTheme({ children }: { children: React.ReactNode }): React.ReactNode {
  return (
    <StyledEngineProvider injectFirst>
      <CssBaseline />
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>{children}</ThemeProvider>
    </StyledEngineProvider>
  );
}

function SidebarStory({ folderId, path }: { folderId: Id; path: ReadonlyArray<GalleryFile> | null }): React.ReactNode {
  // Fresh client per mount so query caches don't leak between tests
  const [queryClient] = React.useState(() => new QueryClient());
  return (
    <React.StrictMode>
      <ErrorBoundary>
        <BrowserRouter>
          <GalleryTheme>
            <QueryClientProvider client={queryClient}>
              <Analytics>
                <UiPreferences>
                  <DisableDragAndDropByDefault>
                    <Alerts>
                      <LandmarksProvider>
                        <Sidebar
                          selectedSection="Images"
                          setSelectedSection={() => {}}
                          drawerOpen={true}
                          setDrawerOpen={() => {}}
                          folderId={{ tag: "success", value: folderId }}
                          path={path}
                          refreshListing={() => Promise.resolve()}
                          id="1"
                        />
                      </LandmarksProvider>
                    </Alerts>
                  </DisableDragAndDropByDefault>
                </UiPreferences>
              </Analytics>
            </QueryClientProvider>
          </GalleryTheme>
        </BrowserRouter>
      </ErrorBoundary>
    </React.StrictMode>
  );
}

export function CreateMenuStory(): React.ReactNode {
  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);
  const { t } = useTranslation(["common", "apps"]);
  return (
    <GalleryTheme>
      <Button onClick={(event) => setAnchorEl(event.currentTarget)}>{t("common:actions.create")}</Button>
      <SidebarCreateMenu anchorEl={anchorEl} onClose={() => setAnchorEl(null)}>
        <MenuItem>{t("apps:dmpIntegrations.dmptool")}</MenuItem>
      </SidebarCreateMenu>
    </GalleryTheme>
  );
}

export function DMPToolCreateMenuStory({ isPicker }: { isPicker: boolean }): React.ReactNode {
  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);
  const { t } = useTranslation("common");
  return (
    <BrowserRouter>
      <GalleryTheme>
        <UiPreferences>
          <Alerts>
            <ThemeProvider
              theme={(theme: Theme) =>
                isPicker
                  ? {
                      ...theme,
                      zIndex: { ...theme.zIndex, drawer: theme.zIndex.modal + 1, modal: theme.zIndex.modal + 2 },
                    }
                  : theme
              }
            >
              <Button onClick={(event) => setAnchorEl(event.currentTarget)}>{t("actions.create")}</Button>
              <SidebarCreateMenu anchorEl={anchorEl} onClose={() => setAnchorEl(null)}>
                <DMPToolAccentMenuItem onDialogClose={() => setAnchorEl(null)} />
              </SidebarCreateMenu>
            </ThemeProvider>
          </Alerts>
        </UiPreferences>
      </GalleryTheme>
    </BrowserRouter>
  );
}

export function DefaultSidebar(): React.ReactNode {
  return <SidebarStory folderId={dummyId()} path={null} />;
}

/** Sidebar while browsing inside an S3 filestore root. */
export const S3_FILESTORE_ID = 42;
export function S3FilestoreSidebar(): React.ReactNode {
  const { t } = useTranslation("gallery");
  const filestore = new Filestore({
    id: S3_FILESTORE_ID,
    name: "my-bucket",
    filesystemId: 1,
    filesystemName: "s3",
    filesystemType: "S3",
    canWrite: true,
    ownerName: t("unknownOwner"),
  });
  return <SidebarStory folderId={null} path={[filestore]} />;
}
