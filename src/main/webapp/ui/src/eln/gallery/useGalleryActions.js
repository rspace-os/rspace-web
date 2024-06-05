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

    const targetFolderId = ArrayUtils.getAt(0, path)
      .map(({ id }) => `${id}`)
      .orElse(`${parentId}`);
    try {
      await Promise.all(
        files.map((file) => {
          const formData = new FormData();
          formData.append("xfile", file);
          formData.append("targetFolderId", targetFolderId);
          return axios.post<FormData, mixed>(
            "gallery/ajax/uploadFile",
            formData,
            {
              headers: {
                "Content-Type": "multipart/form-data",
              },
            }
          );
        })
      );
    } finally {
      removeAlert(uploadingAlert);
    }
    // TODO if error, show error alert
    // TODO if success, show success alert
  }

  return { uploadFiles };
}
