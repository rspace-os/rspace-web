//@flow

import React from "react";
import axios from "axios";
import { getByKey } from "../../../util/optional";
import { Result } from "../../../util/result";

type GalleryFile = {|
  id: number,
  name: string,
  modificationDate: number,
  type: string,
  thumbnailUrl: string,
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
  type: string,
  extension: string,
  thumbnailId: number | null,
  id: number,
  modificationDate: number
) {
  // TODO: when exactly can id be null?
  if (type === "Image")
    return `/gallery/getThumbnail/${id}/${modificationDate}`;
  if ((type === "Documents" || type === "PdfDocuments") && id !== null)
    return `/image/docThumbnail/${id}/${thumbnailId ?? "none"}`;
  if (type === "Chemistry")
    return `/gallery/getChemThumbnail/${id}/${modificationDate}`;
  return getIconPathForExtension(extension);
}

function mkGalleryFile({
  id,
  name,
  modificationDate,
  type,
  extension,
  thumbnailId,
}: {|
  id: number,
  name: string,
  modificationDate: number,
  type: string,
  extension: string,
  thumbnailId: number | null,
|}): GalleryFile {
  return {
    id,
    name,
    modificationDate,
    type,
    thumbnailUrl: generateIconSrc(
      type,
      extension,
      thumbnailId,
      id,
      modificationDate
    ),
  };
}

export default function useGalleryListing({
  section,
  searchTerm,
}: {|
  section: string,
  searchTerm: string,
|}): {|
  galleryListing: $ReadOnlyArray<GalleryFile>,
|} {
  const [galleryListing, setGalleryListing] = React.useState<
    $ReadOnlyArray<GalleryFile>
  >([]);

  async function getGalleryFiles(params: {|
    section: string,
    searchTerm: string,
  |}): Promise<void> {
    setGalleryListing([]);
    try {
      const { data } = await axios.get<mixed>(`/gallery/getUploadedFiles`, {
        params: new URLSearchParams({
          mediatype: params.section,
          currentFolderId: "0",
          name: searchTerm,
          pageNumber: "0",
          sortOrder: "DESC",
          orderBy: "",
        }),
      });

      const isObject = (m: mixed): Result<{ ... } | null> =>
        typeof m === "object"
          ? Result.Ok(m)
          : Result.Error([new TypeError("Not an object")]);
      const isNotNull = <T>(b: T | null): Result<T> =>
        b === null ? Result.Error<T>([new TypeError("Is null")]) : Result.Ok(b);
      const isArray = (m: mixed): Result<$ReadOnlyArray<mixed>> =>
        Array.isArray(m)
          ? Result.Ok(m)
          : Result.Error([new TypeError("Is not an array")]);
      const isString = (m: mixed): Result<string> =>
        typeof m === "string"
          ? Result.Ok(m)
          : Result.Error([new TypeError("Is not a string")]);
      const isNumber = (m: mixed): Result<number> =>
        typeof m === "number"
          ? Result.Ok(m)
          : Result.Error([new TypeError("Is not a number")]);
      const getValueWithKey = (key: string) => (obj: { ... }) =>
        getByKey(key, obj).toResult(() => new Error(`key '${key}' is missing`));

      setGalleryListing(
        isObject(data)
          .flatMap(isNotNull)
          .flatMap(getValueWithKey("data"))
          .flatMap(isObject)
          .flatMap(isNotNull)
          .flatMap(getValueWithKey("items"))
          .flatMap(isObject)
          .flatMap(isNotNull)
          .flatMap(getValueWithKey("results"))
          .flatMap(isArray)
          .flatMap((array) => {
            if (array.length === 0)
              return Result.Ok<$ReadOnlyArray<GalleryFile>>([]);
            return Result.all(
              ...array.map((m) =>
                isObject(m)
                  .flatMap(isNotNull)
                  .map((obj) => {
                    const idR = getValueWithKey("id")(obj)
                      .flatMap(isNumber)
                      .mapError((errors) => {
                        console.error(errors[0]);
                        return errors[0];
                      });
                    const nameR =
                      getValueWithKey("name")(obj).flatMap(isString);
                    const modificationDateR =
                      getValueWithKey("modificationDate")(obj).flatMap(
                        isNumber
                      );
                    const typeR =
                      getValueWithKey("type")(obj).flatMap(isString);
                    const extensionR =
                      getValueWithKey("extension")(obj).flatMap(isString);
                    const thumbnailIdR = getValueWithKey("thumbnailId")(obj)
                      .flatMap((t) =>
                        typeof t === "number" || t === null
                          ? Result.Ok(t)
                          : Result.Error([
                              new Error("thumbnailId must be number or null"),
                            ])
                      )
                      .mapError((errors) => {
                        console.error(errors[0]);
                        return errors[0];
                      });
                    return mkGalleryFile({
                      id: idR.orElse(0),
                      name: nameR.orElse(""),
                      modificationDate: modificationDateR.orElse(0),
                      type: typeR.orElse(""),
                      extension: extensionR.orElse(""),
                      thumbnailId: thumbnailIdR.orElse(0),
                    });
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

      // setGalleryListing(foo7);
    } catch (e) {
      console.error(e);
    }
  }

  React.useEffect(() => {
    void getGalleryFiles({ section, searchTerm });
  }, [section, searchTerm]);

  return {
    galleryListing,
  };
}
