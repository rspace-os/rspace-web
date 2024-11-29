//@flow

import React from "react";
import axios from "axios";
import Result from "../../util/result";
import * as Parsers from "../../util/parsers";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import * as FetchingData from "../../util/fetchingData";
import * as ArrayUtils from "../../util/ArrayUtils";
import { gallerySectionCollectiveNoun, type GallerySection } from "./common";
import {
  filenameExceptExtension,
  justFilenameExtension,
} from "../../util/files";
import { useGallerySelection } from "./useGallerySelection";
import { observable, action, makeObservable } from "mobx";
import { Optional } from "../../util/optional";
import { type URL } from "../../util/types";
import { take, incrementForever } from "../../util/iterators";
import useOauthToken from "../../common/useOauthToken";
import { useFilestoreLogin } from "./components/FilestoreLoginDialog";

export opaque type Id = number;
// dummyId is for use in tests ONLY
let nextDummyId = 0;
export const dummyId: () => Id = () => {
  return nextDummyId++;
};
export function idToString(id: Id): string {
  return `${id}`;
}

type DescriptionInternalState =
  | {| key: "missing" |}
  | {| key: "empty" |}
  | {| key: "present", value: string |};
export class Description {
  +#state: DescriptionInternalState;

  constructor(state: DescriptionInternalState) {
    this.#state = state;
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

  match<T>(opts: {|
    missing: () => T,
    empty: () => T,
    present: (string) => T,
  |}): T {
    if (this.#state.key === "missing") return opts.missing();
    if (this.#state.key === "empty") return opts.empty();
    return opts.present(this.#state.value);
  }
}

/**
 * These are all the files types for which we have a thumbnail specific for the
 * file type.
 */
function getIconPathForExtension(extension: string) {
  const chemFileExtensions = [
    "skc",
    "mrv",
    "cxsmiles",
    "cxsmarts",
    "cdx",
    "cdxml",
    "csrdf",
    "cml",
    "csmol",
    "cssdf",
    "csrxn",
    "mol",
    "mol2",
    "pdb",
    "rxn",
    "rdf",
    "smiles",
    "smarts",
    "sdf",
    "inchi",
  ];
  const dnaFiles = [
    "fa",
    "gb",
    "gbk",
    "fasta",
    "fa",
    "dna",
    "seq",
    "sbd",
    "embl",
    "ab1",
  ];
  const iconOfSameName = [
    "avi",
    "bmp",
    "doc",
    "docx",
    "flv",
    "gif",
    "jpg",
    "jpeg",
    "m4v",
    "mov",
    "mp3",
    "mp4",
    "mpg",
    "ods",
    "odp",
    "csv",
    "pps",
    "odt",
    "pdf",
    "png",
    "rtf",
    "wav",
    "wma",
    "wmv",
    "xls",
    "xlsx",
    "xml",
    "zip",
  ];

  const ext = extension.toLowerCase();
  if (chemFileExtensions.includes(ext))
    return "/images/icons/chemistry-file.png";
  if (dnaFiles.includes(ext)) return "/images/icons/dna-file.svg";
  if (iconOfSameName.includes(ext)) return `/images/icons/${ext}.png`;
  return (
    ({
      htm: "/images/icons/html.png",
      html: "/images/icons/html.png",
      ppt: "/images/icons/powerpoint.png",
      pptx: "/images/icons/powerpoint.png",
      txt: "/images/icons/txt.png",
      text: "/images/icons/txt.png",
      md: "/images/icons/txt.png",
    }: { [string]: string })[ext] ?? "/images/icons/unknownDocument.png"
  );
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
  id: number,
  modificationDate: Date,
  isFolder: boolean,
  isSystemFolder: boolean
) {
  if (isFolder) {
    if (isSystemFolder) {
      if (/snippets/i.test(name)) return "/images/icons/folder-shared.png";
      return "/images/icons/folder-api-inbox.png";
    }
    return "/images/icons/folder.png";
  }
  if (type === "Image")
    return `/gallery/getThumbnail/${id}/${Math.floor(
      modificationDate.getTime() / 1000
    )}`;
  if (type === "Documents" || type === "PdfDocuments")
    return `/image/docThumbnail/${id}/${thumbnailId ?? "none"}`;
  if (type === "Chemistry")
    return `/gallery/getChemThumbnail/${id}/${Math.floor(
      modificationDate.getTime() / 1000
    )}`;
  if (extension === null) return "/images/icons/unknownDocument.png";
  return getIconPathForExtension(extension);
}

/**
 * Objects of this shape model files and folders in the Gallery.
 */
export interface GalleryFile {
  +id: Id;
  +globalId?: string;
  name: string;

  // null for folders, otherwise usually a non-empty string
  +extension: string | null;

  +creationDate?: Date;
  +modificationDate?: Date;
  +type?: string;
  +thumbnailUrl: string;
  +ownerName?: string;
  description: Description;

  /*
   * A positive natural number, that is incremented whenever the user uploads a
   * new version or otherwise edits the file
   */
  +version?: number;

  // In bytes. Folders are always 0 bytes
  +size: number;

  /*
   * A list of folders from the top gallery section to the parent of this
   * file/folder
   */
  +path: $ReadOnlyArray<GalleryFile>;

  /*
   * The names of the folders that form the path, separated with forward slashes
   */
  pathAsString(): string;

  // if the file is a folder, open it
  +open?: () => void;

  downloadHref?: URL;

  /*
   * These predicates should be false whenever the implementing class cannot
   * be sure that the file is of the respective type.
   */
  +isFolder: boolean;
  +isSystemFolder: boolean;
  +isImage: boolean;
  +isSnippet: boolean;
  +isSnippetFolder: boolean;

  /*
   * There are various places in the UI where the user applies some
   * transformation to the name of either a folder or file. Mainly the rename
   * action, but also when duplicating and in other places too. This method
   * allows the call to generate a new file name by applying a transformation
   * to the name before the extension, leaving the extension in place.
   */
  transformFilename((string) => string): string;

  +setName?: (string) => void;
  +setDescription?: (Description) => void;
}

class LocalGalleryFile implements GalleryFile {
  +id: Id;
  +globalId: string;
  name: string;
  +extension: string | null;
  +creationDate: Date;
  +modificationDate: Date;
  description: Description;
  +type: string;
  +ownerName: string;
  +gallerySection: string;
  +size: number;
  +version: number;
  +thumbnailId: number | null;
  +open: () => void | void;
  downloadHref: URL | void;

  // this will only ever actually be an array of LocalGalleryFile,
  // but getting the types correct here is tricky
  +path: $ReadOnlyArray<GalleryFile>;

  +setName: (string) => void;
  +setDescription: (Description) => void;

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
    setPath,
    gallerySection,
    size,
    version,
    thumbnailId,
  }: {|
    id: Id,
    globalId: string,
    name: string,
    extension: string | null,
    creationDate: Date,
    modificationDate: Date,
    description: Description,
    type: string,
    ownerName: string,
    path: $ReadOnlyArray<GalleryFile>,
    setPath: ($ReadOnlyArray<GalleryFile>) => void,
    gallerySection: string,
    size: number,
    version: number,
    thumbnailId: number | null,
  |}) {
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
    if (this.isFolder) {
      this.open = () => setPath([...path, this]);
    } else {
      this.downloadHref = `/Streamfile/${idToString(this.id)}`;
    }
    this.setName = action((newName) => {
      this.name = newName;
    });
    this.setDescription = action((newDescription) => {
      this.description = newDescription;
    });
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
      this.isSystemFolder
    );
  }

  transformFilename(f: (string) => string): string {
    if (this.isFolder) return f(this.name);
    return `${f(filenameExceptExtension(this.name))}.${justFilenameExtension(
      this.name
    )}`;
  }
}

class Filestore implements GalleryFile {
  id: Id;
  filesystemId: number;
  name: string;
  description: Description;
  +isFolder: boolean;
  +size: number;
  +open: () => void;

