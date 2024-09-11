//@flow

import Result from "../../util/result";
import * as Parsers from "../../util/parsers";
import * as FetchingData from "../../util/fetchingData";
import { type GalleryFile } from "./useGalleryListing";
import { useDeploymentProperty } from "../useDeploymentProperty";
import useCollabora from "./useCollabora";
import useOfficeOnline from "./useOfficeOnline";

export function useOpen(): (file: GalleryFile) => Result<() => void> {
  return (file) => {
    if (file.open) {
      const f = file.open;
      return Result.Ok(() => f());
    }
    return Result.Error([new Error("Not a folder")]);
  };
}

export function useImagePreviewOfGalleryFile(): (
  file: GalleryFile
) => Result<string> {
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
      .map(() => {
        return "/collaboraOnline/" + file.globalId + "/edit";
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
      .map(() => {
        return "/officeOnline/" + file.globalId + "/view";
      });
  };
}

export function usePdfPreviewOfGalleryFile(): (
  file: GalleryFile
) => Result<string | typeof undefined> {
  return (file) => {
    if (file.extension !== "pdf") return Result.Error([new Error("Not a PDF")]);
    return Result.Ok(file.downloadHref);
  };
}

const ASPOSE_EXTENSIONS = [
  "doc",
  "docx",
  "md",
  "odt",
  "rtf",
  "txt",
  "xls",
  "xlsx",
  "csv",
  "ods",
  "pdf",
  "ppt",
  "pptx",
  "odp",
];

export function useAsposePreviewOfGalleryFile(): (
  file: GalleryFile
) => Result<null> {
  const asposeEnabled = useDeploymentProperty("aspose.enabled");
  return (file) => {
    return FetchingData.getSuccessValue(asposeEnabled)
      .flatMap(Parsers.isBoolean)
      .flatMap(Parsers.isTrue)
      .mapError(() => new Error("Aspose is not enabled"))
      .flatMap(() =>
        ASPOSE_EXTENSIONS.includes(file.extension)
          ? Result.Ok(null)
          : Result.Error([
              new Error(
                "Aspose does not support the extension of the selected file"
              ),
            ])
      );
  };
}
