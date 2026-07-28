import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import NavigateContext from "../../../stores/contexts/Navigate";
import Result from "../../../util/result";
import { Description, type GalleryFile } from "../useGalleryListing";
import VersionHistoryDialog from "./VersionHistoryDialog";

/**
 * A Gallery item at version 3. Pass `pinnedVersion` to model the item being
 * viewed at a past version rather than live.
 */
export function galleryFile(overrides: Partial<GalleryFile> = {}): GalleryFile {
  return {
    deconstructor: () => {},
    id: 42,
    globalId: "GL42",
    name: "assay.txt",
    extension: "txt",
    creationDate: new Date(),
    modificationDate: new Date(),
    type: "document",
    thumbnailUrl: "example.com",
    ownerId: 1,
    ownerName: "Joe Bloggs",
    ownerUsername: "joebloggs",
    description: new Description({ key: "empty" }),
    version: 3,
    size: 2048,
    path: [],
    pathAsString: () => "",
    isFolder: false,
    isSystemFolder: false,
    isSharedFolder: false,
    isImage: false,
    isSnippet: false,
    isSnippetFolder: false,
    transformFilename(f: (filename: string) => string) {
      return f("assay.txt");
    },
    setName: () => {},
    setDescription: () => {},
    linkedDocuments: null,
    canOpen: Result.Error([new Error("I'm not a folder")]),
    canDuplicate: Result.Ok(null),
    canDelete: Result.Ok(null),
    canRename: Result.Ok(null),
    canMoveToIrods: Result.Ok(null),
    canMoveToS3: Result.Ok(null),
    canBeExported: Result.Ok(null),
    canBeMoved: Result.Ok(null),
    canUploadNewVersion: Result.Ok(null),
    canBeLoggedOutOf: Result.Ok(null),
    canViewVersionHistory: Result.Ok(null),
    treeViewItemId: "GL42",
    key: "GL42",
    metadata: {},
    ...overrides,
  };
}

export function VersionHistoryDialogStory({
  file = galleryFile(),
  open = true,
  onClose = () => {},
  navigate = () => {},
}: {
  file?: GalleryFile;
  open?: boolean;
  onClose?: () => void;
  navigate?: (url: string) => void;
}) {
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      {/* the dialog navigates, so it needs a context; useLocation goes unused */}
      <NavigateContext.Provider
        value={{
          useNavigate: () => navigate,
          useLocation: () => ({ hash: "", pathname: "", search: "", state: {}, key: "" }),
        }}
      >
        <VersionHistoryDialog open={open} onClose={onClose} file={file} />
      </NavigateContext.Provider>
    </ThemeProvider>
  );
}