  constructor({
    id,
    name,
    filesystemId,
    setPath,
    path,
  }: {|
    id: Id,
    name: string,
    filesystemId: number,
    path: $ReadOnlyArray<GalleryFile>,
    setPath: ($ReadOnlyArray<GalleryFile>) => void,
  |}) {
    this.id = id;
    this.name = name;
    this.description = Description.Missing();
    this.isFolder = true;
    this.size = 0;
    this.open = () => setPath([...path, this]);
    this.filesystemId = filesystemId;
  }

  get extension(): string | null {
    return null;
  }

  get thumbnailUrl(): string {
    return "/images/icons/fileStoreLink.png";
  }

  get path(): $ReadOnlyArray<GalleryFile> {
    return [];
  }

  pathAsString(): string {
    return "";
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

  transformFilename(f: (string) => string): string {
    return f(this.name);
  }
}

class RemoteFile implements GalleryFile {
  +nfsId: number;
  name: string;
  description: Description;
  +isFolder: boolean;
  +size: number;
  +modificationDate: Date;

  constructor({
    nfsId,
    name,
    folder,
    fileSize,
    modificationDate,
  }: {|
    nfsId: number,
    name: string,
    folder: boolean,
    fileSize: number,
    modificationDate: Date,
  |}) {
    this.nfsId = nfsId;
    this.name = name;
    this.description = Description.Missing();
    this.isFolder = folder;
    this.size = fileSize;
    this.modificationDate = modificationDate;
  }

