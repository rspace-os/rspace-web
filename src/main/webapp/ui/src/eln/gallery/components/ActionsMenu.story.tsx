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
import Alerts from "@/components/Alerts/Alerts";

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
  ownerId: 1,
  ownerName: "Joe Bloggs",
  ownerUsername: "joebloggs",
  description: new Description({ key: "empty" }),
  version: 1,
  size: 1024,
  path: [],
  pathAsString: () => "",
  isFolder: false,
  isSystemFolder: false,
  isSharedFolder: false,
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
  ownerId: 1,
  ownerName: "Joe Bloggs",
  ownerUsername: "joebloggs",
  description: new Description({ key: "empty" }),
  version: 1,
  size: 1024,
  path: [],
  pathAsString: () => "",
  isFolder: true,
  isSystemFolder: false,
  isSharedFolder: false,
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
  ownerId: 1,
  ownerName: "Joe Bloggs",
  ownerUsername: "joebloggs",
  description: new Description({ key: "empty" }),
  version: 1,
  size: 512,
  path: [],
  pathAsString: () => "",
  isFolder: false,
  isSystemFolder: false,
  isSharedFolder: false,
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

function sharedFolderInPath({
  id,
  isSystemFolder = false,
}: {
  id: ReturnType<typeof dummyId>;
  isSystemFolder?: boolean;
}): GalleryFile {
  return {
    ...folderFile,
    id,
    globalId: `SHARED_${id}`,
    key: `SHARED_${id}`,
    name: isSystemFolder ? "Shared root" : "Shared folder",
    isSystemFolder,
    isSharedFolder: true,
  };
}

function renderWithProviders(files: Array<GalleryFile>) {
  return (
    <QueryClientProvider client={queryClient}>
      <React.Suspense fallback={null}>
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
          <Alerts>
            <LandmarksProvider>
              <GallerySelection>
                <ActionsMenuWrapper files={files} />
              </GallerySelection>
            </LandmarksProvider>
          </Alerts>
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

export function ActionsMenuWithMixedSelection() {
  return renderWithProviders([snippetFile, nonFolderFile]);
}

export function ActionsMenuWithMultipleSnippets() {
  return renderWithProviders([
    snippetFile,
    {
      ...snippetFile,
      globalId: "GF5",
      key: "GF5",
      id: dummyId(),
      name: "My Second Snippet",
    },
  ]);
}

export function ActionsMenuWithSnippetMissingGlobalId() {
  return renderWithProviders([
    {
      ...snippetFile,
      globalId: undefined,
      key: "GF_MISSING",
      id: dummyId(),
      name: "Snippet Missing Global ID",
    },
  ]);
}

export function ActionsMenuWithSnippetInSharedFolderOwnedBySelf() {
  return renderWithProviders([
    {
      ...snippetFile,
      id: dummyId(),
      globalId: "GF_SHARED_SELF",
      key: "GF_SHARED_SELF",
      name: "My Shared Snippet",
      ownerId: 1,
      path: [sharedFolderInPath({ id: dummyId() })],
    },
  ]);
}

export function ActionsMenuWithSnippetInSharedFolderOwnedByOther() {
  return renderWithProviders([
    {
      ...snippetFile,
      id: dummyId(),
      globalId: "GF_SHARED_OTHER",
      key: "GF_SHARED_OTHER",
      name: "Someone Else's Shared Snippet",
      ownerId: 99,
      ownerName: "Other User",
      ownerUsername: "otheruser",
      path: [sharedFolderInPath({ id: dummyId() })],
    },
  ]);
}

export function ActionsMenuWithSnippetInSystemSharedFolder() {
  return renderWithProviders([
    {
      ...snippetFile,
      id: dummyId(),
      globalId: "GF_SHARED_SYSTEM",
      key: "GF_SHARED_SYSTEM",
      name: "System Shared Snippet",
      ownerId: 99,
      ownerName: "Other User",
      ownerUsername: "otheruser",
      path: [sharedFolderInPath({ id: dummyId(), isSystemFolder: true })],
    },
  ]);
}

