//@flow

import React from "react";
import axios from "axios";
import Result from "../../util/result";
import * as Parsers from "../../util/parsers";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import * as FetchingData from "../../util/fetchingData";
import { gallerySectionCollectiveNoun, type GallerySection } from "./common";
import {
  filenameExceptExtension,
  justFilenameExtension,
} from "../../util/files";
import { useGallerySelection } from "./useGallerySelection";
import { observable, runInAction } from "mobx";
import { type URL } from "../../util/types";

export opaque type Id = number;
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
 * Objects of this shape model files and folders in the Gallery.
 */
export type GalleryFile = {|
  id: Id,
  globalId: string,
  name: string,

  // null for folders, otherwise usually a non-empty string
  extension: string | null,

  creationDate: Date,
  modificationDate: Date,
  type: string,
  thumbnailUrl: string,
  ownerName: string,
  description: Description,

  /*
   * A positive natural number, that is incremented whenever the user uploads a
   * new version or otherwise edits the file
   */
  version: number,

  // In bytes. Folders are always 0 bytes
  size: number,

  /*
   * A list of folders from the top gallery section to the parent of this
   * file/folder
   */
  path: $ReadOnlyArray<GalleryFile>,

  /*
   * The names of the folders that form the path, separated with forward slashes
   */
  pathAsString: () => string,

  // if the file is a folder, open it
  open?: () => void,

  downloadHref?: URL,

  isFolder: boolean,
  isSystemFolder: boolean,
  isImage: boolean,
  isSnippet: boolean,
  isSnippetFolder: boolean,

  /*
   * There are various places in the UI where the user applies some
   * transformation to the name of either a folder or file. Mainly the rename
   * action, but also when duplicating and in other places too. This method
   * allows the call to generate a new file name by applying a transformation
   * to the name before the extension, leaving the extension in place.
   */
  transformFilename: ((string) => string) => string,

  setName: (string) => void,
  setDescription: (Description) => void,
|};

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
  if (!extension) return "/images/icons/unknownDocument.png";
  return getIconPathForExtension(extension);
}

export function useGalleryListing({
  section,
  searchTerm,
  path: defaultPath,
  sortOrder,
  orderBy,
}: {|
  section: GallerySection,
  searchTerm: string,
  path?: $ReadOnlyArray<GalleryFile>,
  sortOrder: "DESC" | "ASC",
  orderBy: "name" | "modificationDate",
|}): {|
  galleryListing: FetchingData.Fetched<
    | {| tag: "empty", reason: string |}
    | {| tag: "list", list: $ReadOnlyArray<GalleryFile> |}
  >,
  refreshListing: () => void,
  path: $ReadOnlyArray<GalleryFile>,
  clearPath: () => void,
  folderId: FetchingData.Fetched<Id>,
|} {
  const { addAlert } = React.useContext(AlertContext);
  const [loading, setLoading] = React.useState(true);
  const [galleryListing, setGalleryListing] = React.useState<
    $ReadOnlyArray<GalleryFile>
  >([]);
  const [path, setPath] = React.useState<$ReadOnlyArray<GalleryFile>>(
    defaultPath ?? []
  );
  const [parentId, setParentId] = React.useState<Result<Id>>(Result.Error([]));
  const selection = useGallerySelection();

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

  function mkGalleryFile({
    id,
    globalId,
    name,
    ownerName,
    description,
    creationDate,
    modificationDate,
    type,
    extension,
    thumbnailId,
    size,
    version,
  }: {
    id: number,
    globalId: string,
    name: string,
    ownerName: string,
    description: Description,
    creationDate: Date,
    modificationDate: Date,
    type: string,
    extension: string | null,
    thumbnailId: number | null,
    size: number,
    version: number,
  }): GalleryFile {
    const isFolder = /Folder/.test(type);
    const isSystemFolder = /System Folder/.test(type);
    const ret: GalleryFile = observable({
      id,
      globalId,
      name,
      extension,
      ownerName,
      description,
      creationDate,
      modificationDate,
      type,
      size,
      version,
      thumbnailUrl: generateIconSrc(
        name,
        type,
        extension,
        thumbnailId,
        id,
        modificationDate,
        isFolder,
        isSystemFolder
      ),
      path,
      pathAsString: () =>
        `/${[section, ...path.map(({ name }) => name), name].join("/")}/`,
      ...(isFolder
        ? {
            open: () => {
              setPath([...path, ret]);
            },
          }
        : {
            // downloads the latest version, if the version is >1
            downloadHref: `/Streamfile/${idToString(id)}`,
          }),
      isFolder,
      isSystemFolder,
      isImage: /Image/.test(type),
      isSnippet: /Snippet/.test(type),
      isSnippetFolder: isSystemFolder && /SNIPPETS/.test(name),
      transformFilename: (f) => {
        if (isFolder) return f(name);
        return `${f(filenameExceptExtension(name))}.${justFilenameExtension(
          name
        )}`;
      },
      setName: (newName: string) => {
        runInAction(() => {
          ret.name = newName;
        });
      },
      setDescription: (newDescription: Description) => {
        runInAction(() => {
          ret.description = newDescription;
        });
      },
    });
    return ret;
  }

  async function getGalleryFiles(): Promise<void> {
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

      setGalleryListing(
        Parsers.objectPath(["data", "items", "results"], data)
          .flatMap(Parsers.isArray)
          .map((array) => {
            if (array.length === 0) return ([]: $ReadOnlyArray<GalleryFile>);
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

                      const ownerName = Parsers.getValueWithKey(
                        "ownerFullName"
                      )(obj)
                        .flatMap(Parsers.isString)
                        .orElse("Unknown owner");

                      const description = Parsers.getValueWithKey(
                        "description"
                      )(obj)
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

                      const creationDate = Parsers.getValueWithKey(
                        "creationDate"
                      )(obj)
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

                      const extension = Parsers.getValueWithKey("extension")(
                        obj
                      )
                        .flatMap((e) =>
                          Parsers.isString(e).orElseTry(() => Parsers.isNull(e))
                        )
                        .elseThrow();

                      const thumbnailId = Parsers.getValueWithKey(
                        "thumbnailId"
                      )(obj)
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

                      return Result.Ok(
                        mkGalleryFile({
                          id,
                          globalId,
                          name,
                          ownerName,
                          description,
                          creationDate,
                          modificationDate,
                          type,
                          extension,
                          thumbnailId,
                          size,
                          version,
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
            return [];
          })
      );
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  React.useEffect(() => {
    void getGalleryFiles();
  }, [searchTerm, path, sortOrder, orderBy]);

  React.useEffect(() => {
    setPath(defaultPath ?? []);
  }, [section]);

  if (loading)
    return {
      galleryListing: { tag: "loading" },
      path,
      clearPath: () => {},
      folderId: { tag: "loading" },
      refreshListing: () => {},
    };

  return {
    galleryListing: {
      tag: "success",
      value:
        galleryListing.length > 0
          ? { tag: "list", list: galleryListing }
          : { tag: "empty", reason: emptyReason() },
    },
    path,
    clearPath: () => setPath([]),
    folderId: parentId
      .map((value: number) => ({ tag: "success", value }))
      .orElseGet(([error]) => ({ tag: "error", error: error.message })),
    refreshListing: () => {
      void getGalleryFiles();
    },
  };
}
