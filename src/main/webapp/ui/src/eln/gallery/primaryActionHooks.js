//@flow

import Result from "../../util/result";
import * as Parsers from "../../util/parsers";
import * as FetchingData from "../../util/fetchingData";
import { type GalleryFile, chemistryFilePreview } from "./useGalleryListing";
import { useDeploymentProperty } from "../useDeploymentProperty";
import useCollabora from "./useCollabora";
import useOfficeOnline from "./useOfficeOnline";
import { supportedAsposeFile } from "./components/CallableAsposePreview";
import { type URL } from "../../util/types";

const dnaFileExtensions = [
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
/**
 * Hook that provides a function that can be used to check if a file can be
 * previewed as a DNA sequence in SnapGene. If it can, then a function that
 * returns null is returned.
 */
export function useSnapGenePreviewOfGalleryFile(): (
  file: GalleryFile
) => Result<null> {
  const snapGeneEnabled = useDeploymentProperty("snapgene.available");
  return (file) => {
    if (!dnaFileExtensions.includes(file.extension))
      return Result.Error([new Error("The file is not a DNA file")]);
    return FetchingData.getSuccessValue(snapGeneEnabled)
      .flatMap(Parsers.isString)
      .flatMap((str) =>
        str === "ALLOWED"
          ? Result.Ok(null)
          : Result.Error([
              new Error("The system admin has not made SnapGene available"),
            ])
      );
  };
}

/**
 * Hook that provides a function that can be used to check if a file can be
 * previewed as an image. If it can, then a function that produces a URL to
 * image file of such a preview is returned.
 */
export function useImagePreviewOfGalleryFile(): (
  file: GalleryFile
) => Result<() => Promise<URL>> {
  return (file) => {
    if (file.isImage && file.downloadHref) return Result.Ok(file.downloadHref);
    return chemistryFilePreview(file)
      .map((url) => () => Promise.resolve(url))
      .mapError(() => new Error("The file is not an image"));
  };
}

/**
 * Hook that provides a function that can be used to check if a file can be
 * edited with Collabora. If it can, then a URL to the collabora instance where
 * the document can be edited it returned.
 */
export function useCollaboraEdit(): (file: GalleryFile) => Result<string> {
  const collaboraEnabled = useDeploymentProperty("collabora.wopi.enabled");
  const { supportedExts: supportedCollaboraExts } = useCollabora();
  return (file) => {
    return FetchingData.getSuccessValue(collaboraEnabled)
      .flatMap(Parsers.isBoolean)
      .flatMap(Parsers.isTrue)
      .mapError(() => new Error("Collabora is not enabled"))
      .flatMap(() => Parsers.isNotNull(file.extension))
      .flatMap((extension) =>
        supportedCollaboraExts.has(extension)
          ? Result.Ok(null)
          : Result.Error([
              new Error(
                "Selected file's extension is not supported by collabora"
              ),
            ])
      )
      .map(() => file.globalId)
      .flatMap(Parsers.isNotBottom)
      .map((globalId) => {
        return "/collaboraOnline/" + globalId + "/edit";
      });
  };
}

/**
 * Hook that provides a function that can be used to check if a file can be
 * edited with Office Online. If it can, then a URL to the collabora instance
 * where the document can be edited it returned.
 */
export function useOfficeOnlineEdit(): (file: GalleryFile) => Result<string> {
  const officeOnlineEnabled = useDeploymentProperty("msoffice.wopi.enabled");
  const { supportedExts: supportedOfficeOnlineExts } = useOfficeOnline();
  return (file) => {
    return FetchingData.getSuccessValue(officeOnlineEnabled)
      .flatMap(Parsers.isBoolean)
      .flatMap(Parsers.isTrue)
      .mapError(() => new Error("Office Online is not enabled"))
      .flatMap(() => Parsers.isNotNull(file.extension))
      .flatMap((extension) =>
        supportedOfficeOnlineExts.has(extension)
          ? Result.Ok(null)
          : Result.Error([
              new Error(
                "Selected file's extension is not supported by office online"
              ),
            ])
      )
      .map(() => file.globalId)
      .flatMap(Parsers.isNotBottom)
      .map((globalId) => {
        return "/officeOnline/" + globalId + "/view";
      });
  };
}

/**
 * Hook that returns a function that can be used to check if a file is a PDF.
 * If it is, then a function that produces a URL to the PDF file is returned.
 */
export function usePdfPreviewOfGalleryFile(): (
  file: GalleryFile
) => Result<() => Promise<URL>> {
  return (file) => {
    if (file.extension !== "pdf")
      return Result.Error([new Error("The file is not a PDF")]);
    if (!file.downloadHref)
      return Result.Error([new Error("URL to download is missing")]);
    return Result.Ok(file.downloadHref);
  };
}

/**
 * Hook that returns a function that can be used to check if a file can be
 * previewed by having Aspose generate a PDF.
 */
export function useAsposePreviewOfGalleryFile(): (
  file: GalleryFile
) => Result<null> {
  const asposeEnabled = useDeploymentProperty("aspose.enabled");
  return (file) => {
    return FetchingData.getSuccessValue(asposeEnabled)
      .flatMap(Parsers.isBoolean)
      .flatMap(Parsers.isTrue)
      .mapError(() => new Error("Aspose is not enabled"))
      .flatMap(() => supportedAsposeFile(file));
  };
}

/**
 * A hook that returns a function that can be used to determine what primary
 * action should be presented to the user given a particular file. This will
 * depend on the file type and what services as available.
 */
export default function usePrimaryAction(): (file: GalleryFile) => Result<
  | {| tag: "open" |}
  | {|
      tag: "image",
      downloadHref: () => Promise<URL>,
      caption: $ReadOnlyArray<string>,
    |}
  | {| tag: "collabora", url: string |}
  | {| tag: "officeonline", url: string |}
  | {| tag: "pdf", downloadHref: () => Promise<URL> |}
  | {| tag: "aspose" |}
  | {| tag: "snapgene", file: GalleryFile |}
> {
  const canPreviewAsImage = useImagePreviewOfGalleryFile();
  const canEditWithCollabora = useCollaboraEdit();
  const canEditWithOfficeOnline = useOfficeOnlineEdit();
  const canPreviewAsPdf = usePdfPreviewOfGalleryFile();
  const canPreviewWithAspose = useAsposePreviewOfGalleryFile();
  const canPreviewWithSnapGene = useSnapGenePreviewOfGalleryFile();

  return (file) =>
    file.canOpen
      .map(() => ({ tag: "open" }))
      .orElseTry(() =>
        canPreviewAsImage(file).map((downloadHref) => ({
          tag: "image",
          downloadHref,
          caption: [
            file.description.match({
              missing: () => "",
              empty: () => "",
              present: (desc) => desc,
            }),
            file.name,
          ],
        }))
      )
      .orElseTry(() =>
        canEditWithCollabora(file).map((url) => ({ tag: "collabora", url }))
      )
      .orElseTry(() =>
        canEditWithOfficeOnline(file).map((url) => ({
          tag: "officeonline",
          url,
        }))
      )
      .orElseTry(() =>
        canPreviewAsPdf(file).map((downloadHref) => ({
          tag: "pdf",
          downloadHref,
        }))
      )
      .orElseTry(() =>
        canPreviewWithSnapGene(file).map(() => ({ tag: "snapgene", file }))
      )
      .orElseTry(() =>
        canPreviewWithAspose(file).map(() => ({ tag: "aspose" }))
      );
}
