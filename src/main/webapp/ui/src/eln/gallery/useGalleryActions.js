//@flow

import axios from "axios";
import * as FetchingData from "../../util/fetchingData";

export default function useGalleryActions(): {|
  uploadFiles: ($ReadOnlyArray<File>) => void,
|} {
  function uploadFiles(files: $ReadOnlyArray<File>) {
    console.debug(files);
  }

  return { uploadFiles };
}
