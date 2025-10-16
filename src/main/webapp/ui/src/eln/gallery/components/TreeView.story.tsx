import React from "react";
import TreeView from "./TreeView";
import { LocalGalleryFile, Description } from "../useGalleryListing";
import { Optional } from "@/util/optional";
import {
  CssBaseline,
  StyledEngineProvider,
  ThemeProvider,
} from "@mui/material";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/gallery";
import { DisableDragAndDropByDefault } from "@/hooks/ui/useFileImportDragAndDrop";
import Analytics from "@/components/Analytics";
import { UiPreferences } from "@/hooks/api/useUiPreference";
import { BrowserRouter } from "react-router-dom";
import ErrorBoundary from "@/components/ErrorBoundary";
import Alerts from "@/components/Alerts/Alerts";
import { LandmarksProvider } from "@/components/LandmarksContext";
import { CallableImagePreview } from "./CallableImagePreview";
import { CallablePdfPreview } from "./CallablePdfPreview";
import OpenFolderProvider from "./OpenFolderProvider";

const mockFolder = new LocalGalleryFile({
  id: 1,
  globalId: "GL123",
  name: "Test Folder",
  extension: null,
  creationDate: new Date("2024-01-01"),
  modificationDate: new Date("2024-01-01"),
  description: Description.Present("A test folder"),
  type: "Folder",
  ownerName: "testuser",
  path: [],
  gallerySection: "Images",
  size: 0,
  version: 1,
  thumbnailId: null,
  originalImageId: null,
  metadata: {},
  token: "test-token",
});

mockFolder.downloadHref = () => Promise.resolve("/mock/folder/download");

const mockImageFile = new LocalGalleryFile({
  id: 2,
  globalId: "GL456",
  name: "test-image.jpg",
  extension: "jpg",
  creationDate: new Date("2024-01-02"),
  modificationDate: new Date("2024-01-02"),
  description: Description.Present("A test image"),
  type: "Image",
  ownerName: "testuser",
  path: [],
  gallerySection: "Images",
  size: 1024,
  version: 1,
  thumbnailId: 1,
  originalImageId: null,
  metadata: {},
  token: "test-token",
});

mockImageFile.downloadHref = () =>
  Promise.resolve(
    "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/2wBDAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAv/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwA/fAAAAA//2Q==",
  );

const mockPdfFile = new LocalGalleryFile({
  id: 3,
  globalId: "GL789",
  name: "test-document.pdf",
  extension: "pdf",
  creationDate: new Date("2024-01-03"),
  modificationDate: new Date("2024-01-03"),
  description: Description.Present("A test PDF document"),
  type: "Document",
  ownerName: "testuser",
  path: [],
  gallerySection: "Documents",
  size: 2048,
  version: 1,
  thumbnailId: null,
  originalImageId: null,
  metadata: {},
  token: "test-token",
});

mockPdfFile.downloadHref = () =>
  Promise.resolve(
    "data:application/pdf;base64,JVBERi0xLjMNCiXi48/TDQoNCjEgMCBvYmoNCjw8DQovVHlwZSAvQ2F0YWxvZw0KL091dGxpbmVzIDIgMCBSDQovUGFnZXMgMyAwIFINCj4+DQplbmRvYmoNCg0KMiAwIG9iag0KPDwNCi9UeXBlIC9PdXRsaW5lcw0KL0NvdW50IDANCj4+DQplbmRvYmoNCg0KMyAwIG9iag0KPDwNCi9UeXBlIC9QYWdlcw0KL0NvdW50IDENCi9LaWRzIFs0IDAgUl0NCj4+DQplbmRvYmoNCg0KNCAwIG9iag0KPDwNCi9UeXBlIC9QYWdlDQovUGFyZW50IDMgMCBSDQovTWVkaWFCb3ggWzAgMCA1OTUgODQyXQ0KPj4NCmVuZG9iag0KDQp4cmVmDQowIDUNCjAwMDAwMDAwMDAgNjU1MzUgZg0KMDAwMDAwMDAwOSAwMDAwMCBuDQowMDAwMDAwMDc0IDAwMDAwIG4NCjAwMDAwMDAxMjAgMDAwMDAgbg0KMDAwMDAwMDE3NyAwMDAwMCBuDQp0cmFpbGVyDQo8PA0KL1NpemUgNQ0KL1Jvb3QgMSAwIFINCj4+DQpzdGFydHhyZWYNCjI2Ng0KJUVFT0YNCg==",
  );

// Additional mock files for comprehensive testing
const mockSubFolder = new LocalGalleryFile({
  id: 4,
  globalId: "GL101112",
  name: "Subfolder",
  extension: null,
  creationDate: new Date("2024-01-04"),
  modificationDate: new Date("2024-01-04"),
  description: Description.Present("A subfolder for testing hierarchy"),
  type: "Folder",
  ownerName: "testuser",
  path: [mockFolder],
  gallerySection: "Images",
  size: 0,
  version: 1,
  thumbnailId: null,
  originalImageId: null,
  metadata: {},
  token: "test-token",
});

