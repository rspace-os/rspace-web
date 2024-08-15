//@flow

import React from "react";
import { type GalleryFile } from "./useGalleryListing";

export type Document = {|
  name: string,
|};

export default function useLinkedDocuments(file: GalleryFile): {|
  documents: $ReadOnlyArray<Document>,
  loading: boolean,
|} {
  const [loading, setLoading] = React.useState(true);
  const [documents, setDocuments] = React.useState<$ReadOnlyArray<Document>>(
    []
  );

  return {
    documents,
    loading,
  };
}
