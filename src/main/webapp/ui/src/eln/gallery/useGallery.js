//@flow

import React from "react";
import axios from "axios";
import Result from "../../util/result";
import * as Parsers from "../../util/parsers";
import * as ArrayUtils from "../../util/ArrayUtils";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import * as FetchingData from "../../util/fetchingData";
import { gallerySectionCollectiveNoun } from "./common";

export type GalleryFile = {|
  id: number,
  name: string,
  modificationDate: number,
  type: string,
  thumbnailUrl: string,
  path: $ReadOnlyArray<GalleryFile>,
  open?: () => void,
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
    {
      htm: "/images/icons/html.png",
      html: "/images/icons/html.png",
      ppt: "/images/icons/powerpoint.png",
      pptx: "/images/icons/powerpoint.png",
      txt: "/images/icons/txt.png",
      text: "/images/icons/txt.png",
      md: "/images/icons/txt.png",
    }[ext] ?? "/images/icons/unknownDocument.png"
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
  modificationDate: number
) {
  if (/Folder/.test(type)) {
    if (/System/.test(type)) {
      if (/snippets/i.test(name)) return "/images/icons/folder-shared.png";
      return "/images/icons/folder-api-inbox.png";
    }
    return "/images/icons/folder.png";
  }
  if (type === "Image")
    return `/gallery/getThumbnail/${id}/${modificationDate}`;
  if (type === "Documents" || type === "PdfDocuments")
    return `/image/docThumbnail/${id}/${thumbnailId ?? "none"}`;
  if (type === "Chemistry")
    return `/gallery/getChemThumbnail/${id}/${modificationDate}`;
  if (!extension) return "/images/icons/unknownDocument.png";
  return getIconPathForExtension(extension);
}

