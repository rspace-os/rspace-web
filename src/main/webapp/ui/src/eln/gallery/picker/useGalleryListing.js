//@flow

import React from "react";
import axios from "axios";
import { Result, lift6 } from "../../../util/result";
import * as Parsers from "../../../util/parsers";

export type GalleryFile = {|
  id: number,
  name: string,
  modificationDate: number,
  type: string,
  thumbnailUrl: string,
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
  // TODO: when exactly can id be null?
  if (/Folder/.test(type)) {
    if (/System/.test(type)) {
      if (/snippets/i.test(name)) return "/images/icons/folder-shared.png";
      return "/images/icons/folder-api-inbox.png";
    }
    return "/images/icons/folder.png";
  }
  if (type === "Image")
    return `/gallery/getThumbnail/${id}/${modificationDate}`;
  if ((type === "Documents" || type === "PdfDocuments") && id !== null)
    return `/image/docThumbnail/${id}/${thumbnailId ?? "none"}`;
  if (type === "Chemistry")
    return `/gallery/getChemThumbnail/${id}/${modificationDate}`;
  if (!extension) return "/images/icons/unknownDocument.png";
  return getIconPathForExtension(extension);
}

export default function useGalleryListing({
  section,
  searchTerm,
}: {|
  section: string,
  searchTerm: string,
|}): {|
  galleryListing: $ReadOnlyArray<GalleryFile>,
  path: $ReadOnlyArray<GalleryFile>,
  clearPath: () => void,
|} {
  const [galleryListing, setGalleryListing] = React.useState<
    $ReadOnlyArray<GalleryFile>
  >([]);
  const [path, setPath] = React.useState<$ReadOnlyArray<GalleryFile>>([]);

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

  async function getGalleryFiles(params: {|
    section: string,
    searchTerm: string,
  |}): Promise<void> {
    setGalleryListing([]);
    try {
      const { data } = await axios.get<mixed>(`/gallery/getUploadedFiles`, {
        params: new URLSearchParams({
          mediatype: params.section,
          currentFolderId:
            path.length > 0 ? `${path[path.length - 1].id}` : "0",
          name: searchTerm,
          pageNumber: "0",
          sortOrder: "DESC",
          orderBy: "",
        }),
      });

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
                    return lift6(
                      mkGalleryFile,
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
          .orElseGet((errors) => {
            errors.forEach((e) => {
              console.error(e);
            });
            return [];
          })
      );
    } catch (e) {
      console.error(e);
    }
  }

  React.useEffect(() => {
    void getGalleryFiles({ section, searchTerm });
  }, [section, searchTerm, path]);

  React.useEffect(() => {
    setPath([]);
  }, [section]);

  return {
    galleryListing,
    path,
    clearPath: () => setPath([]),
  };
}
