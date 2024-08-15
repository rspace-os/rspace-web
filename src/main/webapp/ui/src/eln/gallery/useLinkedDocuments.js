//@flow

import React from "react";
import axios from "axios";
import { type GalleryFile, idToString } from "./useGalleryListing";
import Result from "../../util/result";
import * as Parsers from "../../util/parsers";

export type Document = {|
  name: string,
  id: number,
|};

export default function useLinkedDocuments(file: GalleryFile): {|
  documents: $ReadOnlyArray<Document>,
  loading: boolean,
|} {
  const [loading, setLoading] = React.useState(true);
  const [documents, setDocuments] = React.useState<$ReadOnlyArray<Document>>(
    []
  );

  async function getLinkedDocuments(): Promise<void> {
    setDocuments([]);
    setLoading(true);
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
                  const id = Parsers.getValueWithKey("id")(obj)
                    .flatMap(Parsers.isNumber)
                    .elseThrow();

                  const name = Parsers.getValueWithKey("name")(obj)
                    .flatMap(Parsers.isString)
                    .elseThrow();

                  return {
                    id,
                    name,
                  };
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
  };
}