mockSubFolder.downloadHref = () => Promise.resolve("/mock/subfolder/download");

const mockDocumentFile = new LocalGalleryFile({
  id: 5,
  globalId: "GL131415",
  name: "report.docx",
  extension: "docx",
  creationDate: new Date("2024-01-05"),
  modificationDate: new Date("2024-01-05"),
  description: Description.Present("A Word document"),
  type: "Document",
  ownerName: "testuser",
  path: [],
  gallerySection: "Documents",
  size: 4096,
  version: 1,
  thumbnailId: null,
  originalImageId: null,
  metadata: {},
  token: "test-token",
});

mockDocumentFile.downloadHref = () =>
  Promise.resolve("/mock/document/download");

const mockVeryLongNameFile = new LocalGalleryFile({
  id: 6,
  globalId: "GL161718",
  name: "This_is_a_very_long_file_name_that_might_cause_layout_issues_in_the_tree_view_component_and_we_need_to_test_how_it_handles_such_cases.txt",
  extension: "txt",
  creationDate: new Date("2024-01-06"),
  modificationDate: new Date("2024-01-06"),
  description: Description.Present("File with very long name"),
  type: "Document",
  ownerName: "testuser",
  path: [],
  gallerySection: "Documents",
  size: 1024,
  version: 1,
  thumbnailId: null,
  originalImageId: null,
  metadata: {},
  token: "test-token",
});

mockVeryLongNameFile.downloadHref = () =>
  Promise.resolve("/mock/longname/download");

const mockSpecialCharsFile = new LocalGalleryFile({
  id: 7,
  globalId: "GL192021",
  name: "file with spaces & symbols (test) [2024].jpg",
  extension: "jpg",
  creationDate: new Date("2024-01-07"),
  modificationDate: new Date("2024-01-07"),
  description: Description.Present("File with special characters"),
  type: "Image",
  ownerName: "testuser",
  path: [],
  gallerySection: "Images",
  size: 2048,
  version: 1,
  thumbnailId: 2,
  originalImageId: null,
  metadata: {},
  token: "test-token",
});

mockSpecialCharsFile.downloadHref = () =>
  Promise.resolve(
    "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/2wBDAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAv/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwA/fAAAAA//2Q==",
  );

export function TreeViewWithFiles(): React.ReactNode {
  const listing = {
    tag: "list" as const,
    list: [mockFolder, mockImageFile, mockPdfFile],
    totalHits: 3,
    loadMore: Optional.empty<() => Promise<void>>(),
    refreshing: false,
  };

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
                        <div style={{ width: 300, height: 400 }}>
                          <TreeView
                            listing={listing}
                            path={[]}
                            selectedSection="Images"
                            refreshListing={() => Promise.resolve()}
                            sortOrder="ASC"
                            orderBy="name"
                          />
                        </div>
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

export function TreeViewLoading(): React.ReactNode {
  const listing = {
    tag: "list" as const,
    list: [mockFolder, mockImageFile],
    totalHits: 2,
    loadMore: Optional.present(() => Promise.resolve()),
    refreshing: true,
  };

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
                        <CallableImagePreview>
                          <CallablePdfPreview>
                            <OpenFolderProvider setPath={() => {}}>
                              <div style={{ width: 300, height: 400 }}>
                                <TreeView
                                  listing={listing}
                                  path={[]}
                                  selectedSection="Images"
                                  refreshListing={() => Promise.resolve()}
                                  sortOrder="ASC"
                                  orderBy="name"
                                />
                              </div>
                            </OpenFolderProvider>
                          </CallablePdfPreview>
                        </CallableImagePreview>
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

export function TreeViewFoldersOnly(): React.ReactNode {
  const listing = {
    tag: "list" as const,
    list: [mockFolder, mockSubFolder],
    totalHits: 2,
    loadMore: Optional.empty<() => Promise<void>>(),
    refreshing: false,
  };

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
                        <CallableImagePreview>
                          <CallablePdfPreview>
                            <OpenFolderProvider setPath={() => {}}>
                              <div style={{ width: 300, height: 400 }}>
                                <TreeView
                                  listing={listing}
                                  path={[]}
                                  selectedSection="Images"
                                  refreshListing={() => Promise.resolve()}
                                  sortOrder="ASC"
                                  orderBy="name"
                                  foldersOnly={true}
                                />
                              </div>
                            </OpenFolderProvider>
                          </CallablePdfPreview>
                        </CallableImagePreview>
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

export function TreeViewEmpty(): React.ReactNode {
  const listing = {
    tag: "empty" as const,
    reason: "No files found",
    refreshing: false,
  };

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
                        <CallableImagePreview>
                          <CallablePdfPreview>
                            <OpenFolderProvider setPath={() => {}}>
                              <div style={{ width: 300, height: 400 }}>
                                <TreeView
                                  listing={listing}
                                  path={[]}
                                  selectedSection="Images"
                                  refreshListing={() => Promise.resolve()}
                                  sortOrder="ASC"
                                  orderBy="name"
                                />
                              </div>
                            </OpenFolderProvider>
                          </CallablePdfPreview>
                        </CallableImagePreview>
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
