import type { ExportSelection } from "@/Export/common";
import type { ArchiveType } from "@/Export/FormatChoice";
import { DEFAULT_REPO_CONFIG, Repo } from "@/Export/repositories/common";

export type ExportConfig = {
  archiveType: string;
  repository: boolean;
  fileStores: boolean;
  allVersions: boolean;
  repoData: Array<unknown>;
};

export const DEFAULT_STATE = {
  open: false,
  loading: false,
  exportSubmitResponse: "",
  exportSelection: {
    type: "selection",
    exportTypes: [] as Array<"MEDIA_FILE" | "NOTEBOOK" | "NORMAL" | "FOLDER">,
    exportNames: [] as Array<string>,
    exportIds: [] as Array<string>,
  } as ExportSelection,
  exportConfig: {
    archiveType: "" as ArchiveType | "",
    repository: false,
    fileStores: false,
    allVersions: false,
    repoData: [] as Array<Repo>,
  },
  repositoryConfig: DEFAULT_REPO_CONFIG,
  nfsConfig: {
    excludedFileExtensions: "",
    includeNfsFiles: false,
    maxFileSizeInMB: 50 as number | string,
  },
  exportDetails: {
    archiveType: "" as ArchiveType | "",
    repository: false,
    fileStores: false,
    allVersions: false,
    repoData: [],
  } as ExportConfig,
  projectGroupId: null as number | null,
};