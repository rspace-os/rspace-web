import React from "react";
import axios from "@/common/axios";
import Result from "../../util/result";
import * as Parsers from "../../util/parsers";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import * as FetchingData from "../../util/fetchingData";
import * as ArrayUtils from "../../util/ArrayUtils";
import {
  gallerySectionCollectiveNoun,
  type GallerySection,
  GALLERY_SECTION,
  parseGallerySection,
} from "./common";
import {
  filenameExceptExtension,
  justFilenameExtension,
} from "../../util/files";
import { useGallerySelection } from "./useGallerySelection";
import { observable, action, makeObservable } from "mobx";
import { Optional } from "../../util/optional";
import { type URL as UrlType } from "../../util/types";
import { take, incrementForever } from "../../util/iterators";
import useOauthToken from "../../common/useOauthToken";
import { useFilestoreLogin } from "./components/FilestoreLoginDialog";
import { LinkedDocumentsPanel } from "./components/LinkedDocumentsPanel";
import EXT_BY_TYPE from "./fileExtensionsByType.json";

/**
 * The Id of a Gallery file. It may be null if the filesystem being accessed
 * does not support ids. It is an opaque alias because all other modules should
 * either use the `key` attribute to get a globally unique string, or use the
 * `idToString` function to get a string representation of the Id, with
 * explicit handling for the case where there isn't an id.
 */
export type Id = number | null;

/*
 * dummyId is for use in tests ONLY. All other Ids MUST be got from API calls.
 */
let nextDummyId = 0;
/**
 * Create a new dummy Id
 */
export const dummyId: () => Id = () => {
  return nextDummyId++;
};
/**
 * Produce a string representation of the Id
 */
export function idToString(id: Id): Result<string> {
  if (id === null) return Result.Error([new Error("Id is null")]);
  return Result.Ok(`${id}`);
}

/*
 * Maps file extensions to icon files
 */
const mapToSvgImageIcon = (
  extensions: ReadonlyArray<string>,
  filename: string
): ReadonlyArray<[string, string]> =>
  extensions.map((ext) => [ext, `/images/icons/${filename}.svg`]);
const fileIconMap = new Map([
  ...mapToSvgImageIcon(EXT_BY_TYPE.CHEMISTRY, "chemistry"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.DNA, "dna"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.AUDIO, "audio"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.VIDEO, "video"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.SPREADSHEET, "sheet"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.IMAGES, "image"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.DOCUMENTS, "document"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.PRESENTATION, "presentation"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.HTML, "html"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.CSV, "csv"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.PDF, "pdf"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.XML, "xml"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.ZIP, "zip"),
]);

type DescriptionInternalState =
  | { key: "missing" }
  | { key: "empty" }
  | { key: "present"; value: string };
/**
 * All local Gallery files have a description, but this description may be
 * empty. Filestores and filestores in filestores will not have a description.
 */
export class Description {
  private readonly state: DescriptionInternalState;

  constructor(state: DescriptionInternalState) {
    this.state = state;
  }

  static Missing(): Description {
    return new Description({ key: "missing" });
  }

  static Empty(): Description {
    return new Description({ key: "empty" });
  }

  static Present(value: string): Description {
    return new Description({ key: "present", value });
  }

  toString(): Result<string> {
    return this.match({
      missing: () => Result.Error([new Error("Description is missing")]),
      empty: () => Result.Ok(""),
      present: (d) => Result.Ok(d),
    });
  }

  match<T>(opts: {
    missing: () => T;
    empty: () => T;
    present: (desc: string) => T;
  }): T {
    if (this.state.key === "missing") return opts.missing();
    if (this.state.key === "empty") return opts.empty();
    return opts.present(this.state.value);
  }
}

/**
 * Objects of this shape model files and folders in the Gallery.
 */
export interface GalleryFile {
  /*
   * clean up any resources that the object may have created
   * that wont be cleaned up by the garbage collector
   */
  deconstructor(): void;

  readonly id: Id;
  readonly globalId?: string;
  name: string;

  /*
   * A string that uniquely identifies the file/folder within the gallery.
   * This will either be an id or a path depending upon the filesystem being
   * accessed, and thus how the class implements this interface.
   */
  readonly key: string;

  // null for folders, otherwise usually a non-empty string
  readonly extension: string | null;

  readonly creationDate?: Date;
  readonly modificationDate?: Date;
  readonly type?: string;
  readonly thumbnailUrl: UrlType;
  readonly ownerName?: string;
  description: Description;

  // In bytes. Folders are always 0 bytes
  readonly size: number;

  /*
   * A positive natural number, that is incremented whenever the user uploads a
   * new version or otherwise edits the file
   */
  readonly version?: number;

  /*
   * A versioned global Id that refers to an original image file from which
   * this image file was created.
   */
  readonly originalImageId?: string | null;

  /*
   * A list of folders from the top gallery section to the parent of this
   * file/folder
   */
  readonly path: ReadonlyArray<GalleryFile>;

  /*
   * The names of the folders that form the path, separated with forward slashes
   */
  pathAsString(): string;

  downloadHref?: () => Promise<UrlType>;

  /*
   * These predicates should be false whenever the implementing class cannot
   * be sure that the file is of the respective type.
   */
  readonly isFolder: boolean;
  readonly isSystemFolder: boolean;
  readonly isImage: boolean;
  readonly isSnippet: boolean;
  readonly isSnippetFolder: boolean;

  /*
   * There are various places in the UI where the user applies some
   * transformation to the name of either a folder or file. Mainly the rename
   * action, but also when duplicating and in other places too. This method
   * allows the call to generate a new file name by applying a transformation
   * to the name before the extension, leaving the extension in place.
   */
  transformFilename(f: (filename: string) => string): string;

  readonly setName?: (newName: string) => void;
  readonly setDescription?: (newDescription: Description) => void;

  readonly linkedDocuments: React.ReactNode;

  readonly canOpen: Result<null>;
  readonly canDuplicate: Result<null>;
  readonly canDelete: Result<null>;
  readonly canRename: Result<null>;
  readonly canMoveToIrods: Result<null>;
  readonly canBeExported: Result<null>;
  readonly canBeMoved: Result<null>;
  readonly canUploadNewVersion: Result<null>;
  readonly canBeLoggedOutOf: Result<null>;

