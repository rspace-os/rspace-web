//@flow

import React from "react";
import axios from "axios";
import { type GalleryFile, idToString } from "./useGalleryListing";
import Result from "../../util/result";
import * as Parsers from "../../util/parsers";

export type Document = {|
  id: number,
  globalId: string,
  name: string,
|};

export default function useLinkedDocuments(file: GalleryFile): {|
  documents: $ReadOnlyArray<Document>,
  loading: boolean,
  errorMessage: string | null,
|} {
  const [loading, setLoading] = React.useState(true);
  const [documents, setDocuments] = React.useState<$ReadOnlyArray<Document>>(
    []
  );
  const [errorMessage, setErrorMessage] = React.useState<string | null>(null);

  async function getLinkedDocuments(): Promise<void> {
    setDocuments([]);
    setLoading(true);
    setErrorMessage(null);
    try {
      const { data } = await axios.get<mixed>(
        `/gallery/ajax/getLinkedDocuments/${idToString(file.id)}`
      );

      Parsers.objectPath(["data"], data)
        .flatMap(Parsers.isArray)
        .flatMap((x) =>
          Result.all(
            ...x.map((y) =>
              Parsers.isObject(y)
                .flatMap(Parsers.isNotNull)
                .map((obj) => {
                  try {
                    const id = Parsers.getValueWithKey("id")(obj)
                      .flatMap(Parsers.isNumber)
                      .elseThrow();

                    const globalId = Parsers.getValueWithKey("oid")(obj)
                      .flatMap(Parsers.isObject)
                      .flatMap(Parsers.isNotNull)
                      .flatMap(Parsers.getValueWithKey("idString"))
                      .flatMap(Parsers.isString)
                      .elseThrow();

                    const name = Parsers.getValueWithKey("name")(obj)
                      .flatMap(Parsers.isString)
                      .elseThrow();

                    return {
                      id,
                      globalId,
                      name,
                    };
                  } catch (e) {
                    setErrorMessage("Error loading linked documents.");
                    throw e;
                  }
                })
            )
          )
        )
        .do((docs) => {
          setDocuments(docs);
        });
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  React.useEffect(() => {
    void getLinkedDocuments();
  }, [file]);

  return {
    documents,
    loading,
    errorMessage,
  };
}
