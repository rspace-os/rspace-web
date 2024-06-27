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
  moveFiles: (Set<GalleryFile>) => {|
    to: ({|
      destination: {| key: "root" |} | {| key: "folder", folder: GalleryFile |},
      section: string,
    |}) => Promise<void>,
  |},
  deleteFiles: (Set<GalleryFile>) => Promise<void>,
  duplicateFiles: (Set<GalleryFile>) => Promise<void>,
  rename: (GalleryFile, string) => Promise<void>,
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

  function moveFiles(files: Set<GalleryFile>) {
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
        if (
          destination.key === "folder" &&
          destination.folder.isSnippetFolder
        ) {
          addAlert(
            mkAlert({
              variant: "error",
              title: "Cannot drag files into SNIPPETS folders.",
              message:
                "Share them and they will automatically appear in these folders.",
            })
          );
          return;
        }
        const target =
          destination.key === "root"
            ? `/${section}/`
            : `/${[
                section,
                ...destination.folder.path.map(({ name }) => name),
                ...[destination.folder.name],
              ].join("/")}/`;
        const formData = new FormData();
        formData.append("target", target);
        for (const file of files)
          formData.append("filesId[]", idToString(file.id));
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
                    files.size > 0 ? "s" : ""
                  }.`,
                  variant: "success",
                })
              )
          );
        } catch (e) {
          addAlert(
            mkAlert({
              variant: "error",
              title: `Failed to move item${files.size > 0 ? "s" : ""}.`,
              message: e.message,
            })
          );
          throw e;
        }
      },
    };
  }

  async function deleteFiles(files: Set<GalleryFile>) {
    const formData = new FormData();
    for (const file of files)
      formData.append("idsToDelete[]", idToString(file.id));
    try {
      const data = await axios.post<FormData, mixed>(
        "gallery/ajax/deleteElementFromGallery",
        formData,
        {
          headers: {
            "content-type": "multipart/form-data",
          },
        }
      );
      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .flatMap(Parsers.isString)
          .map((exceptionMessage) =>
            mkAlert({
              title: `Failed to delete item.`,
              message: exceptionMessage,
              variant: "error",
            })
          )
          .orElse(
            mkAlert({
              message: `Successfully deleted item${files.size > 0 ? "s" : ""}.`,
              variant: "success",
            })
          )
      );
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to delete item${files.size > 0 ? "s" : ""}.`,
          message: e.message,
        })
      );
      throw e;
    }
  }

  async function duplicateFiles(files: Set<GalleryFile>) {
    const formData = new FormData();
    for (const file of files) {
      formData.append("idToCopy[]", idToString(file.id));
      formData.append(
        "newName[]",
        file.transformFilename((name) => name + "_copy")
      );
    }
    try {
      const data = await axios.post<FormData, mixed>(
        "gallery/ajax/copyGalleries",
        formData,
        {
          headers: {
            "content-type": "multipart/form-data",
          },
        }
      );
      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .flatMap(Parsers.isString)
          .map((exceptionMessage) =>
            mkAlert({
              title: `Failed to duplicate item.`,
              message: exceptionMessage,
              variant: "error",
            })
          )
          .orElse(
            mkAlert({
              message: `Successfully duplicated item${
                files.size > 0 ? "s" : ""
              }.`,
              variant: "success",
            })
          )
      );
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to duplicate item${files.size > 0 ? "s" : ""}.`,
          message: e.message,
        })
      );
      throw e;
    }
  }

  async function rename(file: GalleryFile, newName: string) {
    const formData = new FormData();
    formData.append("recordId", idToString(file.id));
    formData.append(
      "newName",
      file.transformFilename(() => newName)
    );
    try {
      const data = await axios.post<FormData, mixed>(
        "workspace/editor/structuredDocument/ajax/rename",
        formData,
        {
          headers: {
            "content-type": "multipart/form-data",
          },
        }
      );
      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .flatMap(Parsers.isString)
          .map((exceptionMessage) =>
            mkAlert({
              title: `Failed to rename item.`,
              message: exceptionMessage,
              variant: "error",
            })
          )
          .orElse(
            mkAlert({
              message: `Successfully renamed item.`,
              variant: "success",
            })
          )
      );
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to rename item.`,
          message: e.message,
        })
      );
      throw e;
    }
  }

  return {
    uploadFiles,
    createFolder,
    moveFiles,
    deleteFiles,
    duplicateFiles,
    rename,
  };
}