  /*
   * A unique identifier across all possible trees that this file may be
   * rendered in.
   */
  readonly treeViewItemId: string;
}

/**
 * Tries to generate a URL to an image file from the GalleryFile using the
 * chemistry service. If the file is not a chemistry file, then an error is
 * returned.
 */
export function chemistryFilePreview(file: GalleryFile): Result<string> {
  if (!file.modificationDate)
    return Result.Error([new Error("No modification date")]);
  const time = file.modificationDate.getTime();
  if (file.type === "Chemistry")
    return idToString(file.id).map(
      (id) => `/gallery/getChemThumbnail/${id}/${Math.floor(time / 1000)}`
    );
  return Result.Error([new Error("Not a chemistry file")]);
}

/**
 * For some file types we generate thumbnails of the content. For others we
 * have thumbnails to represent all files of that type.
 */
function generateIconSrc(
  name: string,
  type: string,
  extension: string | null,
  thumbnailId: number | null,
  id: Id,
  modificationDate: Date,
  isFolder: boolean,
  isSystemFolder: boolean,
  file: GalleryFile,
  section: GallerySection
) {
  if (isFolder) {
    if (isSystemFolder) return "/images/icons/system_folder.svg";
    return "/images/icons/folder.svg";
  }
  if (section === GALLERY_SECTION.DMPS) {
    return "/images/icons/dmp.svg";
  }
  return idToString(id)
    .flatMap((idStr) => {
      if (type === "Image")
        return Result.Ok(
          `/gallery/getThumbnail/${idStr}/${Math.floor(
            modificationDate.getTime() / 1000
          )}`
        );
      if (
        (type === "Documents" || type === "PdfDocuments") &&
        thumbnailId !== null
      )
        return Result.Ok(`/image/docThumbnail/${idStr}/${thumbnailId}`);
      return Result.Error<string>([new Error("No pre-computed thumbnail")]);
    })
    .orElseTry(() => chemistryFilePreview(file))
    .orElseGet(() => {
      if (extension === null) return "/images/icons/unknown.svg";
      return fileIconMap.get(extension) ?? "/images/icons/unknown.svg";
    });
}

/**
 * These are files that are stored and managed by the RSpace system. They are
 * accessible across all sections of the Gallery with the exception of the
 * filestores section.
 */
export class LocalGalleryFile implements GalleryFile {
  readonly id: Id;
  readonly globalId: string;
  name: string;
  readonly extension: string | null;
  readonly creationDate: Date;
  readonly modificationDate: Date;
  description: Description;
  readonly type: string;
  readonly ownerName: string;
  readonly gallerySection: GallerySection;
  readonly size: number;
  readonly version: number;
  readonly thumbnailId: number | null;
  downloadHref?: () => Promise<UrlType>;
  private cachedDownloadHref?: UrlType;
  readonly originalImageId: string | null;

  // this will only ever actually be an array of LocalGalleryFile,
  // but getting the types correct here is tricky
  readonly path: ReadonlyArray<GalleryFile>;

  readonly setName: (newName: string) => void;
  readonly setDescription: (newDescription: Description) => void;

  constructor({
    id,
    globalId,
    name,
    extension,
    creationDate,
    modificationDate,
    description,
    type,
    ownerName,
    path,
    gallerySection,
    size,
    version,
    thumbnailId,
    originalImageId,
    token,
  }: {
    id: Id;
    globalId: string;
    name: string;
    extension: string | null;
    creationDate: Date;
    modificationDate: Date;
    description: Description;
    type: string;
    ownerName: string;
    path: ReadonlyArray<GalleryFile>;
    gallerySection: GallerySection;
    size: number;
    version: number;
    thumbnailId: number | null;
    originalImageId: string | null;
    token: string;
  }) {
    makeObservable(this, {
      name: observable,
      description: observable,
    });
    this.id = id;
    this.globalId = globalId;
    this.name = name;
    this.extension = extension;
    this.creationDate = creationDate;
    this.modificationDate = modificationDate;
    this.description = description;
    this.type = type;
    this.ownerName = ownerName;
    this.path = path;
    this.gallerySection = gallerySection;
    this.size = size;
    this.version = version;
    this.thumbnailId = thumbnailId;
    this.setName = action((newName) => {
      this.name = newName;
    });
    this.setDescription = action((newDescription) => {
      this.description = newDescription;
    });
    if (!this.isFolder) {
      this.downloadHref = async () => {
        if (this.cachedDownloadHref) return this.cachedDownloadHref;
        const { data: blob } = await axios.get<Blob>(
          `/api/v1/files/${idToString(this.id).elseThrow()}/file`,
          {
            responseType: "blob",
            headers: {
              Authorization: "Bearer " + token,
            },
          }
        );
        const url = URL.createObjectURL(blob);
        this.cachedDownloadHref = url;
        return url;
      };
    }
    this.originalImageId = originalImageId;
  }

  get key(): string {
    return idToString(this.id).elseThrow();
  }

  deconstructor() {
    if (this.cachedDownloadHref) URL.revokeObjectURL(this.cachedDownloadHref);
  }

  get isFolder(): boolean {
    return /Folder/.test(this.type);
  }

  get isImage(): boolean {
    return /Image/.test(this.type);
  }

  get isSnippet(): boolean {
    return /Snippet/.test(this.type);
  }

  get isSystemFolder(): boolean {
    return /System Folder/.test(this.type);
  }

  get isSnippetFolder(): boolean {
    return this.isSystemFolder && /SNIPPETS/.test(this.name);
  }

  pathAsString(): string {
    return `/${[
      this.gallerySection,
      ...this.path.map(({ name }) => name),
      this.name,
    ].join("/")}/`;
  }

  get thumbnailUrl(): string {
    return generateIconSrc(
      this.name,
      this.type,
      this.extension,
      this.thumbnailId,
      this.id,
      this.modificationDate,
      this.isFolder,
      this.isSystemFolder,
      this,
      this.gallerySection
    );
  }

