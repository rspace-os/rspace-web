import React from "react";
import ActionsMenu from "./ActionsMenu";
import { dummyId, type GalleryFile, Description } from "../useGalleryListing";
import { GallerySelection, useGallerySelection } from "../useGallerySelection";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../../accentedTheme";
import Result from "../../../util/result";
import { ACCENT_COLOR } from "../../../assets/branding/irods";
import { LandmarksProvider } from "@/components/LandmarksContext";

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
};

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
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <LandmarksProvider>
        <GallerySelection>
          <ActionsMenuWrapper files={[nonFolderFile]} />
        </GallerySelection>
      </LandmarksProvider>
    </ThemeProvider>
  );
}

export function ActionsMenuWithFolder() {
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <LandmarksProvider>
        <GallerySelection>
          <ActionsMenuWrapper files={[folderFile]} />
        </GallerySelection>
      </LandmarksProvider>
    </ThemeProvider>
  );
}

export function ActionsMenuWithMultipleFiles() {
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <LandmarksProvider>
        <GallerySelection>
          <ActionsMenuWrapper
            files={[folderFile, { ...folderFile, globalId: "GF3", key: "GF3" }]}
          />
        </GallerySelection>
      </LandmarksProvider>
    </ThemeProvider>
  );
}
