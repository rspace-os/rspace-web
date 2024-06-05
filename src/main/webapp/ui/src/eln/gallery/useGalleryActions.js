//@flow

import React from "react";
import axios from "axios";
import * as FetchingData from "../../util/fetchingData";
import * as ArrayUtils from "../../util/ArrayUtils";
import { type GalleryFile } from "./useGalleryListing";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";

export default function useGalleryActions({
  path,
  parentId,
}: {|
  path: $ReadOnlyArray<GalleryFile>,
  parentId: number,
|}): {|
  uploadFiles: ($ReadOnlyArray<File>) => Promise<void>,
|} {
  const { addAlert, removeAlert } = React.useContext(AlertContext);

  async function uploadFiles(files: $ReadOnlyArray<File>) {
    console.debug(files, path, parentId);

    const uploadingAlert = mkAlert({
      message: "Uploading...",
      variant: "notice",
      isInfinite: true,
    });
    addAlert(uploadingAlert);

    const formData = new FormData();
    formData.append("xfile", files[0]);
    formData.append(
      "targetFolderId",
      ArrayUtils.getAt(0, path)
        .map(({ id }) => `${id}`)
        .orElse(`${parentId}`)
    );
    // TODO upload each file in parallel
    try {
      await axios.post<FormData, mixed>("gallery/ajax/uploadFile", formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      });
    } finally {
      removeAlert(uploadingAlert);
    }
    // TODO if error, show error alert
    // TODO if success, show success alert
  }

  return { uploadFiles };
}
