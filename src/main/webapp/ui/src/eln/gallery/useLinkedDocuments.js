//@flow

import React from "react";
import axios from "axios";
import { type GalleryFile, idToString } from "./useGalleryListing";
import Result from "../../util/result";
import * as Parsers from "../../util/parsers";
import { type LinkableRecord } from "../../stores/definitions/LinkableRecord";

/**
 * An ELN document, to the extent that the Gallery needs to know to provide a
 * table of back-references.
 */
export type Document = {|
  id: number,
  globalId: string,
  name: string,

  permalinkHref: string,

  linkableRecord: LinkableRecord,
|};

class LinkableDocument implements LinkableRecord {
  id: ?number;
  globalId: ?string;
  name: string;

  constructor({
    id,
    globalId,
    name,
  }: {|
    id: number,
    globalId: string,
    name: string,
  |}) {
    this.id = id;
    this.globalId = globalId;
    this.name = name;
  }

  get recordTypeLabel(): string {
    return "Document";
  }

  get iconName(): string {
    return "document";
  }

  get permalinkURL(): string {
    if (!this.globalId) throw new Error("Impossible");
    return `/globalId/${this.globalId}`;
  }
}

/**
 * Given a GalleryFile, get all of the ELN documents that reference it.
 */
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
        .flatMap((docs) =>
          Result.all(
            ...docs.map((doc) =>
              Parsers.isObject(doc)
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

                      permalinkHref: `/globalId/${globalId}`,

                      linkableRecord: new LinkableDocument({
                        id,
                        globalId,
                        name,
                      }),
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
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - getLinkedDocuments will not meaningfully change
     */
  }, [file]);

  return {
    documents,
    loading,
    errorMessage,
  };
}
