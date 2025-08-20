import React from "react";
import axios from "@/common/axios";
import * as ArrayUtils from "../../util/ArrayUtils";
import * as Parsers from "../../util/parsers";
import RsSet from "../../util/set";
import Result from "../../util/result";
import {
  type GalleryFile,
  LocalGalleryFile,
  Filestore,
  type Description,
  idToString,
  type Id,
} from "./useGalleryListing";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import useOauthToken from "../../hooks/auth/useOauthToken";
import { partitionAllSettled } from "../../util/Util";
import { type GallerySection } from "./common";
import AnalyticsContext from "../../stores/contexts/Analytics";

const ONE_MINUTE_IN_MS = 60 * 60 * 1000;

/**
 * The destination of a move operation.
 */
export type Destination =
  | { key: "root" } // the gallery section depends on the type of the file being moved
  | { key: "folder"; folder: GalleryFile };

/**
 * Constructor function for specifying that the destination of a move operation
 * should be the root of the gallery section based on the type of the file
 * being moved.
 */
export function rootDestination(): Destination {
  return { key: "root" };
}

/**
 * Constructor function for specifying that the destination of a move operation
 * should be a specific folder.
 */
export function folderDestination(folder: GalleryFile): Destination {
  return { key: "folder", folder };
}

/**
 * Hook that exposes several functions for uploading files, creating new
 * folders, and performing operations on existing files and folders.
 */
