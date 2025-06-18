import React from "react";
import MainPanel from "./MainPanel";
import { Optional } from "@/util/optional";
import {
  CssBaseline,
  StyledEngineProvider,
  ThemeProvider,
} from "@mui/material";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/gallery";
import { DisableDragAndDropByDefault } from "@/components/useFileImportDragAndDrop";
import Analytics from "@/components/Analytics";
import { UiPreferences } from "@/util/useUiPreference";
import { BrowserRouter } from "react-router-dom";
import ErrorBoundary from "@/components/ErrorBoundary";
import { Description, LocalGalleryFile } from "../useGalleryListing";
import Alerts from "@/components/Alerts/Alerts";
import OpenFolderProvider from "./OpenFolderProvider";
import { GallerySelection } from "../useGallerySelection";

/**
 * A gallery listing with nested folders:
 * - Root contains "Outer folder"
 * - "Outer folder" contains "Inner folder"
 * - "Inner folder" contains "Some image"
 */
export function NestedFoldersWithImageFile(): React.ReactNode {
  const MOCK_ROOT_WITH_OUTER_FOLDER = {
    tag: "success" as const,
    value: {
      tag: "list" as const,
      list: [
        new LocalGalleryFile({
          id: 1,
          globalId: "GF1",
          name: "Outer folder",
          extension: null,
          creationDate: new Date("2023-01-01T00:00:00Z"),
          modificationDate: new Date("2023-01-01T00:00:00Z"),
          description: Description.Empty(),
          type: "Folder",
          ownerName: "user1",
          path: [],
          gallerySection: "Images",
          size: 0,
          version: 1,
          thumbnailId: null,
          originalImageId: null,
          token: "",
        }),
      ],
      totalHits: 1,
      loadMore: Optional.empty<() => Promise<void>>(),
      refreshing: false,
    },
  };

  const MOCK_OUTER_FOLDER_LISTING = {
    tag: "success" as const,
    value: {
      tag: "list" as const,
      list: [
        new LocalGalleryFile({
          id: 2,
          globalId: "GF2",
          name: "Inner folder",
          extension: null,
          creationDate: new Date("2023-01-01T00:00:00Z"),
          modificationDate: new Date("2023-01-01T00:00:00Z"),
          description: Description.Empty(),
          type: "Folder",
          ownerName: "user1",
          path: [MOCK_ROOT_WITH_OUTER_FOLDER.value.list[0]],
          gallerySection: "Images",
          size: 0,
          version: 1,
          thumbnailId: null,
          originalImageId: null,
          token: "",
        }),
      ],
      totalHits: 1,
      loadMore: Optional.empty<() => Promise<void>>(),
      refreshing: false,
    },
  };

  const MOCK_INNER_FOLDER_LISTING = {
    tag: "success" as const,
    value: {
      tag: "list" as const,
      list: [
        new LocalGalleryFile({
          id: 3,
          globalId: "GL3",
          name: "Some image",
          extension: "png",
          creationDate: new Date("2023-01-01T00:00:00Z"),
          modificationDate: new Date("2023-01-01T00:00:00Z"),
          description: Description.Empty(),
          type: "Image",
          ownerName: "user1",
          path: [
            MOCK_ROOT_WITH_OUTER_FOLDER.value.list[0],
            MOCK_OUTER_FOLDER_LISTING.value.list[0],
          ],
          gallerySection: "Images",
          size: 0,
          version: 1,
          thumbnailId: null,
          originalImageId: null,
          token: "",
        }),
      ],
      totalHits: 1,
      loadMore: Optional.empty<() => Promise<void>>(),
      refreshing: false,
    },
  };
  const [galleryListing, setGalleryListing] = React.useState<
    React.ComponentProps<typeof MainPanel>["galleryListing"]
  >(MOCK_ROOT_WITH_OUTER_FOLDER);
  const [path, setPath] = React.useState<
    React.ComponentProps<typeof MainPanel>["path"]
  >([]);

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
                      <GallerySelection>
                        <OpenFolderProvider
                          setPath={(newPath) => {
                            if (newPath.length === 0) {
                              setGalleryListing(MOCK_ROOT_WITH_OUTER_FOLDER);
                              setPath([]);
                            } else if (newPath.length === 1) {
                              setGalleryListing(MOCK_OUTER_FOLDER_LISTING);
                              setPath([
                                MOCK_ROOT_WITH_OUTER_FOLDER.value.list[0],
                              ]);
                            }
                          }}
                        >
                          <MainPanel
                            selectedSection="Images"
                            path={path}
                            setSelectedSection={() => {
                              setGalleryListing(MOCK_ROOT_WITH_OUTER_FOLDER);
                              setPath([]);
                            }}
                            galleryListing={galleryListing}
                            folderId={{ tag: "success", value: -1 }}
                            refreshListing={async () => {}}
                            sortOrder="ASC"
                            orderBy="name"
                            setSortOrder={() => {}}
                            setOrderBy={() => {}}
                            appliedSearchTerm=""
                            setAppliedSearchTerm={() => {}}
                          />
                        </OpenFolderProvider>
                      </GallerySelection>
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
