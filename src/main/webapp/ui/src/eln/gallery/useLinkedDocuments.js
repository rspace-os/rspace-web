//@flow

import { type GalleryFile } from "./useGalleryListing";

export default function useLinkedDocuments(file: GalleryFile): {|
  documents: $ReadOnlyArray<{| name: string |}>,
|} {
  return {
    documents: [],
  };
}