export function useGalleryActions(): {
  /**
   * For uploading new files.
   *
   * @arg parentId The id of the folder or gallery section that the file should
   *               be uploaded to. The server will ignore this if the type of
   *               the files do not match the specified gallery section or the
   *               gallery section at the root of the specified folder, placing
   *               the files at the root of the corresponding gallery sections
   *               instead.
   * @arg files    The File objects being uploaded.
   */
  uploadFiles: (
    parentId: Id,
    files: ReadonlyArray<File>,
    options?: { originalImageId: Id },
  ) => Promise<void>;

  /**
   * For creating new folders.
   *
   * @arg parentId The id of the folder or gallery section that the new folder
   *               will be created within.
   * @arg name     The name of the new folder.
   */
  createFolder: (parentId: Id, name: string) => Promise<void>;

  /**
   * Move files to a different folder.
   *
   * @arg section     The relevant gallery section for the files being operated
   *                  on. Whilst it would seem that this would only be required
   *                  if the destination is the root of a gallery section, the
   *                  API always requires it.
   * @arg destination Either the root of the specified *section* or a
   *                  particular folder.
   * @arg files       The files being moved.
   */
  moveFiles: (
    section: GallerySection,
    destination: Destination,
    files: RsSet<GalleryFile>,
  ) => Promise<void>;

  /**
   * Delete the specified files. If the files are Filestores then they are disconnected.
   *
   * @arg files The files being deleted.
   */
  deleteFiles: (files: RsSet<GalleryFile>) => Promise<void>;

  /**
   * Duplicate the specified files.
   */
  duplicateFiles: (files: RsSet<GalleryFile>) => Promise<void>;

  /**
   * Rename the specified file.
   *
   * @arg file The file being renamed.
   * @arg newName The new name for the file.
   */
  rename: (file: GalleryFile, newName: string) => Promise<void>;

  /**
   * The contents of *file* is replaced with *newFile*. The filename is also
   * replaced and the version number incremented.
   *
   * @arg folderId The Id of the folder that *file* currently resides in.
   * @arg file     The file whose contents are being updated.
   * @arg newFile  The contents that *file* is being updated to.
   */
  uploadNewVersion: (
    folderId: Id,
    file: GalleryFile,
    newFile: File,
  ) => Promise<void>;

  /**
   * Modify the description of a specified file.
   *
   * @arg file           The file whose description is being modified.
   * @arg newDescription The new description.
   */
  changeDescription: (
    file: GalleryFile,
    newDescription: Description,
  ) => Promise<void>;

  /**
   * Download the specified files.
   *
   * @arg files The files being downloaded.
   */
  download: (files: RsSet<GalleryFile>) => Promise<void>;
} {
  const { addAlert, removeAlert } = React.useContext(AlertContext);
  const { getToken } = useOauthToken();
  const { trackEvent } = React.useContext(AnalyticsContext);

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
    parentId: Id,
    files: ReadonlyArray<File>,
    options?: { originalImageId: Id },
  ) {
    const uploadingAlert = mkAlert({
      message: "Uploading...",
      variant: "notice",
      isInfinite: true,
    });
    addAlert(uploadingAlert);

    const api = axios.create({
      baseURL: "/api/v1/files",
      headers: {
        Authorization: "Bearer " + (await getToken()),
      },
    });

    try {
      const data = await Promise.all(
        files.map((file) => {
          const formData = new FormData();
          formData.append("file", file);
          formData.append("folderId", idToString(parentId).elseThrow());
          if (options?.originalImageId)
            formData.append(
              "originalImageId",
              idToString(options.originalImageId).elseThrow(),
            );
          return api.post<unknown>("/", formData, {
            headers: {
              "Content-Type": "multipart/form-data",
            },
          });
        }),
      );

      addAlert(
        Result.any(
          ...data.map((d) =>
            Parsers.objectPath(["data", "exceptionMessage"], d).flatMap(
              Parsers.isString,
            ),
          ),
        )
          .map((exceptionMessages) =>
            mkAlert({
              message: `Failed to upload file${files.length === 1 ? "" : "s"}.`,
              variant: "error",
              details: exceptionMessages.map((m) => ({
                title: m,
                variant: "error",
              })),
            }),
          )
          .orElse(
            mkAlert({
              message: `Successfully uploaded file${
                files.length === 1 ? "" : "s"
              }.`,
              variant: "success",
            }),
          ),
      );
    } catch (e) {
      if (e instanceof Error) {
        const message = Parsers.objectPath(["response", "data", "message"], e)
          .flatMap(Parsers.isString)
          .orElse(e.message);
        console.error(e);
        addAlert(
          mkAlert({
            variant: "error",
            title: `Failed to upload file${files.length === 1 ? "" : "s"}.`,
            message,
          }),
        );
        throw e;
      } else {
        throw new Error("Unexpected error occurred");
      }
    } finally {
      removeAlert(uploadingAlert);
    }
  }

  async function createFolder(parentId: Id, name: string) {
    try {
      const formData = new FormData();
      formData.append("folderName", name);
      formData.append("parentId", idToString(parentId).elseThrow());
      formData.append("isMedia", "true");
      const data = await galleryApi.post<unknown>("createFolder", formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      });

      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .flatMap(Parsers.isString)
          .map((exceptionMessage) =>
            mkAlert({
              title: `Failed to create new folder.`,
              message: exceptionMessage,
              variant: "error",
            }),
          )
          .orElse(
            mkAlert({
              message: `Successfully created new folder.`,
              variant: "success",
            }),
          ),
      );
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to create new folder.`,
          message: e.message,
        }),
      );
      throw e;
    }
  }

  async function moveFiles(
    section: GallerySection,
    destination: Destination,
    files: RsSet<GalleryFile>,
  ): Promise<void> {
    if (files.some((f) => f.canBeMoved.isError)) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Failed to move files.",
          message: "Some of the selected files cannot be moved.",
        }),
      );
      throw new Error("Some of the files cannot be moved");
    }
    if (destination.key === "folder" && destination.folder.isSnippetFolder) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Cannot move files into SNIPPETS folders.",
          message:
            "Share them and they will automatically appear in these folders.",
        }),
      );
      return;
    }
    const movingAlert = mkAlert({
      message: "Moving...",
      variant: "notice",
      isInfinite: true,
    });
    try {
      addAlert(movingAlert);
      const formData = new FormData();
      formData.append(
        "target",
        destination.key === "root"
          ? "0"
          : idToString(destination.folder.id).elseThrow(),
      );
      for (const file of files)
        formData.append("filesId[]", idToString(file.id).elseThrow());
      // mediaType is required, but only actually used if target is 0
      formData.append("mediaType", section);
      const data = await galleryApi.post<unknown>(
        "moveGalleriesElements",
        formData,
        {
          headers: {
            "Content-Type": "multipart/form-data",
          },
        },
      );
      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .orElseTry(() =>
            Parsers.objectPath(["data", "error", "errorMessages"], data)
              .flatMap(Parsers.isArray)
              .flatMap(ArrayUtils.head),
          )
          .flatMap(Parsers.isString)
          .map((exceptionMessage) =>
            mkAlert({
              title: `Failed to move item.`,
              message: exceptionMessage,
              variant: "error",
            }),
          )
          .orElse(
            mkAlert({
              message: `Successfully moved item${files.size > 0 ? "s" : ""}.`,
              variant: "success",
            }),
          ),
      );
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to move item${files.size > 0 ? "s" : ""}.`,
          message: e.message,
        }),
      );
      throw e;
    } finally {
      removeAlert(movingAlert);
    }
  }

  async function deleteLocalFiles(files: RsSet<LocalGalleryFile>) {
    const deletingAlert = mkAlert({
      message: "Deleting...",
      variant: "notice",
      isInfinite: true,
    });
    try {
      addAlert(deletingAlert);
      const formData = new FormData();
      for (const file of files)
        formData.append("idsToDelete[]", idToString(file.id).elseThrow());
      const data = await galleryApi.post<unknown>(
        "deleteElementFromGallery",
        formData,
        {
          headers: {
            "content-type": "multipart/form-data",
          },
        },
      );
      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .orElseTry(() =>
            Parsers.objectPath(["data", "error", "errorMessages"], data)
              .flatMap(Parsers.isArray)
              .flatMap(ArrayUtils.head),
          )
          .flatMap(Parsers.isString)
          .map((exceptionMessage) =>
            mkAlert({
              title: `Failed to delete item.`,
              message: exceptionMessage,
              variant: "error",
            }),
          )
          .orElse(
            mkAlert({
              message: `Successfully deleted item${files.size > 0 ? "s" : ""}.`,
              variant: "success",
            }),
          ),
      );
    } finally {
      removeAlert(deletingAlert);
    }
  }

  async function deleteFilestore(filestore: Filestore) {
    const api = axios.create({
      baseURL: "/api/v1/gallery",
      headers: {
        Authorization: "Bearer " + (await getToken()),
      },
    });
    try {
      await api.delete<unknown>(
        `filestores/${idToString(filestore.id).elseThrow()}`,
      );
      addAlert(
        mkAlert({
          message: "Successfully deleted filestore.",
          variant: "success",
        }),
      );
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: "Failed to delete filestore.",
          message: e.message,
        }),
      );
      throw e;
    }
  }

  async function deleteFiles(files: RsSet<GalleryFile>) {
    if (files.some((f) => f.canDelete.isError)) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Failed to delete files.",
          message: "Some of the selected files cannot be deleted.",
        }),
      );
      throw new Error("Some of the files cannot be deleted");
    }
    if (files.every((f) => f instanceof Filestore)) {
      await Promise.all(files.filterClass(Filestore).map(deleteFilestore));
      return;
    }
    try {
      if (files.some((f) => !(f instanceof LocalGalleryFile)))
        throw new Error("Can only delete local files");
      await deleteLocalFiles(files.filterClass(LocalGalleryFile));
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to delete item${files.size > 0 ? "s" : ""}.`,
          message: e.message,
        }),
      );
      throw e;
    }
  }

  async function duplicateFiles(files: RsSet<GalleryFile>) {
    if (files.some((f) => f.canDuplicate.isError)) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Failed to duplicate files.",
          message: "Some of the selected files cannot be duplicated.",
        }),
      );
      throw new Error("Some of the files cannot be duplicated");
    }
    const formData = new FormData();
    for (const file of files) {
      formData.append("idToCopy[]", idToString(file.id).elseThrow());
      formData.append(
        "newName[]",
        file.transformFilename((name) => name + "_copy"),
      );
    }
    const duplicatingAlert = mkAlert({
      message: "Duplicating...",
      variant: "notice",
      isInfinite: true,
    });
    try {
      addAlert(duplicatingAlert);
      const data = await galleryApi.post<unknown>("copyGalleries", formData, {
        headers: {
          "content-type": "multipart/form-data",
        },
      });
      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .orElseTry(() =>
            Parsers.objectPath(["data", "error", "errorMessages"], data)
              .flatMap(Parsers.isArray)
              .flatMap(ArrayUtils.head),
          )
          .flatMap(Parsers.isString)
          .map((exceptionMessage) =>
            mkAlert({
              title: `Failed to duplicate item.`,
              message: exceptionMessage,
              variant: "error",
            }),
          )
          .orElse(
            mkAlert({
              message: `Successfully duplicated item${
                files.size > 0 ? "s" : ""
              }.`,
              variant: "success",
            }),
          ),
      );
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to duplicate item${files.size > 0 ? "s" : ""}.`,
          message: e.message,
        }),
      );
      throw e;
    } finally {
      removeAlert(duplicatingAlert);
    }
  }

  async function rename(file: GalleryFile, newName: string) {
    if (file.canRename.isError) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Failed to rename file.",
          message: "The selected file cannot be renamed.",
        }),
      );
      throw new Error("The file cannot be renamed");
    }
    const renamingAlert = mkAlert({
      message: "Renaming...",
      variant: "notice",
      isInfinite: true,
    });
    const formData = new FormData();
    formData.append("recordId", idToString(file.id).elseThrow());
    formData.append(
      "newName",
      file.transformFilename(() => newName),
    );
    try {
      addAlert(renamingAlert);
      if (typeof file.setName === "undefined")
        throw new Error("This file cannot be renamed");
      const setName = file.setName;
      const data = await structuredDocumentApi.post<unknown>(
        "rename",
        formData,
        {
          headers: {
            "content-type": "multipart/form-data",
          },
        },
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
        }),
      );

      setName(file.transformFilename(() => newName));
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to rename item.`,
          message: e.message,
        }),
      );
      throw e;
    } finally {
      removeAlert(renamingAlert);
    }
  }

  async function uploadNewVersion(
    folderId: Id,
    file: GalleryFile,
    newFile: File,
  ) {
    if (file.canUploadNewVersion.isError) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Failed to upload new version for the file.",
          message: "A new version for this file cannot be set.",
        }),
      );
      throw new Error("The selected file cannot be updated with a new version");
    }
    const formData = new FormData();
    formData.append("selectedMediaId", idToString(file.id).elseThrow());
    formData.append("xfile", newFile);
    formData.append("targetFolderId", idToString(folderId).elseThrow());
    const uploadingAlert = mkAlert({
      message: "Uploading...",
      variant: "notice",
      isInfinite: true,
    });
    try {
      addAlert(uploadingAlert);
      const { data } = await galleryApi.post<unknown>("uploadFile", formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      });
      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .flatMap(Parsers.isString)
          .orElseTry(() =>
            Parsers.objectPath(["exceptionMessage"], data).flatMap(
              Parsers.isString,
            ),
          )
          .map((exceptionMessage) =>
            mkAlert({
              title: `Failed to upload new version.`,
              message: exceptionMessage,
              variant: "error",
            }),
          )
          .orElse(
            mkAlert({
              message: `Successfully uploaded new version.`,
              variant: "success",
            }),
          ),
      );
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to upload new version.`,
          message: e.message,
        }),
      );
      throw e;
    } finally {
      removeAlert(uploadingAlert);
    }
  }

  async function changeDescription(
    file: GalleryFile,
    newDescription: Description,
  ) {
    if (
      !file.description.match({
        missing: () => false,
        empty: () => true,
        present: () => true,
      })
    ) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Failed to change file description.",
          message: "The file does not have a description.",
        }),
      );
      throw new Error("The file does not have a description");
    }
    const formData = new FormData();
    formData.append("recordId", idToString(file.id).elseThrow());
    formData.append(
      "description",
      newDescription.match({
        missing: () => {
          throw new Error("Description is missing");
        },
        empty: () => "",
        present: (d) => d,
      }),
    );
    try {
      if (typeof file.setDescription === "undefined")
        throw new Error("Cannot edit description");
      const setDescription = file.setDescription;
      const data = await structuredDocumentApi.post<unknown>(
        "description",
        formData,
        {
          headers: {
            "content-type": "multipart/form-data",
          },
        },
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
        }),
      );

      trackEvent("user:edit:description:gallery");

      setDescription(newDescription);
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: `Failed to update description.`,
          message: e.message,
        }),
      );
      throw e;
    }
  }

  async function download(files: RsSet<GalleryFile>) {
    try {
      const { fulfilled, rejected } = partitionAllSettled(
        await Promise.allSettled(
          [...files].map(async (file) => {
            const link = document.createElement("a");
            if (!file.downloadHref)
              throw new Error(`Cannot download ${file.name}`);
            link.href = await file.downloadHref();
            link.download = file.name;
            link.click();
            return file;
          }),
        ),
      );
      if (fulfilled.length > 0) {
        addAlert(
          mkAlert({
            variant: "success",
            message: `Successfully downloaded ${
              rejected.length > 0 ? "some of " : ""
            }the files.`,
            ...(rejected.length > 0
              ? {
                  details: fulfilled.map((f) => ({
                    variant: "success",
                    title: f.name,
                  })),
                }
              : {}),
          }),
        );
      }
      if (rejected.length > 0) {
        const rejectedResponses = await Promise.all(
          rejected.map(async (response) => {
            try {
              const data = Parsers.objectPath(
                ["response", "data"],
                response,
              ).elseThrow();
              if (!(data instanceof Blob))
                throw new Error("Response is not a blob");
              const json = JSON.parse(await data.text()) as unknown;
              return Parsers.objectPath(["message"], json)
                .flatMap(Parsers.isString)
                .elseThrow();
            } catch (e) {
              if (e instanceof Error) {
                return Promise.resolve(e.message);
              }
              return Promise.resolve("Unknown error");
            }
          }),
        );
        addAlert(
          mkAlert({
            variant: "error",
            message: `Failed to download ${
              fulfilled.length > 0 ? "some of " : ""
            }the files.`,
            details: rejectedResponses.map((errorMsg) => ({
              variant: "error",
              title: errorMsg,
            })),
          }),
        );
      }
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: "Failed to download all the files.",
          message: e.message,
        }),
      );
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
    download,
  };
}
