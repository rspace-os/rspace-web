//@flow

import React from "react";
import axios from "axios";
import * as ArrayUtils from "../../util/ArrayUtils";
import * as Parsers from "../../util/parsers";
import RsSet from "../../util/set";
import Result from "../../util/result";
import {
  type GalleryFile,
  Description,
  idToString,
  type Id,
} from "./useGalleryListing";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";

const ONE_MINUTE_IN_MS = 60 * 60 * 1000;

export opaque type Destination =
  | {| key: "root" |}
  | {| key: "folder", folder: GalleryFile |};

export function rootDestination(): Destination {
  return { key: "root" };
}

export function folderDestination(folder: GalleryFile): Destination {
  return { key: "folder", folder };
}

export function useGalleryActions(): {|
  uploadFiles: (
    $ReadOnlyArray<GalleryFile>,
    Id,
    $ReadOnlyArray<File>
  ) => Promise<void>,
  createFolder: ($ReadOnlyArray<GalleryFile>, Id, string) => Promise<void>,
  moveFiles: (Set<GalleryFile>) => {|
    to: ({|
      destination: Destination,
      section: string,
    |}) => Promise<void>,
    toDestinationWithFolder: (string, GalleryFile) => Promise<void>,
  |},
  deleteFiles: (RsSet<GalleryFile>) => Promise<void>,
  duplicateFiles: (RsSet<GalleryFile>) => Promise<void>,
  rename: (GalleryFile, string) => Promise<void>,

  /**
   * The contents of `file` is replaced with `newFile`. The filename is also
   * replaced and the version number incremented.
   *
   * @arg folderId The Id of the folder that `file` currently resides in.
   * @arg file     The file whose contents are being updated.
   * @arg newFile  The contents that `file` is being updated to.
   */
  uploadNewVersion: (
    folderId: Id,
    file: GalleryFile,
    newFile: File
  ) => Promise<void>,
  changeDescription: (GalleryFile, Description) => Promise<void>,
|} {
  const { addAlert, removeAlert } = React.useContext(AlertContext);

  /*
   * We create these axios objects because the global axios object is polluted
   * in Inventory to make the network calls relative to /inventory
   */
  const galleryApi = axios.create({
    baseURL: "/gallery/ajax",
    timeout: ONE_MINUTE_IN_MS,
  });
  const structuredDocumentApi = axios.create({
    baseURL: "/workspace/editor/structuredDocument/ajax",
    timeout: ONE_MINUTE_IN_MS,
  });

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
          return galleryApi.post<FormData, mixed>("uploadFile", formData, {
            headers: {
              "Content-Type": "multipart/form-data",
            },
          });
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
      const data = await galleryApi.post<FormData, mixed>(
        "createFolder",
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

  function moveFiles(files: Set<GalleryFile>): {|
    to: ({|
      destination: Destination,
      section: string,
    |}) => Promise<void>,
    toDestinationWithFolder: (string, GalleryFile) => Promise<void>,
  |} {
    return {
      to: async ({
        destination,
        section,
      }: {|
        destination:
          | {| key: "root" |}
          | {| key: "folder", folder: GalleryFile |},
        section: string,
      |}): Promise<void> => {
        if (destination.key === "folder")
          return moveFiles(files).toDestinationWithFolder(
            section,
            destination.folder
          );
        try {
          const formData = new FormData();
          formData.append(
            "target",
            destination.key === "root" ? "0" : destination.folder.id
          );
          for (const file of files)
            formData.append("filesId[]", idToString(file.id));
          formData.append("mediaType", section);
          const data = await galleryApi.post<FormData, mixed>(
            "moveGalleriesElements",
            formData,
            {
              headers: {
                "Content-Type": "multipart/form-data",
              },
            }
          );
          addAlert(
            Parsers.objectPath(["data", "exceptionMessage"], data)
              .orElseTry(() =>
                Parsers.objectPath(["data", "error", "errorMessages"], data)
                  .flatMap(Parsers.isArray)
                  .flatMap(ArrayUtils.head)
              )
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
      toDestinationWithFolder: async (
        section: string,
        destinationFolder: GalleryFile
      ): Promise<void> => {
        if (destinationFolder.isSnippetFolder) {
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
        try {
          const formData = new FormData();
          formData.append("target", idToString(destinationFolder.id));
          for (const file of files)
            formData.append("filesId[]", idToString(file.id));
          formData.append("mediaType", section);
          const data = await galleryApi.post<FormData, mixed>(
            "moveGalleriesElements",
            formData,
            {
              headers: {
                "Content-Type": "multipart/form-data",
              },
            }
          );
          addAlert(
            Parsers.objectPath(["data", "exceptionMessage"], data)
              .orElseTry(() =>
                Parsers.objectPath(["data", "error", "errorMessages"], data)
                  .flatMap(Parsers.isArray)
                  .flatMap(ArrayUtils.head)
              )
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

  async function deleteFiles(files: RsSet<GalleryFile>) {
    if (files.some((f) => f.isSystemFolder)) return;
    const formData = new FormData();
    for (const file of files)
      formData.append("idsToDelete[]", idToString(file.id));
    try {
      const data = await galleryApi.post<FormData, mixed>(
        "deleteElementFromGallery",
        formData,
        {
          headers: {
            "content-type": "multipart/form-data",
          },
        }
      );
      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .orElseTry(() =>
            Parsers.objectPath(["data", "error", "errorMessages"], data)
              .flatMap(Parsers.isArray)
              .flatMap(ArrayUtils.head)
          )
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

  async function duplicateFiles(files: RsSet<GalleryFile>) {
    if (files.some((f) => f.isSystemFolder)) return;
    const formData = new FormData();
    for (const file of files) {
      formData.append("idToCopy[]", idToString(file.id));
      formData.append(
        "newName[]",
        file.transformFilename((name) => name + "_copy")
      );
    }
    try {
      const data = await galleryApi.post<FormData, mixed>(
        "copyGalleries",
        formData,
        {
          headers: {
            "content-type": "multipart/form-data",
          },
        }
      );
      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .orElseTry(() =>
            Parsers.objectPath(["data", "error", "errorMessages"], data)
              .flatMap(Parsers.isArray)
              .flatMap(ArrayUtils.head)
          )
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
    if (file.isSystemFolder) return;
    const formData = new FormData();
    formData.append("recordId", idToString(file.id));
    formData.append(
      "newName",
      file.transformFilename(() => newName)
    );
    try {
      const data = await structuredDocumentApi.post<FormData, mixed>(
        "rename",
        formData,
        {
          headers: {
            "content-type": "multipart/form-data",
          },
        }
      );

      Parsers.objectPath(["data", "exceptionMessage"], data)
        .flatMap(Parsers.isString)
        .do((exceptionMessage) => {
          throw new Error(exceptionMessage);
        });

      addAlert(
        mkAlert({
          message: `Successfully renamed item.`,
          variant: "success",
        })
      );

      file.setName(file.transformFilename(() => newName));
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

  async function uploadNewVersion(
    folderId: Id,
    file: GalleryFile,
    newFile: File
  ) {
    if (file.isSystemFolder) return;
    const formData = new FormData();
    formData.append("selectedMediaId", idToString(file.id));
    formData.append("xfile", newFile);
    formData.append("targetFolderId", idToString(folderId));
    try {
      const { data } = await galleryApi.post<FormData, mixed>(
        "uploadFile",
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
          .orElseTry(() =>
            Parsers.objectPath(["exceptionMessage"], data).flatMap(
              Parsers.isString
            )
          )
          .map((exceptionMessage) =>
            mkAlert({
              title: `Failed to upload new version.`,
              message: exceptionMessage,
              variant: "error",
            })
          )
          .orElse(
            mkAlert({
              message: `Successfully uploaded new version.`,
              variant: "success",
            })
          )
      );
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to upload new version.`,
          message: e.message,
        })
      );
      throw e;
    }
  }

  async function changeDescription(
    file: GalleryFile,
    newDescription: Description
  ) {
    const formData = new FormData();
    formData.append("recordId", idToString(file.id));
    formData.append(
      "description",
      newDescription.match({
        missing: () => {
          throw new Error("Description is missing");
        },
        empty: () => "",
        present: (d) => d,
      })
    );
    try {
      const data = await structuredDocumentApi.post<FormData, mixed>(
        "description",
        formData,
        {
          headers: {
            "content-type": "multipart/form-data",
          },
        }
      );

      Parsers.objectPath(["data", "exceptionMessage"], data)
        .flatMap(Parsers.isString)
        .do((exceptionMessage) => {
          throw new Error(exceptionMessage);
        });

      addAlert(
        mkAlert({
          message: `Successfully updated description.`,
          variant: "success",
        })
      );

      file.setDescription(newDescription);
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to update description.`,
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
    uploadNewVersion,
    changeDescription,
  };
}
