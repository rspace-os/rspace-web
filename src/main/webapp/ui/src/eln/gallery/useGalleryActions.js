//@flow

import axios from "axios";
import * as FetchingData from "../../util/fetchingData";

export default function useGalleryActions(): {|
  uploadFiles: ($ReadOnlyArray<File>) => Promise<void>,
|} {
  function uploadFiles(files: $ReadOnlyArray<File>) {
    console.debug(files);

    // TODO open "uploading" alert
    const formData = new FormData();
    formData.append("xfile", files[0]);
    // TODO pass folderId in as a parameter
    formData.append("targetFolderId", 8);
    // TODO upload each file in parallel
    return axios.post("gallery/ajax/uploadFile", formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
    // TODO close "uploading" alert
    // TODO if error, show error alert
    // TODO if success, show success alert
  }

  return { uploadFiles };
}
