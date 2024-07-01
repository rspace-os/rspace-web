//@flow

import React from "react";
import axios from "axios";
import * as ArrayUtils from "../../util/ArrayUtils";
import * as Parsers from "../../util/parsers";
import Result from "../../util/result";
import { type GalleryFile, idToString, type Id } from "./useGalleryListing";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";

export function useGalleryActions(): {|
  uploadFiles: (
    $ReadOnlyArray<GalleryFile>,
    Id,
    $ReadOnlyArray<File>
  ) => Promise<void>,
  createFolder: ($ReadOnlyArray<GalleryFile>, Id, string) => Promise<void>,
  moveFilesWithIds: ($ReadOnlyArray<number>) => {|
    to: ({|
      destination: {| key: "root" |} | {| key: "folder", folder: GalleryFile |},
      section: string,
    |}) => Promise<void>,
  |},
|} {
  const { addAlert, removeAlert } = React.useContext(AlertContext);

  async function uploadFiles(
    path: $ReadOnlyArray<GalleryFile>,
    parentId: Id,
    files: $ReadOnlyArray<File>
  ) {
    const uploadingAlert = mkAlert({
      message: "Uploading...",
      variant: "notice",
      isInfinite: true,
    });
    addAlert(uploadingAlert);

    const targetFolderId = ArrayUtils.last(path)
      .map(({ id }) => idToString(id))
      .orElse(idToString(parentId));
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

  async function createFolder(
    path: $ReadOnlyArray<GalleryFile>,
    parentId: Id,
    name: string
  ) {
    const parentFolderId = ArrayUtils.last(path)
      .map(({ id }) => idToString(id))
      .orElse(idToString(parentId));
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

  function moveFilesWithIds(fileIds: $ReadOnlyArray<number>) {
    return {
      to: async ({
        destination,
        section,
      }: {|
        destination:
          | {| key: "root" |}
          | {| key: "folder", folder: GalleryFile |},
        section: string,
      |}) => {
        const target = `/${[
          section,
          ...(destination.key === "folder"
            ? destination.folder.path.map(({ name }) => name)
            : []),
          ...(destination.key === "root" ? [] : [destination.folder.name]),
        ].join("/")}/`;
        const formData = new FormData();
        formData.append("target", target);
        fileIds.forEach((fileId) => {
          formData.append("filesId[]", `${fileId}`);
        });
        formData.append("mediaType", section);
        try {
          const data = await axios.post<FormData, mixed>(
            "gallery/ajax/moveGalleriesElements",
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
                  title: `Failed to move item.`,
                  message: exceptionMessage,
                  variant: "error",
                })
              )
              .orElse(
                mkAlert({
                  message: `Successfully moved item${
                    fileIds.length > 0 ? "s" : ""
                  }.`,
                  variant: "success",
                })
              )
          );
        } catch (e) {
          addAlert(
            mkAlert({
              variant: "error",
              title: `Failed to move item${fileIds.length > 0 ? "s" : ""}.`,
              message: e.message,
            })
          );
          throw e;
        }
      },
    };
  }

  return { uploadFiles, createFolder, moveFilesWithIds };
}
