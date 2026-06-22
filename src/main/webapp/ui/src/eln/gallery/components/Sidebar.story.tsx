// biome-ignore lint/style/noRestrictedImports: initial biome migration
import { CssBaseline, StyledEngineProvider, ThemeProvider } from "@mui/material";
import React from "react";
import { BrowserRouter } from "react-router-dom";
import createAccentedTheme from "@/accentedTheme";
import Alerts from "@/components/Alerts/Alerts";
import Analytics from "@/components/Analytics";
import ErrorBoundary from "@/components/ErrorBoundary";
import { LandmarksProvider } from "@/components/LandmarksContext";
import { UiPreferences } from "@/hooks/api/useUiPreference";
import { DisableDragAndDropByDefault } from "@/hooks/ui/useFileImportDragAndDrop";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/gallery";
import { dummyId, Filestore, type GalleryFile, type Id } from "../useGalleryListing";
import Sidebar from "./Sidebar";

function SidebarStory({ folderId, path }: { folderId: Id; path: ReadonlyArray<GalleryFile> | null }): React.ReactNode {
  return (
    <React.StrictMode>
      <ErrorBoundary>
        <BrowserRouter>
          <StyledEngineProvider injectFirst>
            <CssBaseline />
            <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
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
            </ThemeProvider>
          </StyledEngineProvider>
        </BrowserRouter>
      </ErrorBoundary>
    </React.StrictMode>
  );
}

export function DefaultSidebar(): React.ReactNode {
  return <SidebarStory folderId={dummyId()} path={null} />;
}

/** Sidebar while browsing inside an S3 filestore root. */
export const S3_FILESTORE_ID = 42;
export function S3FilestoreSidebar(): React.ReactNode {
  const filestore = new Filestore({
    id: S3_FILESTORE_ID,
    name: "my-bucket",
    filesystemId: 1,
    filesystemName: "s3",
    filesystemType: "S3",
    canWrite: true,
  });
  return <SidebarStory folderId={null} path={[filestore]} />;
}
