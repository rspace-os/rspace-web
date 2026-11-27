import { useForm } from "@tanstack/react-form";
import * as v from "valibot";
import type { ExportSelection } from "./common";
import type { ArchiveType } from "./FormatChoice";
import type { HtmlXmlExportDetails } from "./HtmlXmlExport";
import type { PdfExportDetails } from "./PdfExport";
import type { DMPUserInternalId, Person, Repo } from "./repositories/common";
import type { Tag } from "./repositories/Tags";
import type { WordExportDetails } from "./WordExport";

/*
 * The export wizard keeps every value the user can set, on any of its panes,
 * in one TanStack form. Each pane reads and writes its slice of these values,
 * and the dialog validates only the current pane's slice when the user taps
 * Next, through a FormGroup named after that slice.
 */

/**
 * The format and the additional destinations chosen on the first pane.
 */
export type ExportConfig = {
  archiveType: ArchiveType | "";
  repository: boolean;
  fileStores: boolean;
  allVersions: boolean;
  repoData: Array<Repo>;
};

/**
 * The options of the chosen format, which the export request sends as its
 * `exportConfig`. Choosing PDF or Word turns the revisions switch off, so
 * their options carry `allVersions` too. It is null until a format is chosen.
 */
export type ExportDetails =
  | (PdfExportDetails & { allVersions: boolean })
  | (WordExportDetails & { allVersions: boolean })
  | HtmlXmlExportDetails
  | null;

/**
 * The metadata collected for a deposit to a repository. The radio, select,
 * and switch inputs store the string value of the DOM event, as they always
 * have, so the request carries the same values.
 */
export type RepositoryForm = {
  repoChoice: number | string;
  title: string;
  description: string;
  subject: string;
  license: number | string;
  publish: boolean | string;
  authors: Array<Person>;
  contacts: Array<Person>;
  otherProperties: { funder?: string; metadataLanguage?: string };
  linkDMP: boolean;
  selectedPlans: Array<DMPUserInternalId>;
  tags: Array<Tag>;
  metadataLanguage: string;
  exportToRaid: boolean;
  // the format whose record tags `tags` was loaded for, so revisiting the pane keeps the user's edits
  tagsLoadedFor: string | null;
};

/**
 * The filters applied to the files that filestore links point to.
 */
export type NfsConfig = {
  excludedFileExtensions: string; // comma-separated
  includeNfsFiles: boolean;
  maxFileSizeInMB: number | string;
};

/**
 * What the filestore pane has found so far, which decides whether the user
 * is asked to confirm the export. It is not part of the export request.
 */
export type FileStoreScan = {
  totalFilesFound: number;
  loggedOutFileSystems: number;
  scanResultsPresent: boolean;
  maxArchiveSizeBytes: number;
  scanResultsTotalFileSize: number;
  currentlyAllowedArchiveSizeBytes: number;
};

export type ExportFormValues = {
  exportSelection: ExportSelection;
  exportConfig: ExportConfig;
  exportDetails: ExportDetails;
  repositoryForm: RepositoryForm;
  nfsConfig: NfsConfig;
  fileStoreScan: FileStoreScan;
};

export const DEFAULT_REPOSITORY_FORM: RepositoryForm = {
  repoChoice: 0,
  title: "",
  description: "",
  subject: "",
  license: 0,
  publish: false,
  authors: [],
  contacts: [],
  otherProperties: {},
  linkDMP: false,
  selectedPlans: [],
  tags: [],
  metadataLanguage: "",
  exportToRaid: false,
  tagsLoadedFor: null,
};

export const DEFAULT_FILE_STORE_SCAN: FileStoreScan = {
  totalFilesFound: 0,
  loggedOutFileSystems: 0,
  scanResultsPresent: false,
  maxArchiveSizeBytes: 0,
  scanResultsTotalFileSize: 0,
  currentlyAllowedArchiveSizeBytes: 0,
};

export const DEFAULT_EXPORT_FORM_VALUES: ExportFormValues = {
  exportSelection: {
    type: "selection",
    exportTypes: [],
    exportNames: [],
    exportIds: [],
  },
  exportConfig: {
    archiveType: "",
    repository: false,
    fileStores: false,
    allVersions: false,
    repoData: [],
  },
  exportDetails: null,
  repositoryForm: DEFAULT_REPOSITORY_FORM,
  nfsConfig: {
    excludedFileExtensions: "",
    includeNfsFiles: false,
    maxFileSizeInMB: 50,
  },
  fileStoreScan: DEFAULT_FILE_STORE_SCAN,
};

/**
 * Creates the form that holds the export wizard's values.
 *
 * @param defaultValues The values the form starts with and returns to on reset.
 */
export function useExportForm(defaultValues: ExportFormValues = DEFAULT_EXPORT_FORM_VALUES) {
  return useForm({ defaultValues });
}

export type ExportForm = ReturnType<typeof useExportForm>;

/**
 * Wraps a Valibot schema as a FormGroup validator.
 *
 * @param schema The schema that the group's value must satisfy.
 * @return A validator that returns the flattened issues when the value fails
 *         the schema, and undefined when it passes.
 */
export const validateWith =
  <TSchema extends v.GenericSchema>(schema: TSchema) =>
  ({ value }: { value: unknown }): v.FlatErrors<TSchema> | undefined => {
    const result = v.safeParse(schema, value);
    return result.success ? undefined : v.flatten<TSchema>(result.issues);
  };
