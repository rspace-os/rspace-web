import React from "react";
import { type GalleryFile, idToString } from "../useGalleryListing";
import { usePdfPreview } from "./CallablePdfPreview";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as Parsers from "../../../util/parsers";
import * as ArrayUtils from "../../../util/ArrayUtils";
import Result from "../../../util/result";

/*
 * If aspose is configured, then users can preview the contents of various
 * common document file types by running them through the PDF generator and
 * viewing that PDF via CallablePdfPreview.
 *
 * Much like how `window.open` allows any JS code on the page to trigger the
 * opening of in a new window/tab with a specified URL, this module provides a
 * mechanism to allow any JS code to trigger the opening of a dialog by
 * providing a GalleryFile that can be converted to a PDF by Aspose.
 *
 * If the conversion fails or there is an error for any other reason with the
 * network call then an error toast is shown.
 */

export type AsposePreviewDetails = {
  documentId: number;
  fileExtension: string;
  revisionId?: number | null;
  publicView?: boolean;
};

const AsposePreviewContext = React.createContext({
  setFile: (_file: GalleryFile) => Promise.resolve(),
  setDetails: (_details: AsposePreviewDetails) => Promise.resolve(),
  loading: false,
});

/**
 * Use the callable aspose preview component to display a document in a dialog
 * as a PDF.
 */
export function useAsposePreview(): {
  /**
   * Preview the document at this GalleryFile.
   */
  openAsposePreview: (file: GalleryFile) => Promise<void>;

  /**
   * Preview a document by using its ids/extension rather than a GalleryFile.
   */
  openAsposePreviewFromDetails: (details: AsposePreviewDetails) => Promise<void>;

  loading: boolean;
} {
  const {
    setFile: openAsposePreview,
    setDetails: openAsposePreviewFromDetails,
    loading,
  } =
    React.useContext(AsposePreviewContext);
  return {
    openAsposePreview,
    openAsposePreviewFromDetails,
    loading,
  };
}

/**
 * This component provides a mechanism for any other component that is its
 * descendent to trigger the previewing of a document by passing the GalleryFile
 * to a call to `useAsposePreview`'s `openAsposePreview`. Just do something like
 *    const { openAsposePreview } = useImagePreview();
 *    openAsposePreview("http://example.com/document.doc");
 *
 * Relies on being inside of an AlertContext and the context created by
 * CallablePdfPreview
 */
export function CallableAsposePreview({
  children,
}: {
  children: React.ReactNode;
}): React.ReactNode {
  const [loading, setLoading] = React.useState(false);
  const { openPdfPreview } = usePdfPreview();
  const { addAlert } = React.useContext(AlertContext);

  const openConvertedFile = React.useCallback(
    async ({
      documentId,
      fileExtension,
      revisionId = null,
      publicView = false,
    }: AsposePreviewDetails) => {
      const revisionUrlSuffix = revisionId != null ? `&revision=${revisionId}` : "";
      const { data } = await axios.get<unknown>(
        `/Streamfile/ajax/convert/${documentId}?outputFormat=pdf${revisionUrlSuffix}`,
      );
      const fileName = Parsers.isObject(data)
        .flatMap(Parsers.isNotNull)
        .flatMap(Parsers.getValueWithKey("data"))
        .flatMap(Parsers.isString)
        .orElse(null);
      if (fileName) {
        openPdfPreview(
          (publicView ? "/public/publicView" : "") +
            `/Streamfile/direct/${documentId}?fileName=${fileName}`,
        );
        return;
      }
      Parsers.isObject(data)
        .flatMap(Parsers.isNotNull)
        .flatMap(Parsers.getValueWithKey("exceptionMessage"))
        .flatMap(Parsers.isString)
        .do((msg) => {
          throw new Error(msg);
        });
      Parsers.objectPath(["error", "errorMessages"], data)
        .flatMap(Parsers.isArray)
        .flatMap(ArrayUtils.head)
        .flatMap(Parsers.isString)
        .do((msg) => {
          throw new Error(msg);
        });
      throw new Error(
        `Could not generate a PDF preview for .${fileExtension} documents.`,
      );
    },
    [openPdfPreview],
  );

  const setDetails = async (details: AsposePreviewDetails) => {
    setLoading(true);
    try {
      await openConvertedFile(details);
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unknown error");
      addAlert(
        mkAlert({
          variant: "error",
          title: "Could not generate preview.",
          message: e.message ?? "Unknown reason",
        })
      );
    } finally {
      setLoading(false);
    }
  };

  const setFile = async (file: GalleryFile) => {
    await setDetails({
      documentId: Number(idToString(file.id).elseThrow()),
      fileExtension: file.extension ?? "",
    });
  };

  return (
    <>
      <AsposePreviewContext.Provider value={{ setFile, setDetails, loading }}>
        {children}
      </AsposePreviewContext.Provider>
    </>
  );
}

/**
 * Check if the file is supported by Aspose based on its extension.
 */
export function supportedAsposeFile(file: GalleryFile): Result<null> {
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

  if (!file.id) return Result.Error([new Error("Aspose requires a file ID")]);
  if (!file.extension)
    return Result.Error([new Error("Aspose requires a file extension")]);
  if (!ASPOSE_EXTENSIONS.includes(file.extension))
    return Result.Error([
      new Error("Aspose does not support the extension of the file"),
    ]);
  return Result.Ok(null);
}
