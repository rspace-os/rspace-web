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
