import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as Parsers from "../../../util/parsers";
import Result from "../../../util/result";
import { type GalleryFile, idToString } from "../useGalleryListing";
import { usePdfPreview } from "./CallablePdfPreview";

const firstResult = <T,>(items: ReadonlyArray<T>): Result<T> =>
  Result.fromNullable(items.at(0), new Error("Array is empty"));

/*
 * When document conversion is configured, users can preview common document
 * formats as PDFs through CallablePdfPreview.
 *
 * Much like how `window.open` allows any JS code on the page to trigger the
 * opening of in a new window/tab with a specified URL, this module provides a
 * mechanism to allow any JS code to trigger the opening of a dialog by
 * providing a GalleryFile that can be converted to a PDF.
 *
 * If the conversion fails or there is an error for any other reason with the
 * network call then an error toast is shown.
 */

export type DocumentPreviewDetails = {
  documentId: number;
  fileExtension: string;
  revisionId?: number | null;
  publicView?: boolean;
};

const DocumentPreviewContext = React.createContext({
  setFile: (_file: GalleryFile) => Promise.resolve(),
  setDetails: (_details: DocumentPreviewDetails) => Promise.resolve(),
  loading: false,
});

/**
 * Display a converted document in the PDF preview dialog.
 */
export function useDocumentPreview(): {
  /**
   * Preview the document at this GalleryFile.
   */
  openDocumentPreview: (file: GalleryFile) => Promise<void>;

  /**
   * Preview a document by using its ids/extension rather than a GalleryFile.
   */
  openDocumentPreviewFromDetails: (details: DocumentPreviewDetails) => Promise<void>;

  loading: boolean;
} {
  const {
    setFile: openDocumentPreview,
    setDetails: openDocumentPreviewFromDetails,
    loading,
  } = React.useContext(DocumentPreviewContext);
  return {
    openDocumentPreview,
    openDocumentPreviewFromDetails,
    loading,
  };
}

/**
 * This component provides a mechanism for any other component that is its
 * descendent to trigger the previewing of a document by passing the GalleryFile
 * to `useDocumentPreview`'s `openDocumentPreview` function.
 *
 * Relies on being inside of an AlertContext and the context created by
 * CallablePdfPreview
 */
export function CallableDocumentPreview({ children }: { children: React.ReactNode }): React.ReactNode {
  const [loading, setLoading] = React.useState(false);
  const { openPdfPreview } = usePdfPreview();
  const { addAlert } = React.useContext(AlertContext);
  const { t } = useTranslation("gallery");

  const openConvertedFile = React.useCallback(
    async ({ documentId, fileExtension, revisionId = null, publicView = false }: DocumentPreviewDetails) => {
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
          `${publicView ? "/public/publicView" : ""}/Streamfile/direct/${documentId}?fileName=${fileName}`,
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
        .flatMap(firstResult)
        .flatMap(Parsers.isString)
        .do((msg) => {
          throw new Error(msg);
        });
      throw new Error(t("callableDocumentPreview.generatePdfError", { fileExtension }));
    },
    [openPdfPreview, t],
  );

  const setDetails = async (details: DocumentPreviewDetails) => {
    setLoading(true);
    try {
      await openConvertedFile(details);
    } catch (e) {
      if (!(e instanceof Error)) throw new Error(t("errors.unknownError"));
      addAlert(
        mkAlert({
          variant: "error",
          title: t("callableDocumentPreview.previewError"),
          message: e.message ?? t("errors.unknownReason"),
        }),
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
    <DocumentPreviewContext.Provider value={{ setFile, setDetails, loading }}>
      {children}
    </DocumentPreviewContext.Provider>
  );
}

/**
 * Check whether the configured preview converter supports the file extension.
 */
export function supportedPreviewFile(file: GalleryFile): Result<null> {
  const PREVIEW_EXTENSIONS = [
    "csv",
    "doc",
    "docx",
    "md",
    "odt",
    "rtf",
    "txt",
    "xls",
    "xlsx",
    "ods",
    "pdf",
    "ppt",
    "pptx",
    "odp",
  ];

  if (!file.id) return Result.Error([new Error("Document preview requires a file ID")]);
  if (!file.extension) return Result.Error([new Error("Document preview requires a file extension")]);
  if (!PREVIEW_EXTENSIONS.includes(file.extension))
    return Result.Error([new Error("Document preview does not support this file extension")]);
  return Result.Ok(null);
}