  get id(): Id {
    return this.nfsId;
  }

  get extension(): string | null {
    return null;
  }

  get thumbnailUrl(): string {
    return "/images/icons/unknownDocument.png";
  }

  get path(): $ReadOnlyArray<GalleryFile> {
    return [];
  }

  pathAsString(): string {
    return "";
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

  transformFilename(f: (string) => string): string {
    if (this.isFolder) return f(this.name);
    return `${f(filenameExceptExtension(this.name))}.${justFilenameExtension(
      this.name
    )}`;
  }
}

export function useGalleryListing({
  section,
  searchTerm,
  path: defaultPath,
  sortOrder,
  orderBy,
  foldersOnly,
}: {|
  section: GallerySection,
  searchTerm: string,
  path?: $ReadOnlyArray<GalleryFile>,
  sortOrder: "DESC" | "ASC",
  orderBy: "name" | "modificationDate",
  foldersOnly?: boolean,
|}): {|
  galleryListing: FetchingData.Fetched<
    | {| tag: "empty", reason: string |}
    | {|
        tag: "list",
        totalHits: number,
        list: $ReadOnlyArray<GalleryFile>,
        loadMore: Optional<() => Promise<void>>,
      |}
  >,
  refreshListing: () => Promise<void>,
  path: $ReadOnlyArray<GalleryFile>,
  clearPath: () => void,
  folderId: FetchingData.Fetched<Id>,
|} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);
  const [loading, setLoading] = React.useState(true);
  const [galleryListing, setGalleryListing] = React.useState<
    $ReadOnlyArray<GalleryFile>
  >([]);
  const [page, setPage] = React.useState<number>(0);
  const [totalPages, setTotalPages] = React.useState<number>(0);
  const [totalHits, setTotalHits] = React.useState<number>(0);
  const [path, setPath] = React.useState<$ReadOnlyArray<GalleryFile>>(
    defaultPath ?? []
  );
  const [parentId, setParentId] = React.useState<Result<Id>>(
    Result.Error([new Error("Parent Id is not yet known")])
  );
  const selection = useGallerySelection();
  const { login } = useFilestoreLogin();

  function emptyReason(): string {
    if (path.length > 0) {
      const folderName = path[path.length - 1].name;
      if (searchTerm !== "")
        return `Nothing in the folder "${folderName}" matches the search term "${searchTerm}".`;
      return `The folder "${folderName}" is empty.`;
    }
    if (searchTerm !== "")
      return `There are no top-level ${gallerySectionCollectiveNoun[section]} that match the search term "${searchTerm}".`;
    return `There are no top-level ${gallerySectionCollectiveNoun[section]}.`;
  }