  transformFilename(f: (filename: string) => string): string {
    if (this.isFolder) return f(this.name);
    return `${f(filenameExceptExtension(this.name))}.${justFilenameExtension(
      this.name
    )}`;
  }

  get linkedDocuments(): React.ReactNode {
    return <LinkedDocumentsPanel file={this} />;
  }

  get canOpen(): Result<null> {
    if (this.isFolder) return Result.Ok(null);
    return Result.Error([new Error("Only folders can be opened.")]);
  }

  get canDuplicate(): Result<null> {
    if (this.isSystemFolder)
      return Result.Error([new Error("Cannot duplicate system folders.")]);
    return Result.Ok(null);
  }

  get canDelete(): Result<null> {
    if (this.isSystemFolder)
      return Result.Error([new Error("Cannot delete system folders.")]);
    return Result.Ok(null);
  }

  get canRename(): Result<null> {
    if (this.isSystemFolder)
      return Result.Error([new Error("Cannot rename system folders.")]);
    return Result.Ok(null);
  }

  get canMoveToIrods(): Result<null> {
    if (this.isSystemFolder)
      return Result.Error([new Error("Cannot move system folders to iRODS.")]);
    return Result.Ok(null);
  }

  get canBeExported(): Result<null> {
    return Result.Ok(null);
  }

  get canBeMoved(): Result<null> {
    return Result.Ok(null);
  }

  get canUploadNewVersion(): Result<null> {
    if (this.isFolder)
      return Result.Error([new Error("Cannot upload new version of folders.")]);
    if (!this.extension)
      return Result.Error([
        new Error(
          "An extension is required to be able to update the file with a new version."
        ),
      ]);
    return Result.Ok(null);
  }

  get canBeLoggedOutOf(): Result<null> {
    return Result.Error([
      new Error("Cannot log out of local files and folders."),
    ]);
  }

  get treeViewItemId(): string {
    return `LOCAL_${idToString(this.id).elseThrow()}`;
  }
}

/**
 * Filestores are remote filesystems (e.g. iRODS, SAMBA, SFTP) that the
 * sysadmin has configured and the user has set up. They can then reference the
 * files in those filestores as if they were stored locally in the RSpace
 * instance.
 */
export class Filestore implements GalleryFile {
  id: Id;
  filesystemId: number;
  filesystemName: string;
  name: string;
  description: Description;
  readonly isFolder: boolean;
  readonly size: number;
  readonly path: ReadonlyArray<GalleryFile>;

  constructor({
    id,
    name,
    filesystemId,
    filesystemName,
  }: {
    id: Id;
    name: string;
    filesystemId: number;
    filesystemName: string;
  }) {
    this.id = id;
    this.name = name;
    this.description = Description.Missing();
    this.isFolder = true;
    this.size = 0;
    this.filesystemId = filesystemId;
    this.filesystemName = filesystemName;
    this.path = [];
  }

  deconstructor() {}

  get key(): string {
    return idToString(this.id).elseThrow();
  }

  get extension(): string | null {
    return null;
  }

  get thumbnailUrl(): string {
    return "/images/icons/filestore.svg";
  }

  pathAsString(): string {
    return "/";
  }

  get isSystemFolder(): boolean {
    return false;
  }

  get isImage(): boolean {
    return false;
  }

  get isSnippet(): boolean {
    return false;
  }

  get isSnippetFolder(): boolean {
    return false;
  }

  transformFilename(f: (filename: string) => string): string {
    return f(this.name);
  }

  get linkedDocuments(): React.ReactNode {
    return null;
  }

  get canOpen(): Result<null> {
    return Result.Ok(null);
  }

  get canDuplicate(): Result<null> {
    return Result.Error([new Error("Cannot duplicate filestores.")]);
  }

  get canDelete(): Result<null> {
    return Result.Ok(null);
  }

  get canRename(): Result<null> {
    return Result.Error([new Error("Cannot rename filestores.")]);
  }

  get canMoveToIrods(): Result<null> {
    return Result.Error([new Error("Cannot move filestores to iRODS.")]);
  }

  get canBeExported(): Result<null> {
    return Result.Error([new Error("Filestores cannot be exported.")]);
  }

  get canBeMoved(): Result<null> {
    return Result.Error([new Error("Filestores cannot be moved.")]);
  }

  get canUploadNewVersion(): Result<null> {
    return Result.Error([
      new Error("Filestores cannot be updated by uploading new versions."),
    ]);
  }

  get canBeLoggedOutOf(): Result<null> {
    return Result.Ok(null);
  }

  get treeViewItemId(): string {
    return `FILESTORE_${idToString(this.id).elseThrow()}`;
  }
}

class RemoteFile implements GalleryFile {
  readonly nfsId: number | null;
  name: string;
  description: Description;
  readonly isFolder: boolean;
  readonly size: number;
  readonly modificationDate: Date;
  readonly path: ReadonlyArray<GalleryFile>;
  downloadHref?: () => Promise<UrlType>;
  logicPath: string;
  remotePath: string;
  private cachedDownloadHref?: UrlType;

  constructor({
    nfsId,
    name,
    folder,
    fileSize,
    modificationDate,
    path,
    logicPath,
    token,
  }: {
    nfsId: number | null;
    name: string;
    folder: boolean;
    fileSize: number;
    modificationDate: Date;
    path: ReadonlyArray<GalleryFile>;
    logicPath: string;
    token: string;
  }) {
    this.nfsId = nfsId;
    this.name = name;
    this.description = Description.Missing();
    this.isFolder = folder;
    this.size = fileSize;
    this.modificationDate = modificationDate;
    this.path = path;
    this.logicPath = logicPath;
    this.remotePath = logicPath.split(":").slice(1).join(":");
    if (!this.isFolder) {
      const filestoreId = path[0].id;
      this.downloadHref = async () => {
        if (this.cachedDownloadHref) return this.cachedDownloadHref;
        const urlSearchParams = new URLSearchParams();
        idToString(this.id).do((id) => {
          urlSearchParams.append("remoteId", id);
        });
        urlSearchParams.append("remotePath", this.remotePath);
        const { data: blob } = await axios.get<Blob>(
          `/api/v1/gallery/filestores/${idToString(
            filestoreId
          ).elseThrow()}/download`,
          {
            params: urlSearchParams,
            responseType: "blob",
            headers: {
              Authorization: "Bearer " + token,
            },
          }
        );
        const url = URL.createObjectURL(blob);
        this.cachedDownloadHref = url;
        return url;
      };
    }
  }

