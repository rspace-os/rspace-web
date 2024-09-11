//@flow

import React, { type Node } from "react";
import { type GalleryFile, idToString } from "../useGalleryListing";
import { usePdfPreview } from "./CallablePdfPreview";
import axios from "axios";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as Parsers from "../../../util/parsers";

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

const AsposePreviewContext = React.createContext((_file: GalleryFile) => {});

/**
 * Use the callable aspose preview component to display a document in a dialog
 * as a PDF.
 */
export function useAsposePreview(): {|
  /**
   * Preview the document at this GalleryFile.
   */
  openAsposePreview: (GalleryFile) => void,
|} {
  const openAsposePreview = React.useContext(AsposePreviewContext);
  return {
    openAsposePreview,
  };
}

/**
 * This components provides a mechanism for any other component that is its
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
}: {|
  children: Node,
|}): Node {
  const [file, setFile] = React.useState<null | GalleryFile>(null);
  const { openPdfPreview } = usePdfPreview();
  const { addAlert } = React.useContext(AlertContext);

  React.useEffect(() => {
    if (file) {
      void (async () => {
        try {
          const { data } = await axios.get<mixed>(
            "/Streamfile/ajax/convert/" +
              idToString(file.id) +
              "?outputFormat=pdf"
          );
          const fileName = Parsers.isObject(data)
            .flatMap(Parsers.isNotNull)
            .flatMap(Parsers.getValueWithKey("data"))
            .flatMap(Parsers.isString)
            .orElse(null);
          if (fileName) {
            openPdfPreview(
              "/Streamfile/direct/" +
                idToString(file.id) +
                "?fileName=" +
                fileName
            );
          } else {
            Parsers.isObject(data)
              .flatMap(Parsers.isNotNull)
              .flatMap(Parsers.getValueWithKey("exceptionMessage"))
              .flatMap(Parsers.isString)
              .do((msg) => {
                throw new Error(msg);
              });
          }
        } catch (e) {
          addAlert(
            mkAlert({
              variant: "error",
              title: "Could not generate preview.",
              message: e.message ?? "Unknown reason",
            })
          );
        }
      })();
    }
  }, [file]);

  return (
    <>
      <AsposePreviewContext.Provider value={setFile}>
        {children}
      </AsposePreviewContext.Provider>
    </>
  );
}
