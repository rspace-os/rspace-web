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

export function SimpleFolderWithImageFiles(): React.ReactNode {
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
                      <MainPanel
                        selectedSection="Images"
                        path={[]}
                        setSelectedSection={() => {}}
                        galleryListing={{
                          tag: "success",
                          value: {
                            tag: "list",
                            list: [
                              new LocalGalleryFile({
                                id: 1,
                                globalId: "GL1",
                                name: "Some folder",
                                extension: null,
                                creationDate: new Date("2023-01-01T00:00:00Z"),
                                modificationDate: new Date(
                                  "2023-01-01T00:00:00Z"
                                ),
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
                            loadMore: Optional.empty(),
                            refreshing: false,
                          },
                        }}
                        folderId={{ tag: "success", value: -1 }}
                        refreshListing={async () => {}}
                        sortOrder="ASC"
                        orderBy="name"
                        setSortOrder={() => {}}
                        setOrderBy={() => {}}
                        appliedSearchTerm=""
                        setAppliedSearchTerm={() => {}}
                      />
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
