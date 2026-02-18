import React from "react";
import ActionsMenu from "./ActionsMenu";
import { dummyId, type GalleryFile, Description } from "../useGalleryListing";
import { GallerySelection, useGallerySelection } from "../useGallerySelection";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../../accentedTheme";
import Result from "../../../util/result";
import { ACCENT_COLOR } from "../../../assets/branding/irods";
import { LandmarksProvider } from "@/components/LandmarksContext";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

const nonFolderFile: GalleryFile = {
  deconstructor: () => {},
  id: dummyId(),
  globalId: "GF1",
  name: "Foo",
  extension: "txt",
  creationDate: new Date(),
  modificationDate: new Date(),
  type: "image",
  thumbnailUrl: "example.com",
  ownerName: "Joe Bloggs",
  description: new Description({ key: "empty" }),
  version: 1,
  size: 1024,
  path: [],
  pathAsString: () => "",
  isFolder: false,
  isSystemFolder: false,
  isImage: true,
  isSnippet: false,
  isSnippetFolder: false,
  transformFilename(f: (filename: string) => string) {
    return f("Foo");
  },
  setName: () => {},
  setDescription: () => {},
  linkedDocuments: null,
  canOpen: Result.Error([new Error("I'm not a folder")]),
  canDuplicate: Result.Ok(null),
  canDelete: Result.Ok(null),
  canRename: Result.Ok(null),
  canMoveToIrods: Result.Ok(null),
  canBeExported: Result.Ok(null),
  canBeMoved: Result.Ok(null),
  canUploadNewVersion: Result.Ok(null),
  canBeLoggedOutOf: Result.Ok(null),
  treeViewItemId: "GF1",
  key: "GF1",
  metadata: {},
};

const folderFile: GalleryFile = {
  deconstructor: () => {},
  id: dummyId(),
  globalId: "GF2",
  name: "Foo",
  extension: "",
  creationDate: new Date(),
  modificationDate: new Date(),
  type: "folder",
  thumbnailUrl: "example.com",
  ownerName: "Joe Bloggs",
  description: new Description({ key: "empty" }),
  version: 1,
  size: 1024,
  path: [],
  pathAsString: () => "",
  isFolder: true,
  isSystemFolder: false,
  isImage: false,
  isSnippet: false,
  isSnippetFolder: false,
  transformFilename(f: (filename: string) => string) {
    return f("Foo");
  },
  setName: () => {},
  setDescription: () => {},
  linkedDocuments: null,
  canOpen: Result.Ok(null),
  canDuplicate: Result.Ok(null),
  canDelete: Result.Ok(null),
  canRename: Result.Ok(null),
  canMoveToIrods: Result.Ok(null),
  canBeExported: Result.Ok(null),
  canBeMoved: Result.Ok(null),
  canUploadNewVersion: Result.Ok(null),
  canBeLoggedOutOf: Result.Ok(null),
  treeViewItemId: "GF2",
  key: "GF2",
  metadata: {},
};

const snippetFile: GalleryFile = {
  deconstructor: () => {},
  id: dummyId(),
  globalId: "GF3",
  name: "My Snippet",
  extension: "txt",
  creationDate: new Date(),
  modificationDate: new Date(),
  type: "Snippet",
  thumbnailUrl: "example.com",
  ownerName: "Joe Bloggs",
  description: new Description({ key: "empty" }),
  version: 1,
  size: 512,
  path: [],
  pathAsString: () => "",
  isFolder: false,
  isSystemFolder: false,
  isImage: false,
  isSnippet: true,
  isSnippetFolder: false,
  transformFilename(f: (filename: string) => string) {
    return f("My Snippet");
  },
  setName: () => {},
  setDescription: () => {},
  linkedDocuments: null,
  canOpen: Result.Error([new Error("I'm not a folder")]),
  canDuplicate: Result.Ok(null),
  canDelete: Result.Ok(null),
  canRename: Result.Ok(null),
  canMoveToIrods: Result.Ok(null),
  canBeExported: Result.Ok(null),
  canBeMoved: Result.Ok(null),
  canUploadNewVersion: Result.Ok(null),
  canBeLoggedOutOf: Result.Ok(null),
  treeViewItemId: "GF3",
  key: "GF3",
  metadata: {},
};

function renderWithProviders(files: Array<GalleryFile>) {
  return (
    <QueryClientProvider client={queryClient}>
      <React.Suspense fallback={null}>
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
          <LandmarksProvider>
            <GallerySelection>
              <ActionsMenuWrapper files={files} />
            </GallerySelection>
          </LandmarksProvider>
        </ThemeProvider>
      </React.Suspense>
    </QueryClientProvider>
  );
}

function ActionsMenuWrapper({ files }: { files: Array<GalleryFile> }) {
  const selection = useGallerySelection();
  React.useEffect(() => {
    selection.clear();
    for (const file of files) {
      selection.append(file);
    }
  }, []);
  return (
    <ActionsMenu
      refreshListing={() => Promise.reject(new Error("not implemented"))}
      section="Images"
      folderId={{ tag: "success", value: -1 }}
    />
  );
}

export function ActionsMenuWithNonFolder() {
  return renderWithProviders([nonFolderFile]);
}

export function ActionsMenuWithFolder() {
  return renderWithProviders([folderFile]);
}

export function ActionsMenuWithMultipleFiles() {
  return renderWithProviders([
    folderFile,
    { ...folderFile, globalId: "GF4", key: "GF4" },
  ]);
}

export function ActionsMenuWithSnippet() {
  return renderWithProviders([snippetFile]);
}
