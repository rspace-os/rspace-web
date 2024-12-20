//@flow

import Result from "../../util/result";
import * as Parsers from "../../util/parsers";
import * as FetchingData from "../../util/fetchingData";
import { type GalleryFile } from "./useGalleryListing";
import { useDeploymentProperty } from "../useDeploymentProperty";
import useCollabora from "./useCollabora";
import useOfficeOnline from "./useOfficeOnline";
import { supportedAsposeFile } from "./components/CallableAsposePreview";
import { type URL } from "../../util/types";

export function useImagePreviewOfGalleryFile(): (
  file: GalleryFile
) => Result<URL> {
  return (file) => {
    if (file.isImage && file.downloadHref) return Result.Ok(file.downloadHref);
    return Result.Error([new Error("Not an image")]);
  };
}

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

export function usePdfPreviewOfGalleryFile(): (
  file: GalleryFile
) => Result<string> {
  return (file) => {
    if (file.extension !== "pdf") return Result.Error([new Error("Not a PDF")]);
    if (!file.downloadHref)
      return Result.Error([new Error("URL to download is missing")]);
    return Result.Ok(file.downloadHref);
  };
}

export function useAsposePreviewOfGalleryFile(): (
  file: GalleryFile
) => Result<null> {
  const asposeEnabled = useDeploymentProperty("aspose.enabled");
  return (file) => {
    return FetchingData.getSuccessValue(asposeEnabled)
      .flatMap(Parsers.isBoolean)
      .flatMap(Parsers.isTrue)
      .mapError(() => new Error("Aspose is not enabled"))
      .flatMap(() => supportedAsposeFile(file))
      .map(() => null);
  };
}

export default function usePrimaryAction(): (
  file: GalleryFile
) => Result<
  | {| tag: "open" |}
  | {| tag: "image", downloadHref: string, caption: $ReadOnlyArray<string> |}
  | {| tag: "collabora", url: string |}
  | {| tag: "officeonline", url: string |}
  | {| tag: "pdf", downloadHref: string |}
  | {| tag: "aspose" |}
> {
  const canPreviewAsImage = useImagePreviewOfGalleryFile();
  const canEditWithCollabora = useCollaboraEdit();
  const canEditWithOfficeOnline = useOfficeOnlineEdit();
  const canPreviewAsPdf = usePdfPreviewOfGalleryFile();
  const canPreviewWithAspose = useAsposePreviewOfGalleryFile();

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
        canPreviewWithAspose(file).map(() => ({ tag: "aspose" }))
      );
}