export function useGalleryListing({
  section,
  searchTerm,
  path: defaultPath,
}: {|
  section: string,
  searchTerm: string,
  path?: $ReadOnlyArray<GalleryFile>,
|}): {|
  galleryListing: FetchingData.Fetched<
    | {| tag: "empty", reason: string |}
    | {| tag: "list", list: $ReadOnlyArray<GalleryFile> |}
  >,
  refreshListing: () => void,
  path: $ReadOnlyArray<GalleryFile>,
  clearPath: () => void,
  parentId: FetchingData.Fetched<number>,
|} {
  const { addAlert } = React.useContext(AlertContext);
  const [loading, setLoading] = React.useState(true);
  const [galleryListing, setGalleryListing] = React.useState<
    $ReadOnlyArray<GalleryFile>
  >([]);
  const [path, setPath] = React.useState<$ReadOnlyArray<GalleryFile>>(
    defaultPath ?? []
  );
  const [parentId, setParentId] = React.useState<Result<number>>(
    Result.Error([])
  );

  function emptyReason(): string {
    if (path.length > 0) {
      const folderName = path[path.length - 1].name;
      if (searchTerm !== "")
        return `Nothing in the folder "${folderName}" matches the search term "${searchTerm}".`;
      return `The folder "${folderName}" is empty.`;
    }
    if (searchTerm !== "")
      return `There are no root-level ${gallerySectionCollectiveNoun[section]} that match the search term "${searchTerm}".`;
    return `There are no root-level ${gallerySectionCollectiveNoun[section]}.`;
  }

  function mkGalleryFile(
    id: number,
    name: string,
    modificationDate: number,
    type: string,
    extension: string | null,
    thumbnailId: number | null
  ): GalleryFile {
    const ret: GalleryFile = {
      id,
      name,
      modificationDate,
      type,
      thumbnailUrl: generateIconSrc(
        name,
        type,
        extension,
        thumbnailId,
        id,
        modificationDate
      ),
      path,
      ...(/Folder/.test(type)
        ? {
            open: () => {
              setPath([...path, ret]);
            },
          }
        : {}),
    };
    return ret;
  }

  async function getGalleryFiles(): Promise<void> {
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
          sortOrder: "DESC",
          orderBy: "",
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

      setGalleryListing(
        Parsers.objectPath(["data", "items", "results"], data)
          .flatMap(Parsers.isArray)
          .flatMap((array) => {
            if (array.length === 0)
              return Result.Ok<$ReadOnlyArray<GalleryFile>>([]);
            return Result.all(
              ...array.map((m) =>
                Parsers.isObject(m)
                  .flatMap(Parsers.isNotNull)
                  .flatMap((obj) => {
                    return Result.lift6(mkGalleryFile)(
                      Parsers.getValueWithKey("id")(obj).flatMap(
                        Parsers.isNumber
                      ),
                      Parsers.getValueWithKey("name")(obj).flatMap(
                        Parsers.isString
                      ),
                      Parsers.getValueWithKey("modificationDate")(obj).flatMap(
                        Parsers.isNumber
                      ),
                      Parsers.getValueWithKey("type")(obj).flatMap(
                        Parsers.isString
                      ),
                      Parsers.getValueWithKey("extension")(obj).flatMap((e) =>
                        Parsers.isString(e).orElseTry(() => Parsers.isNull(e))
                      ),
                      Parsers.getValueWithKey("thumbnailId")(obj).flatMap((t) =>
                        Parsers.isNumber(t).orElseTry(() => Parsers.isNull(t))
                      )
                    );
                  })
              )
            );
          })
          .orElseTry(() => {
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
            return Result.Ok<$ReadOnlyArray<GalleryFile>>([]);
          })
          .orElseGet((errors) => {
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
  }, [searchTerm, path]);

  React.useEffect(() => {
    setPath(defaultPath ?? []);
  }, [section]);

  if (loading)
    return {
      galleryListing: { tag: "loading" },
      path: [],
      clearPath: () => {},
      parentId: { tag: "loading" },
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
    parentId: parentId
      .map((value: number) => ({ tag: "success", value }))
      .orElseGet(([error]) => ({ tag: "error", error: error.message })),
    refreshListing: () => {
      void getGalleryFiles();
    },
  };
}

export function useGalleryActions(): {|
  uploadFiles: (
    $ReadOnlyArray<GalleryFile>,
    number,
    $ReadOnlyArray<File>
  ) => Promise<void>,
  createFolder: ($ReadOnlyArray<GalleryFile>, number, string) => Promise<void>,
  moveFileWithId: (number) => {|
    to: ({|
      target: string,
      section: string,
    |}) => Promise<void>,
  |},
|} {
  const { addAlert, removeAlert } = React.useContext(AlertContext);

  async function uploadFiles(
    path: $ReadOnlyArray<GalleryFile>,
    parentId: number,
    files: $ReadOnlyArray<File>
  ) {
    const uploadingAlert = mkAlert({
      message: "Uploading...",
      variant: "notice",
      isInfinite: true,
    });
    addAlert(uploadingAlert);

    const targetFolderId = ArrayUtils.getAt(0, path)
      .map(({ id }) => `${id}`)
      .orElse(`${parentId}`);
    try {
      const data = await Promise.all(
        files.map((file) => {
          const formData = new FormData();
          formData.append("xfile", file);
          formData.append("targetFolderId", targetFolderId);
          return axios.post<FormData, mixed>(
            "gallery/ajax/uploadFile",
            formData,
            {
              headers: {
                "Content-Type": "multipart/form-data",
              },
            }
          );
        })
      );

      addAlert(
        Result.any(
          ...data.map((d) =>
            Parsers.objectPath(["data", "exceptionMessage"], d).flatMap(
              Parsers.isString
            )
          )
        )
          .map((exceptionMessages) =>
            mkAlert({
              message: `Failed to upload file${files.length === 1 ? "" : "s"}.`,
              variant: "error",
              details: exceptionMessages.map((m) => ({
                title: m,
                variant: "error",
              })),
            })
          )
          .orElse(
            mkAlert({
              message: `Successfully uploaded file${
                files.length === 1 ? "" : "s"
              }.`,
              variant: "success",
            })
          )
      );
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to upload file${files.length === 1 ? "" : "s"}.`,
          message: e.message,
        })
      );
      throw e;
    } finally {
      removeAlert(uploadingAlert);
    }
  }

  async function createFolder(
    path: $ReadOnlyArray<GalleryFile>,
    parentId: number,
    name: string
  ) {
    const parentFolderId = ArrayUtils.getAt(0, path)
      .map(({ id }) => `${id}`)
      .orElse(`${parentId}`);
    try {
      const formData = new FormData();
      formData.append("folderName", name);
      formData.append("parentId", parentFolderId);
      formData.append("isMedia", "true");
      const data = await axios.post<FormData, mixed>(
        "gallery/ajax/createFolder",
        formData,
        {
          headers: {
            "Content-Type": "multipart/form-data",
          },
        }
      );

      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .flatMap(Parsers.isString)
          .map((exceptionMessage) =>
            mkAlert({
              title: `Failed to create new folder.`,
              message: exceptionMessage,
              variant: "error",
            })
          )
          .orElse(
            mkAlert({
              message: `Successfully created new folder.`,
              variant: "success",
            })
          )
      );
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to create new folder.`,
          message: e.message,
        })
      );
      throw e;
    }
  }

  function moveFileWithId(fileId: number) {
    return {
      to: async ({
        target,
        section,
      }: {|
        target: string,
        section: string,
      |}) => {
        const formData = new FormData();
        formData.append("target", target);
        formData.append("filesId[]", `${fileId}`);
        formData.append("mediaType", section);
        await axios.post<FormData, mixed>(
          "gallery/ajax/moveGalleriesElements",
          formData,
          {
            headers: {
              "Content-Type": "multipart/form-data",
            },
          }
        );
        // TODO handle errors
      },
    };
  }

  return { uploadFiles, createFolder, moveFileWithId };
}
