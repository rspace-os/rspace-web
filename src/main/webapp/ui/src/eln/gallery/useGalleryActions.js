//@flow

import React from "react";
import axios from "axios";
import * as ArrayUtils from "../../util/ArrayUtils";
import * as Parsers from "../../util/parsers";
import Result from "../../util/result";
import { type GalleryFile, idToString } from "./useGalleryListing";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";

export default function useGalleryActions({
  path,
  parentId,
}: {|
  path: $ReadOnlyArray<GalleryFile>,
  parentId: number,
|}): {|
  uploadFiles: ($ReadOnlyArray<File>) => Promise<void>,
  createFolder: (string) => Promise<void>,
  moveFile: ({|
    target: string,
    fileId: number,
    section: string,
  |}) => Promise<void>,
|} {
  const { addAlert, removeAlert } = React.useContext(AlertContext);

  async function uploadFiles(files: $ReadOnlyArray<File>) {
    const uploadingAlert = mkAlert({
      message: "Uploading...",
      variant: "notice",
      isInfinite: true,
    });
    addAlert(uploadingAlert);

    const targetFolderId = ArrayUtils.getAt(0, path)
      .map(({ id }) => idToString(id))
      .orElse(`${parentId}`);
    try {
      const data = await Promise.all(
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

      addAlert(
        Result.any(
          ...data.map((d) =>
            Parsers.objectPath(["data", "exceptionMessage"], d).flatMap(
              Parsers.isString
            )
          )
        )
          .map((exceptionMessages) =>
            mkAlert({
              message: `Failed to upload file${files.length === 1 ? "" : "s"}.`,
              variant: "error",
              details: exceptionMessages.map((m) => ({
                title: m,
                variant: "error",
              })),
            })
          )
          .orElse(
            mkAlert({
              message: `Successfully uploaded file${
                files.length === 1 ? "" : "s"
              }.`,
              variant: "success",
            })
          )
      );
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to upload file${files.length === 1 ? "" : "s"}.`,
          message: e.message,
        })
      );
      throw e;
    } finally {
      removeAlert(uploadingAlert);
    }
  }

  async function createFolder(name: string) {
    const parentFolderId = ArrayUtils.getAt(0, path)
      .map(({ id }) => idToString(id))
      .orElse(`${parentId}`);
    try {
      const formData = new FormData();
      formData.append("folderName", name);
      formData.append("parentId", parentFolderId);
      formData.append("isMedia", "true");
      const data = await axios.post<FormData, mixed>(
        "gallery/ajax/createFolder",
        formData,
        {
          headers: {
            "Content-Type": "multipart/form-data",
          },
        }
      );

      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .flatMap(Parsers.isString)
          .map((exceptionMessage) =>
            mkAlert({
              title: `Failed to create new folder.`,
              message: exceptionMessage,
              variant: "error",
            })
          )
          .orElse(
            mkAlert({
              message: `Successfully created new folder.`,
              variant: "success",
            })
          )
      );
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to create new folder.`,
          message: e.message,
        })
      );
      throw e;
    }
  }

  async function moveFile({
    target,
    fileId,
    section,
  }: {|
    target: string,
    fileId: number,
    section: string,
  |}) {
    const formData = new FormData();
    formData.append("target", target);
    formData.append("filesId[]", `${fileId}`);
    formData.append("mediaType", section);
    await axios.post<FormData, mixed>(
      "gallery/ajax/moveGalleriesElements",
      formData,
      {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      }
    );
    // TODO handle errors
  }

  return { uploadFiles, createFolder, moveFile };
}