  deconstructor() {
    if (this.cachedDownloadHref) URL.revokeObjectURL(this.cachedDownloadHref);
  }

  get id(): Id {
    return this.nfsId;
  }

  get key(): string {
    return this.logicPath;
  }

  get extension(): string | null {
    return null;
  }

  get thumbnailUrl(): string {
    return generateIconSrc(
      this.name,
      "",
      justFilenameExtension(this.name),
      null,
      -1,
      this.modificationDate,
      this.isFolder,
      false,
      this,
      GALLERY_SECTION.NETWORKFILES
    );
  }

  pathAsString(): string {
    const parent = ArrayUtils.last(this.path).elseThrow();
    return `${parent.pathAsString()}${this.name}/`;
  }

  get isSystemFolder(): boolean {
    return false;
  }

  get isImage(): boolean {
    return false;
  }

  get isSnippet(): boolean {
    return false;
  }

  get isSnippetFolder(): boolean {
    return false;
  }

  transformFilename(f: (filename: string) => string): string {
    if (this.isFolder) return f(this.name);
    return `${f(filenameExceptExtension(this.name))}.${justFilenameExtension(
      this.name
    )}`;
  }

  get linkedDocuments(): React.ReactNode {
    return null;
  }

  get canOpen(): Result<null> {
    if (this.isFolder) return Result.Ok(null);
    return Result.Error([new Error("Only folders can be opened.")]);
  }

  get canDuplicate(): Result<null> {
    return Result.Error([
      new Error(
        `Cannot duplicate ${
          this.isFolder ? "folders" : "files"
        } stored in filestores.`
      ),
    ]);
  }

  get canDelete(): Result<null> {
    return Result.Error([
      new Error(
        `Cannot delete ${
          this.isFolder ? "folders" : "files"
        } stored in filestores.`
      ),
    ]);
  }

  get canRename(): Result<null> {
    return Result.Error([
      new Error(
        `Cannot rename ${
          this.isFolder ? "folders" : "files"
        } stored in filestores.`
      ),
    ]);
  }

  get canMoveToIrods(): Result<null> {
    return Result.Error([
      new Error(
        `Cannot move ${
          this.isFolder ? "folders" : "files"
        } stored in filestores to iRODS.`
      ),
    ]);
  }

  get canBeExported(): Result<null> {
    return Result.Error([
      new Error("Contents of filestores cannot be exported."),
    ]);
  }

  get canBeMoved(): Result<null> {
    return Result.Error([
      new Error("Contents of filestores cannot be moved from within RSpace."),
    ]);
  }

  get canUploadNewVersion(): Result<null> {
    return Result.Error([
      new Error(
        "Contents of filestores cannot be updated by uploading new versions."
      ),
    ]);
  }

  get canBeLoggedOutOf(): Result<null> {
    return Result.Error([
      new Error("Cannot log out of files stored in filestores."),
    ]);
  }

  get treeViewItemId(): string {
    const filestoreId = this.path[0].id;
    return `REMOTE_FILE_${idToString(filestoreId).elseThrow()}_${idToString(
      this.id
    ).elseThrow()}`;
  }
}

function parseGalleryFileFromFolderApiResponse(
  obj: object,
  path: ReadonlyArray<GalleryFile>
): Result<LocalGalleryFile> {
  try {
    const id = Parsers.getValueWithKey("id")(obj)
      .flatMap(Parsers.isNumber)
      .elseThrow();
    const globalId = Parsers.getValueWithKey("globalId")(obj)
      .flatMap(Parsers.isString)
      .elseThrow();
    const name = Parsers.getValueWithKey("name")(obj)
      .flatMap(Parsers.isString)
      .elseThrow();
    const creationDate = Parsers.getValueWithKey("created")(obj)
      .flatMap(Parsers.isString)
      .flatMap(Parsers.parseDate)
      .elseThrow();
    const modificationDate = Parsers.getValueWithKey("lastModified")(obj)
      .flatMap(Parsers.isString)
      .flatMap(Parsers.parseDate)
      .elseThrow();
    const mediaType = Parsers.getValueWithKey("mediaType")(obj)
      .flatMap(Parsers.isString)
      .flatMap(parseGallerySection)
      .elseThrow();
    return Result.Ok(
      new LocalGalleryFile({
        id,
        globalId,
        name,
        extension: null,
        creationDate,
        modificationDate,
        description: Description.Missing(),
        type: "Folder",
        ownerName: "Unknown owner",
        path,
        gallerySection: mediaType,
        size: 0,
        version: 1,
        thumbnailId: null,
        originalImageId: null,
        token: "",
      })
    );
  } catch (e) {
    return Result.Error([e instanceof Error ? e : new Error("Unknown error")]);
  }
}

/**
 * Hook that gets a listing of Gallery files, for displaying in the UI.
 */
