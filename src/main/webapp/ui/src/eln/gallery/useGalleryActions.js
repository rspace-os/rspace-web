//@flow

import axios from "axios";
import * as FetchingData from "../../util/fetchingData";
import * as ArrayUtils from "../../util/ArrayUtils";
import { type GalleryFile } from "./useGalleryListing";

export default function useGalleryActions({
  path,
  parentId,
}: {|
  path: $ReadOnlyArray<GalleryFile>,
  parentId: number,
|}): {|
  uploadFiles: ($ReadOnlyArray<File>) => Promise<void>,
|} {
  async function uploadFiles(files: $ReadOnlyArray<File>) {
    console.debug(files, path, parentId);

    // TODO open "uploading" alert
    const formData = new FormData();
    formData.append("xfile", files[0]);
    formData.append(
      "targetFolderId",
      ArrayUtils.getAt(0, path)
        .map(({ id }) => `${id}`)
        .orElse(`${parentId}`)
    );
    // TODO upload each file in parallel
    await axios.post<FormData, mixed>("gallery/ajax/uploadFile", formData, {
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