  function parseGalleryFiles(data: mixed) {
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
                      path,
                      gallerySection: section,
                      size,
                      version,
                      thumbnailId,
                      setPath,
                    })
                  );
                } catch (e) {
                  return Result.Error<GalleryFile>([e]);
                }
              })
          )
        ).orElseGet<$ReadOnlyArray<GalleryFile>>((errors) => {
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
        return ([]: $ReadOnlyArray<GalleryFile>);
      });
  }

  async function getFilestores(): Promise<void> {
    selection.clear();
    setGalleryListing([]);
    setLoading(true);
    const api = axios.create({
      baseURL: "/api/v1/gallery",
      headers: {
        Authorization: "Bearer " + (await getToken()),
      },
    });
    try {
      const { data } = await api.get<mixed>("filestores");
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

                    return Result.Ok<GalleryFile>(
                      new Filestore({
                        id,
                        name,
                        filesystemId,
                        path,
                        setPath,
                      })
                    );
                  } catch (e) {
                    return Result.Error<GalleryFile>([e]);
                  }
                })
            )
          )
        )
        .do(setGalleryListing);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function getRemoteFiles(): Promise<void> {
    selection.clear();
    setGalleryListing([]);
    setLoading(true);
    const api = axios.create({
      baseURL: "/api/v1/gallery",
      headers: {
        Authorization: "Bearer " + (await getToken()),
      },
    });
    try {
      const { data } = await api.get<mixed>(
        `filestores/${path[0].id}/browse?remotePath=%2F`
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
                      .elseThrow();

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

                    return Result.Ok<GalleryFile>(
                      new RemoteFile({
                        nfsId,
                        name,
                        folder,
                        fileSize,
                        modificationDate,
                      })
                    );
                  } catch (e) {
                    return Result.Error<GalleryFile>([e]);
                  }
                })
            )
          )
        )
        .do(setGalleryListing);
    } catch (e) {
      console.error(e);
      if (
        e.response?.status === 403 &&
        typeof e.response?.data.message === "string" &&
        new RegExp("Call '/login' endpoint first?").test(
          e.response.data.message
        )
      ) {
        if (await login()) {
          // and then call this function again
        } else {
          ArrayUtils.dropLast(path).do((newPath) => {
            setPath(newPath);
          });
        }
      } else {
        throw e;
      }
    } finally {
      setLoading(false);
    }
  }

  async function getGalleryFiles(): Promise<void> {
    if (section === "NetworkFiles" && path.length === 0) {
      return getFilestores();
    }
    if (section === "NetworkFiles") {
      return getRemoteFiles();
    }
    selection.clear();
    setGalleryListing([]);
    setLoading(true);
    try {
      const { data } = await axios.get<mixed>(`/gallery/getUploadedFiles`, {
        params: new URLSearchParams({
          mediatype: section,
          currentFolderId:
            path.length > 0 ? `${path[path.length - 1].id}` : "0",
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
          .map((x) => x) // possibly a bug in Flow
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

      setGalleryListing(parseGalleryFiles(data));
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function loadMore(): Promise<void> {
    setPage(page + 1);
    try {
      const { data } = await axios.get<mixed>(`/gallery/getUploadedFiles`, {
        params: new URLSearchParams({
          mediatype: section,
          currentFolderId:
            path.length > 0 ? `${path[path.length - 1].id}` : "0",
          name: searchTerm,
          pageNumber: `${page + 1}`,
          sortOrder,
          orderBy,
        }),
      });

      setGalleryListing([...galleryListing, ...parseGalleryFiles(data)]);
    } catch (e) {
      console.error(e);
    }
  }

  React.useEffect(() => {
    setPage(0);
    void getGalleryFiles();
  }, [searchTerm, path, sortOrder, orderBy]);

  /*
   * Whenever section changes, we want to clear the path so that navigating to
   * a different section returns you to the root of the folder hierarchy.
   * However, we don't want to set the path when this custom hook is mounted as
   * setting the path will invoke the above useEffect again -- in addition to
   * the time when it is invoked on mount -- resulting in a second GET request
   * to getUploadedFiles. As such, we use this single flag to ensure that we
   * only change the path when the section is changed on subsequent re-renders.
   */
  const [mounted, setMounted] = React.useState(false);
  React.useEffect(() => {
    if (mounted) {
      setPath(defaultPath ?? []);
    }
    setMounted(true);
  }, [section]);

  if (loading)
    return {
      galleryListing: { tag: "loading" },
      path,
      clearPath: () => {},
      folderId: { tag: "loading" },
      refreshListing: () => Promise.resolve(),
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
            }
          : { tag: "empty", reason: emptyReason() },
    },
    path,
    clearPath: () => setPath([]),
    folderId: parentId
      .map((value: number) => ({ tag: "success", value }))
      .orElseGet(([error]) => ({ tag: "error", error: error.message })),
    refreshListing: async () => {
      const newFiles = (
        await Promise.all(
          [...take(incrementForever(), page + 1)].map((p) =>
            axios
              .get<mixed>(`/gallery/getUploadedFiles`, {
                params: new URLSearchParams({
                  mediatype: section,
                  currentFolderId:
                    path.length > 0 ? `${path[path.length - 1].id}` : "0",
                  name: searchTerm,
                  pageNumber: `${p}`,
                  sortOrder,
                  orderBy,
                }),
              })
              .then(({ data }) => parseGalleryFiles(data))
          )
        )
      ).flat();
      setGalleryListing(newFiles);

      /*
       * If some of the selected files are no longer included in the listing
       * then we clear the selection as it would be quite confusing to allow
       * the user to operate on files they can no longer see. An obvious
       * example is that the user has just performed a delete action but other
       * such scenarios include when duplicating the last file in a page; the
       * selected one will become the first file of the next page that the user
       * needs to load by tapping the "Load More" button.
       */
      const newFilesIds = new Set(newFiles.map(({ id }) => id));
      if (selection.asSet().some((f) => !newFilesIds.has(f.id)))
        selection.clear();
    },
  };
}