export function useGalleryListing({
  listingOf,
  searchTerm,
  sortOrder,
  orderBy,
  foldersOnly,
}: {
  /**
   * The location within the Gallery that this listing should show. A location
   * in the Gallery can be defined in one of two ways:
   *   - Either as a path down from the root of a section of the Gallery
   *   - Or as a specific folder
   */
  listingOf:
    | {
        tag: "section";
        section: GallerySection;
        path: ReadonlyArray<GalleryFile>;
      }
    | { tag: "folder"; folderId: number };

  /**
   * The contents of folders within the local sections of the Gallery can be
   * filtered based on a search term. Do note that this does not search the
   * entire Gallery nor the whole section, but rather just the current folder.
   * It is not available at all when `section` is "NetworkFiles" and the
   * listing is of remote files.
   */
  searchTerm: string;

  /**
   * When viewing a listing of a local section, the listing can be sorted.
   */
  sortOrder: "DESC" | "ASC";
  orderBy: "name" | "modificationDate";

  /**
   * When viewing a listing of a local section, the listing can be filtered to
   * only return folders. This is so that pagination works correctly within the
   * move dialog where only folders are shown.
   */
  foldersOnly?: boolean;
}): {
  galleryListing: FetchingData.Fetched<
    | { tag: "empty"; reason: string; refreshing: boolean }
    | {
        tag: "list";
        totalHits: number;
        list: ReadonlyArray<GalleryFile>;
        loadMore: Optional<() => Promise<void>>;
        refreshing: boolean;
      }
  >;
  refreshListing: () => Promise<void>;
  path: FetchingData.Fetched<ReadonlyArray<GalleryFile>>;
  folderId: FetchingData.Fetched<Id>;
  selectedSection: FetchingData.Fetched<GallerySection>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);
  const [loading, setLoading] = React.useState(true);
  const [errorState, setErrorState] = React.useState(false);
  const [refreshing, setRefreshing] = React.useState(false);
  const [galleryListing, setGalleryListing] = React.useState<
    ReadonlyArray<GalleryFile>
  >([]);
  const [api] = React.useState(
    getToken().then((token) =>
      axios.create({
        baseURL: "/api/v1",
        headers: {
          Authorization: "Bearer " + token,
        },
      })
    )
  );

  /*
   * If the listing is of a section, then the path to the folder and the
   * gallery section are known directly. If the listing is of a particular
   * folder, then the path to the folder and the section need to be fetched.
   */
  const [directFolderPath, setDirectFolderPath] = React.useState<
    FetchingData.Fetched<ReadonlyArray<GalleryFile>>
  >({ tag: "loading" as const });
  const [directSection, setDirectSection] = React.useState<
    FetchingData.Fetched<GallerySection>
  >({ tag: "loading" as const });
  React.useEffect(() => {
    if (listingOf.tag === "folder") {
      void (async () => {
        try {
          const response = await (
            await api
          ).get<unknown>(
            `folders/${listingOf.folderId}?includePathToRootFolder=true`
          );
          const data = Parsers.isObject(response.data).flatMap(
            Parsers.isNotNull
          );
          setDirectSection(
            data
              .flatMap(Parsers.getValueWithKey("mediaType"))
              .flatMap(Parsers.isString)
              .flatMap(parseGallerySection)
              .map((value) => ({ tag: "success" as const, value }))
              .elseThrow()
          );

          const path = data
            .flatMap(Parsers.getValueWithKey("pathToRootFolder"))
            .flatMap(Parsers.isArray)
            .map((array) => array.toReversed())
            .elseThrow();

          if (path.length === 1) {
            // the folder we fetched is the gallery section root
            setDirectFolderPath({ tag: "success", value: [] });
            return;
          }

          /*
           * We drop the last two "folders" as the penultimate folder is the
           * gallery section and the last folder is the gallery itself.
           */
          const [, , ...pathToRootFolder] = path;
          const parents = pathToRootFolder.reduce(
            (p: ReadonlyArray<GalleryFile>, obj: unknown) => [
              ...p,
              Parsers.isObject(obj)
                .flatMap(Parsers.isNotNull)
                .flatMap((folderObj) =>
                  parseGalleryFileFromFolderApiResponse(folderObj, p)
                )
                .elseThrow(),
            ],
            []
          );
          /*
           * this code does not work when the folderId is that of the
           * root of the gallery section. This can happen when the user
           * goes to /gallery/item/<id>, where <id> is the id of a file
           * at the root of the gallery section, but in principle could
           * happen at any time when we link to the root of the section.
           *
           * When that happens, `pathToRootFolder` will only contain one
           * element
           */
          setDirectFolderPath({
            tag: "success",
            value: [
              ...parents,
              data
                .flatMap(Parsers.isObject)
                .flatMap(Parsers.isNotNull)
                .flatMap((folderObj) =>
                  parseGalleryFileFromFolderApiResponse(folderObj, parents)
                )
                .elseThrow(),
            ],
          });
        } catch (e) {
          setLoading(false);
          setErrorState(true);
          console.error(e);
          if (!(e instanceof Error)) {
            setDirectFolderPath({ tag: "error", error: "Unknown error" });
            setDirectSection({ tag: "error", error: "Unknown error" });
            return;
          }
          setDirectFolderPath({ tag: "error", error: e.message });
          setDirectSection({ tag: "error", error: e.message });
        }
      })();
    }
  }, [listingOf, api]);
  const section =
    listingOf.tag === "section"
      ? { tag: "success" as const, value: listingOf.section }
      : directSection;
  const path =
    listingOf.tag === "section"
      ? { tag: "success" as const, value: listingOf.path }
      : directFolderPath;

  const [page, setPage] = React.useState<number>(0);
  const [totalPages, setTotalPages] = React.useState<number>(0);
  const [totalHits, setTotalHits] = React.useState<number>(0);
  const [parentId, setParentId] = React.useState<Result<Id>>(
    Result.Error([new Error("Parent Id is not yet known")])
  );
  const selection = useGallerySelection();
  const { login } = useFilestoreLogin();

  function emptyReason(): string {
    if (errorState) return "Error loading files.";
    return Result.lift2<ReadonlyArray<GalleryFile>, GallerySection, string>(
      (p, s) => {
        if (p.length > 0) {
          const folderName = p[p.length - 1].name;
          if (searchTerm !== "")
            return `Nothing in the folder "${folderName}" matches the search term "${searchTerm}".`;
          return `The folder "${folderName}" is empty.`;
        }
        if (s === "NetworkFiles") return "Add a filestore in the Create menu.";
        if (searchTerm !== "")
          return `There are no top-level ${gallerySectionCollectiveNoun[s]} that match the search term "${searchTerm}".`;
        return `There are no top-level ${gallerySectionCollectiveNoun[s]}.`;
      }
    )(
      FetchingData.getSuccessValue(path),
      FetchingData.getSuccessValue(section)
    ).orElse("Loading...");
  }

  function clearAndSetGalleryListing(list: ReadonlyArray<GalleryFile>) {
    galleryListing.forEach((file) => {
      file.deconstructor();
    });
    setGalleryListing(list);
  }

  function parseGalleryFiles(
    data: unknown,
    token: string,
    p: ReadonlyArray<GalleryFile>
  ) {
    return Parsers.objectPath(["data", "items", "results"], data)
      .flatMap(Parsers.isArray)
      .map((array) => {
        return Result.all(
          ...array.map((m) =>
            Parsers.isObject(m)
              .flatMap(Parsers.isNotNull)
              .flatMap((obj) => {
                try {
                  const id = Parsers.getValueWithKey("id")(obj)
                    .flatMap(Parsers.isNumber)
                    .elseThrow();

                  const globalId = Parsers.getValueWithKey("oid")(obj)
                    .flatMap(Parsers.isObject)
                    .flatMap(Parsers.isNotNull)
                    .flatMap(Parsers.getValueWithKey("idString"))
                    .flatMap(Parsers.isString)
                    .elseThrow();

                  const name = Parsers.getValueWithKey("name")(obj)
                    .flatMap(Parsers.isString)
                    .elseThrow();

                  const ownerName = Parsers.getValueWithKey("ownerFullName")(
                    obj
                  )
                    .flatMap(Parsers.isString)
                    .orElse("Unknown owner");

                  const description = Parsers.getValueWithKey("description")(
                    obj
                  )
                    .flatMap(Parsers.isString)
                    .map((d) => {
                      if (d === "") return Description.Empty();
                      return Description.Present(d);
                    })
                    .orElseTry(() =>
                      Parsers.getValueWithKey("description")(obj)
                        .flatMap(Parsers.isNull)
                        .map(() => Description.Empty())
                    )
                    .orElse(Description.Missing());

                  const creationDate = Parsers.getValueWithKey("creationDate")(
                    obj
                  )
                    .flatMap(Parsers.isNumber)
                    .flatMap(Parsers.parseDate)
                    .elseThrow();

                  const modificationDate = Parsers.getValueWithKey(
                    "modificationDate"
                  )(obj)
                    .flatMap(Parsers.isNumber)
                    .flatMap(Parsers.parseDate)
                    .elseThrow();

                  const type = Parsers.getValueWithKey("type")(obj)
                    .flatMap(Parsers.isString)
                    .elseThrow();

                  const extension = Parsers.getValueWithKey("extension")(obj)
                    .flatMap((e) =>
                      Parsers.isString(e).orElseTry(() => Parsers.isNull(e))
                    )
                    .elseThrow();

                  const thumbnailId = Parsers.getValueWithKey("thumbnailId")(
                    obj
                  )
                    .flatMap((t) =>
                      Parsers.isNumber(t).orElseTry(() => Parsers.isNull(t))
                    )
                    .elseThrow();

                  const size = Parsers.getValueWithKey("size")(obj)
                    .flatMap(Parsers.isNumber)
                    .elseThrow();

                  const version = Parsers.getValueWithKey("version")(obj)
                    .flatMap(Parsers.isNumber)
                    .elseThrow();

                  const originalImageId = Parsers.objectPath(
                    ["originalImageOid", "idString"],
                    obj
                  )
                    .flatMap(Parsers.isString)
                    .orElse(null);

                  return Result.Ok<GalleryFile>(
                    new LocalGalleryFile({
                      id,
                      globalId,
                      name,
                      extension,
                      creationDate,
                      modificationDate,
                      description,
                      type,
                      ownerName,
                      path: p,
                      gallerySection:
                        FetchingData.getSuccessValue(section).elseThrow(),
                      size,
                      version,
                      thumbnailId,
                      originalImageId,
                      token,
                    })
                  );
                } catch (e) {
                  return Result.Error<GalleryFile>([
                    e instanceof Error ? e : new Error("Unknown error"),
                  ]);
                }
              })
          )
        ).orElseGet<ReadonlyArray<GalleryFile>>((errors) => {
          addAlert(
            mkAlert({
              variant: "error",
              title: "Could not process Gallery content.",
              message: "Please try refreshing.",
            })
          );
          errors.forEach((e) => {
            console.error(e);
          });
          return [];
        });
      })
      .orElseGet(() => {
        Parsers.isObject(data)
          .flatMap(Parsers.isNotNull)
          .flatMap(Parsers.getValueWithKey("exceptionMessage"))
          .flatMap(Parsers.isString)
          .do((exceptionMessage) => {
            addAlert(
              mkAlert({
                variant: "error",
                title: "Error retrieving gallery files.",
                message: exceptionMessage,
              })
            );
          });
        return [];
      });
  }

  async function getFilestores(): Promise<void> {
    selection.clear();
    clearAndSetGalleryListing([]);
    setLoading(true);
    try {
      const { data } = await (await api).get<unknown>("gallery/filestores");
      Parsers.isArray(data)
        .flatMap((array) =>
          Result.all(
            ...array.map((mixed) =>
              Parsers.isObject(mixed)
                .flatMap(Parsers.isNotNull)
                .flatMap((obj) => {
                  try {
                    const id = Parsers.getValueWithKey("id")(obj)
                      .flatMap(Parsers.isNumber)
                      .elseThrow();

                    const name = Parsers.getValueWithKey("name")(obj)
                      .flatMap(Parsers.isString)
                      .elseThrow();

                    const filesystem = Parsers.getValueWithKey("fileSystem")(
                      obj
                    )
                      .flatMap(Parsers.isObject)
                      .flatMap(Parsers.isNotNull)
                      .elseThrow();

                    const filesystemId = Parsers.getValueWithKey("id")(
                      filesystem
                    )
                      .flatMap(Parsers.isNumber)
                      .elseThrow();

                    const filesystemName = Parsers.getValueWithKey("name")(
                      filesystem
                    )
                      .flatMap(Parsers.isString)
                      .elseThrow();

                    return Result.Ok<GalleryFile>(
                      new Filestore({
                        id,
                        name,
                        filesystemId,
                        filesystemName,
                      })
                    );
                  } catch (e) {
                    return Result.Error<GalleryFile>([
                      e instanceof Error ? e : new Error("Unknown error"),
                    ]);
                  }
                })
            )
          )
        )
        .do(clearAndSetGalleryListing);

      setParentId(
        Result.Error([new Error("Remote filesystems don't have parent ids")])
      );

      Parsers.isArray(data)
        .map((filestores) => filestores.length)
        .do(setTotalHits);

      setTotalPages(1);
    } catch (e) {
      setErrorState(true);
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error retrieving filestores.",
          message: "Please try refreshing.",
        })
      );
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function getRemoteFiles(pa: ReadonlyArray<GalleryFile>): Promise<void> {
    selection.clear();
    clearAndSetGalleryListing([]);
    setLoading(true);
    const filestore = ArrayUtils.getAt(0, pa)
      .toResult(
        () =>
          new Error(
            "Remote files path should never be empty. Where is the filestore?"
          )
      )
      .flatMap<Filestore>((p) =>
        p instanceof Filestore
          ? Result.Ok(p)
          : Result.Error([new Error("First part of path isn't a filestore")])
      )
      .elseThrow();

    try {
      const token = await getToken();
      const { data } = await (
        await api
      ).get<unknown>(
        `gallery/filestores/${idToString(
          filestore.id
        ).elseThrow()}/browse?remotePath=${ArrayUtils.last(pa)
          .map((file) => file.pathAsString())
          .orElse("/")}`
      );
      Parsers.isObject(data)
        .flatMap(Parsers.isNotNull)
        .flatMap(Parsers.getValueWithKey("content"))
        .flatMap(Parsers.isArray)
        .flatMap((array) =>
          Result.all(
            ...array.map((mixed) =>
              Parsers.isObject(mixed)
                .flatMap(Parsers.isNotNull)
                .flatMap((obj) => {
                  try {
                    const nfsId = Parsers.getValueWithKey("nfsId")(obj)
                      .flatMap(Parsers.isNumber)
                      .orElse(null);

                    const name = Parsers.getValueWithKey("name")(obj)
                      .flatMap(Parsers.isString)
                      .elseThrow();

                    const folder = Parsers.getValueWithKey("folder")(obj)
                      .flatMap(Parsers.isBoolean)
                      .elseThrow();

                    const fileSize = Parsers.getValueWithKey("fileSize")(obj)
                      .flatMap(Parsers.isNumber)
                      .elseThrow();

                    const modificationDate = Parsers.getValueWithKey(
                      "modificationDate"
                    )(obj)
                      .flatMap(Parsers.isString)
                      .flatMap(Parsers.parseDate)
                      .elseThrow();

                    const logicPath = Parsers.getValueWithKey("logicPath")(obj)
                      .flatMap(Parsers.isString)
                      .elseThrow();

                    return Result.Ok<GalleryFile>(
                      new RemoteFile({
                        nfsId,
                        name,
                        folder,
                        fileSize,
                        modificationDate,
                        path: pa,
                        logicPath,
                        token,
                      })
                    );
                  } catch (e) {
                    return Result.Error<GalleryFile>([
                      e instanceof Error ? e : new Error("Unknown error"),
                    ]);
                  }
                })
            )
          )
        )
        .do(clearAndSetGalleryListing);

      setParentId(
        Result.Error([new Error("Remote filesystems don't have parent ids")])
      );

      Parsers.isObject(data)
        .flatMap(Parsers.isNotNull)
        .flatMap(Parsers.getValueWithKey("content"))
        .flatMap(Parsers.isArray)
        .map((filestores) => filestores.length)
        .do(setTotalHits);

      setTotalPages(1);
    } catch (e) {
      console.error(e);
      if (
        Parsers.objectPath(["response", "status"], e)
          .flatMap(Parsers.isNumber)
          .flatMap((status) =>
            status === 403
              ? Result.Ok(null)
              : Result.Error([new Error("Not a 403")])
          )
          .flatMap(() =>
            Parsers.objectPath(["response", "data", "message"], e)
              .flatMap(Parsers.isString)
              .flatMap((message) =>
                new RegExp("Call '/login' endpoint first?").test(message)
                  ? Result.Ok(true)
                  : Result.Error([new Error("Not a login error")])
              )
          )
          .orElse(false)
      ) {
        if (
          await login({
            filesystemName: filestore.filesystemName,
            filesystemId: filestore.filesystemId,
          })
        ) {
          await getRemoteFiles(pa);
        } else {
          setErrorState(true);
        }
      } else {
        if (e instanceof Error) {
          addAlert(
            mkAlert({
              variant: "error",
              title: "Error retrieving remote files.",
              message: e.message,
            })
          );
          setErrorState(true);
          throw e;
        }
        throw new Error("Unexpected error");
      }
    } finally {
      setLoading(false);
    }
  }

  async function getGalleryFiles(
    p: ReadonlyArray<GalleryFile>,
    s: GallerySection
  ): Promise<void> {
    setErrorState(false);
    if (s === "NetworkFiles" && p.length === 0) {
      return getFilestores();
    }
    if (s === "NetworkFiles") {
      return getRemoteFiles(p);
    }
    selection.clear();
    clearAndSetGalleryListing([]);
    setLoading(true);
    try {
      const token = await getToken();

      let currentFolderId = "0";
      if (p.length > 0) {
        currentFolderId = idToString(p[p.length - 1].id).elseThrow();
      } else if (listingOf.tag === "folder") {
        currentFolderId = `${listingOf.folderId}`;
      }

      const { data } = await axios.get<unknown>(`/gallery/getUploadedFiles`, {
        params: new URLSearchParams({
          mediatype: s,
          currentFolderId,
          name: searchTerm,
          pageNumber: "0",
          sortOrder,
          orderBy,
          foldersOnly:
            foldersOnly !== null && Boolean(foldersOnly) ? "true" : "false",
        }),
      });

      setParentId(
        Parsers.isObject(data)
          .flatMap(Parsers.isNotNull)
          .flatMap(Parsers.getValueWithKey("data"))
          .flatMap(Parsers.isObject)
          .flatMap(Parsers.isNotNull)
          .flatMap(Parsers.getValueWithKey("parentId"))
          .flatMap(Parsers.isNumber)
      );

      setTotalPages(
        Parsers.objectPath(["data", "items", "totalPages"], data)
          .flatMap(Parsers.isNumber)
          .orElse(1)
      );

      setTotalHits(
        Parsers.objectPath(["data", "items", "totalHits"], data)
          .flatMap(Parsers.isNumber)
          .orElse(1)
      );

      clearAndSetGalleryListing(parseGalleryFiles(data, token, p));
    } catch (e) {
      console.error(e);
      setErrorState(true);
      if (e instanceof Error) {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Error retrieving gallery files.",
            message: e.message,
          })
        );
      }
    } finally {
      setLoading(false);
    }
  }

  async function loadMore(): Promise<void> {
    setPage(page + 1);
    try {
      const token = await getToken();
      const s = FetchingData.getSuccessValue(section).elseThrow();
      const p = FetchingData.getSuccessValue(path).elseThrow();
      const { data } = await axios.get<unknown>(`/gallery/getUploadedFiles`, {
        params: new URLSearchParams({
          mediatype: s,
          currentFolderId:
            p.length > 0 ? idToString(p[p.length - 1].id).elseThrow() : "0",
          name: searchTerm,
          pageNumber: `${page + 1}`,
          sortOrder,
          orderBy,
          foldersOnly:
            foldersOnly !== null && Boolean(foldersOnly) ? "true" : "false",
        }),
      });

      setGalleryListing([
        ...galleryListing,
        ...parseGalleryFiles(data, token, p),
      ]);
    } catch (e) {
      console.error(e);
    }
  }

  React.useEffect(() => {
    Result.lift2<ReadonlyArray<GalleryFile>, GallerySection, void>((p, s) => {
      setPage(0);
      setTotalPages(0);
      void getGalleryFiles(p, s);
    })(
      FetchingData.getSuccessValue(path),
      FetchingData.getSuccessValue(section)
    );
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - getGalleryFiles will not meaningfully change
     */
  }, [searchTerm, sortOrder, orderBy, listingOf, directFolderPath]);

  if (loading)
    return {
      galleryListing: { tag: "loading" },
      path,
      folderId: { tag: "loading" },
      refreshListing: () => Promise.resolve(),
      selectedSection: section,
    };

  return {
    galleryListing: {
      tag: "success",
      value:
        galleryListing.length > 0
          ? {
              tag: "list",
              list: galleryListing,
              totalHits,
              loadMore:
                page + 1 < totalPages
                  ? Optional.present(loadMore)
                  : Optional.empty(),
              refreshing,
            }
          : { tag: "empty", reason: emptyReason(), refreshing },
    },
    path,
    folderId: parentId
      .map((value: Id) => ({ tag: "success" as const, value }))
      .orElseGet(([error]) => ({ tag: "error", error: error.message })),
    refreshListing: async () => {
      const pa = FetchingData.getSuccessValue(path).elseThrow();
      const s = FetchingData.getSuccessValue(section).elseThrow();
      let newTotalHits: null | number = null;
      if (s === GALLERY_SECTION.NETWORKFILES) {
        if (pa.length === 0) {
          return getFilestores();
        }
        throw new Error(
          "refreshListing is not implemented for filestore contents"
        );
      }
      try {
        const token = await getToken();
        setRefreshing(true);
        const newFiles = (
          await Promise.all(
            [...take(incrementForever(), page + 1)].map((p) =>
              axios
                .get<unknown>(`/gallery/getUploadedFiles`, {
                  params: new URLSearchParams({
                    mediatype: s,
                    currentFolderId:
                      pa.length > 0
                        ? idToString(pa[pa.length - 1].id).elseThrow()
                        : "0",
                    name: searchTerm,
                    pageNumber: `${p}`,
                    sortOrder,
                    orderBy,
                    foldersOnly:
                      foldersOnly !== null && Boolean(foldersOnly)
                        ? "true"
                        : "false",
                  }),
                })
                .then(({ data }) => {
                  Parsers.objectPath(["exceptionMessage"], data)
                    .flatMap(Parsers.isString)
                    .do((exceptionMessage) => {
                      throw new Error(exceptionMessage);
                    });
                  Parsers.objectPath(["data", "items", "totalHits"], data)
                    .flatMap(Parsers.isNumber)
                    .do((th) => {
                      newTotalHits ??= th;
                    });
                  const newTotalPages = Parsers.objectPath(
                    ["data", "items", "totalPages"],
                    data
                  )
                    .flatMap(Parsers.isNumber)
                    .orElse(1);
                  setTotalPages(newTotalPages);
                  /*
                   * After deleting some files, the number of pages may have
                   * decreased and we don't want to keep making unnecessary
                   * requests for empty pages.
                   */
                  setPage(Math.min(page, newTotalPages - 1));

                  return parseGalleryFiles(data, token, pa);
                })
            )
          )
        ).flat();
        clearAndSetGalleryListing(newFiles);
        if (newTotalHits !== null) setTotalHits(newTotalHits);

        /*
         * If some of the selected files are no longer included in the listing
         * then we clear the selection as it would be quite confusing to allow
         * the user to operate on files they can no longer see. An obvious
         * example is that the user has just performed a delete action but other
         * such scenarios include when duplicating the last file in a page; the
         * selected one will become the first file of the next page that the user
         * needs to load by tapping the "Load More" button.
         *
         * This has the downside of losing the selection when the user is using
         * tree view as if the selected file is within another folder then this
         * logic will not find the whole selection in the root listing and
         * clear the selection.
         */
        const newFilesIds = new Set(newFiles.map(({ id }) => id));
        if (selection.asSet().some((f) => !newFilesIds.has(f.id)))
          selection.clear();
      } catch (e) {
        if (!(e instanceof Error)) {
          return;
        }
        /*
         * This error is thrown when the user tries to open a folder that has
         * just been deleted. We don't want to show an alert for this as the
         * tree node will be removed as soon as the parent folder is done
         * refreshing itself. We could avoid this by only refreshing folders
         * once the parent is done refreshing, propagating the refresh
         * downwards, but that would make the refreshing of deep folder
         * hierarchies substantially slower.
         */
        if (/open a deleted folder/.test(e.message)) return;

        addAlert(
          mkAlert({
            variant: "error",
            title: "Error refreshing Gallery listing.",
            message: e.message,
          })
        );
      } finally {
        setRefreshing(false);
      }
    },
    selectedSection: section,
  };
}
