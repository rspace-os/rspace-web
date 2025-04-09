/**
 * The export dialog can be triggered in a few different ways.
 *
 *  1. The user selects a set of gallery files (MEDIA_FILE), notebooks, normal
 *     files, or folders.
 *  2. The user selects a group.
 *  3. The user selects a user.
 */
export type ExportSelection =
  | {
      type: "selection";
      /*
       * Note that if these arrays are larger than 100, the network call to
       * trigger the export will fail.
       */
      exportTypes: Array<"MEDIA_FILE" | "NOTEBOOK" | "NORMAL" | "FOLDER">;
      exportNames: Array<string>;
      exportIds: ReadonlyArray<string>;
    }
  | {
      type: "group";
      groupId: string;
      groupName: string;
      exportIds: Array<unknown>;
    }
  | {
      type: "user";
      username: string;
      exportIds: Array<unknown>;
    };

/**
 * When generating PDFs, the user can select between A4 and LETTER page sizes.
 */
export type PageSize = "A4" | "LETTER";

/*
 * Documents included in the export can reference files in external filestores.
 * If the user chooses, they can include those files in the export.
 */

type Path = string;

/**
 * A link to a file in an external filestore.
 */
export type FileLink = {
  type: "file";
  size: number;
  fileSystemFullPath: Path;
  path: Path;
};

/**
 * A link to a folder in an external filestore.
 */
export type FolderLink = {
  type: "folder";
  // eslint-disable-next-line no-use-before-define
  content: Array<MixedLink>;
  fileSystemFullPath: Path;
  size: null;
};

type FoundFileLink = {
  linkType: "file" | "directory";
  path: Path;
};

/**
 * Either a link to file or a folder in an external filestore.
 */
export type MixedLink = FileLink | FolderLink;

/**
 * The Id of an external file system.
 */
export type FileSystemId = string;

/**
 * The metadata associated with an external file system.
 */
export type FileSystem = {
  id: FileSystemId;
  name: string;
  foundNfsLinks: Array<FoundFileLink>;
  loggedAs: string | null;
  checkedNfsLinks: Array<MixedLink | null>;
  checkedNfsLinkMessages: { [path: Path]: string };
};
