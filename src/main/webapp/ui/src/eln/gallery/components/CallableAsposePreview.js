//@flow

import React, { type Node } from "react";
import { type GalleryFile, idToString } from "../useGalleryListing";
import { usePdfPreview } from "./CallablePdfPreview";
import axios from "axios";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as Parsers from "../../../util/parsers";

const AsposePreviewContext = React.createContext((_file: GalleryFile) => {});

export function useAsposePreview(): {|
  openAsposePreview: (GalleryFile) => void,
|} {
  const openAsposePreview = React.useContext(AsposePreviewContext);
  return {
    openAsposePreview,
  };
}

/**
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
