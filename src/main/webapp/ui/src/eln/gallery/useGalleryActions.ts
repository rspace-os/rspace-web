import React from "react";
import axios from "@/common/axios";
import i18n from "@/modules/common/i18n";
import { getErrorMessage } from "@/util/error";
import useOauthToken from "../../hooks/auth/useOauthToken";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import AnalyticsContext from "../../stores/contexts/Analytics";
import * as Parsers from "../../util/parsers";
import Result from "../../util/result";
import type RsSet from "../../util/set";
import { partitionAllSettled } from "../../util/Util";
import type { GallerySection } from "./common";
import {
  type Description,
  Filestore,
  type GalleryFile,
  type Id,
  idToString,
  LocalGalleryFile,
  RemoteFile,
} from "./useGalleryListing";

const ONE_MINUTE_IN_MS = 60 * 1000;
// Uploads need longer than the gallery clients' 1-minute default.
const UPLOAD_TIMEOUT_MS = 10 * 60 * 1000;

const firstResult = <T>(items: ReadonlyArray<T>): Result<T> =>
  Result.fromNullable(items.at(0), new Error("Array is empty"));

/**
 * Best error message from a failed filestore API call: the first non-blank entry of the
 * BindException `errors` array, else `data.message`/`exceptionMessage` via getErrorMessage. A 403
 * gate denial returns its reason in `message` with a blank `errors: [""]`, so blanks must not win.
 */
function firstErrorMessage(e: unknown): string {
  return Parsers.objectPath(["response", "data", "errors"], e)
    .flatMap(Parsers.isArray)
    .flatMap(firstResult)
    .flatMap(Parsers.isString)
    .flatMap((s) => (s.trim().length > 0 ? Result.Ok(s) : Result.Error<string>([new Error("blank")])))
    .orElse(getErrorMessage(e, i18n.t("gallery:errors.unknownError")));
}

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
  uploadFiles: (parentId: Id, files: ReadonlyArray<File>, options?: { originalImageId: Id }) => Promise<void>;

  /**
   * For creating new folders.
   *
   * @arg parentId The id of the folder or gallery section that the new folder
   *               will be created within.
   * @arg name     The name of the new folder.
   */
  createFolder: (parentId: Id, name: string) => Promise<void>;

  /** Create a folder inside an S3 filestore, under `path` ("" for the filestore root). */
  createRemoteFolder: (filestoreId: number, path: string, name: string) => Promise<void>;

  /** Move items to another folder within the same S3 filestore (destPath "" for the root). */
  moveRemoteFiles: (sources: ReadonlyArray<RemoteFile>, destPath: string) => Promise<void>;

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
  moveFiles: (section: GallerySection, destination: Destination, files: RsSet<GalleryFile>) => Promise<void>;

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
  uploadNewVersion: (folderId: Id, file: GalleryFile, newFile: File) => Promise<void>;

  /**
   * Modify the description of a specified file.
   *
   * @arg file           The file whose description is being modified.
   * @arg newDescription The new description.
   */
  changeDescription: (file: GalleryFile, newDescription: Description) => Promise<void>;

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

  /** Authenticated client for the REST gallery API (/api/v1/gallery). */
  const remoteGalleryApi = async () =>
    axios.create({
      baseURL: "/api/v1/gallery",
      headers: { Authorization: `Bearer ${await getToken()}` },
    });

  async function uploadFiles(parentId: Id, files: ReadonlyArray<File>, options?: { originalImageId: Id }) {
    const uploadingAlert = mkAlert({
      message: i18n.t("gallery:actions.inProgress.uploading"),
      variant: "notice",
      isInfinite: true,
    });
    addAlert(uploadingAlert);

    const api = axios.create({
      baseURL: "/api/v1/files",
      headers: {
        Authorization: `Bearer ${await getToken()}`,
      },
    });

    try {
      const data = await Promise.all(
        files.map((file) => {
          const formData = new FormData();
          formData.append("file", file);
          formData.append("folderId", idToString(parentId).elseThrow());
          if (options?.originalImageId)
            formData.append("originalImageId", idToString(options.originalImageId).elseThrow());
          return api.post<unknown>("/", formData, {
            headers: {
              "Content-Type": "multipart/form-data",
            },
          });
        }),
      );

      addAlert(
        Result.any(...data.map((d) => Parsers.objectPath(["data", "exceptionMessage"], d).flatMap(Parsers.isString)))
          .map((exceptionMessages) =>
            mkAlert({
              message: i18n.t("gallery:actions.upload.failed", { count: files.length }),
              variant: "error",
              details: exceptionMessages.map((m) => ({
                title: m,
                variant: "error",
              })),
            }),
          )
          .orElse(
            mkAlert({
              message: i18n.t("gallery:actions.upload.success", { count: files.length }),
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
            title: i18n.t("gallery:actions.upload.failed", { count: files.length }),
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
              title: i18n.t("gallery:actions.folder.createFailed"),
              message: exceptionMessage,
              variant: "error",
            }),
          )
          .orElse(
            mkAlert({
              message: i18n.t("gallery:actions.folder.createSuccess"),
              variant: "success",
            }),
          ),
      );
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: i18n.t("gallery:actions.folder.createFailed"),
          message: e.message,
        }),
      );
      throw e;
    }
  }

  async function createRemoteFolder(filestoreId: number, path: string, name: string) {
    const api = await remoteGalleryApi();
    try {
      await api.post<unknown>(`filestores/${filestoreId}/folder`, { path, name });
      addAlert(
        mkAlert({
          message: i18n.t("gallery:actions.folder.createSuccess"),
          variant: "success",
        }),
      );
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: i18n.t("gallery:actions.folder.createFailed"),
          message: firstErrorMessage(e),
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
          title: i18n.t("gallery:actions.move.filesFailed"),
          message: i18n.t("gallery:actions.move.filesCannotBeMoved"),
        }),
      );
      throw new Error("Some of the files cannot be moved");
    }
    if (destination.key === "folder" && destination.folder.isSnippetFolder) {
      addAlert(
        mkAlert({
          variant: "error",
          title: i18n.t("gallery:actions.move.snippetsFolderFailed"),
          message: i18n.t("gallery:actions.move.snippetsFolderMessage"),
        }),
      );
      return;
    }
    const movingAlert = mkAlert({
      message: i18n.t("gallery:actions.inProgress.moving"),
      variant: "notice",
      isInfinite: true,
    });
    try {
      addAlert(movingAlert);
      const formData = new FormData();
      formData.append("target", destination.key === "root" ? "0" : idToString(destination.folder.id).elseThrow());
      for (const file of files) formData.append("filesId[]", idToString(file.id).elseThrow());
      // mediaType is required, but only actually used if target is 0
      formData.append("mediaType", section);
      const data = await galleryApi.post<unknown>("moveGalleriesElements", formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      });
      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .orElseTry(() =>
            Parsers.objectPath(["data", "error", "errorMessages"], data).flatMap(Parsers.isArray).flatMap(firstResult),
          )
          .flatMap(Parsers.isString)
          .map((exceptionMessage) =>
            mkAlert({
              title: i18n.t("gallery:actions.move.itemFailed"),
              message: exceptionMessage,
              variant: "error",
            }),
          )
          .orElse(
            mkAlert({
              message: i18n.t("gallery:actions.move.itemsSuccess", { count: files.size }),
              variant: "success",
            }),
          ),
      );
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: i18n.t("gallery:actions.move.itemsFailed", { count: files.size }),
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
      message: i18n.t("gallery:actions.inProgress.deleting"),
      variant: "notice",
      isInfinite: true,
    });
    try {
      addAlert(deletingAlert);
      const formData = new FormData();
      for (const file of files) formData.append("idsToDelete[]", idToString(file.id).elseThrow());
      const data = await galleryApi.post<unknown>("deleteElementFromGallery", formData, {
        headers: {
          "content-type": "multipart/form-data",
        },
      });
      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .orElseTry(() =>
            Parsers.objectPath(["data", "error", "errorMessages"], data).flatMap(Parsers.isArray).flatMap(firstResult),
          )
          .flatMap(Parsers.isString)
          .map((exceptionMessage) =>
            mkAlert({
              title: i18n.t("gallery:actions.delete.itemFailed"),
              message: exceptionMessage,
              variant: "error",
            }),
          )
          .orElse(
            mkAlert({
              message: i18n.t("gallery:actions.delete.itemsSuccess", { count: files.size }),
              variant: "success",
            }),
          ),
      );
    } finally {
      removeAlert(deletingAlert);
    }
  }

  /**
   * Run a per-item S3 filestore write op. Each item is a separate API call gated server-side, so
   * failures are collected and reported together rather than aborting the batch. Callers supply
   * complete user-facing phrases (not assembled from fragments) so each is translatable as a unit.
   */
  async function runPerItemFilestoreOp(
    items: ReadonlyArray<RemoteFile>,
    messages: {
      inProgress: string;
      success: (count: number) => string;
      failure: (count: number) => string;
    },
    request: (api: ReturnType<typeof axios.create>, file: RemoteFile) => Promise<unknown>,
  ) {
    const progressAlert = mkAlert({
      message: messages.inProgress,
      variant: "notice",
      isInfinite: true,
    });
    try {
      addAlert(progressAlert);
      const api = await remoteGalleryApi();
      const failures: Array<string> = [];
      for (const file of items) {
        try {
          await request(api, file);
        } catch (e) {
          failures.push(`${file.name}: ${firstErrorMessage(e)}`);
        }
      }
      if (failures.length === 0) {
        addAlert(
          mkAlert({
            message: messages.success(items.length),
            variant: "success",
          }),
        );
      } else {
        addAlert(
          mkAlert({
            variant: "error",
            title: messages.failure(failures.length),
            message: failures.join("; "),
          }),
        );
      }
    } finally {
      removeAlert(progressAlert);
    }
  }

  /** Delete files/folders inside an S3 filestore (POST /filestores/{id}/delete per item). */
  async function deleteRemoteFiles(files: RsSet<RemoteFile>) {
    await runPerItemFilestoreOp(
      files.toArray(),
      {
        inProgress: i18n.t("gallery:actions.inProgress.deleting"),
        success: (count) => i18n.t("gallery:actions.delete.remoteSuccess", { count }),
        failure: (count) => i18n.t("gallery:actions.delete.remoteFailed", { count }),
      },
      (api, file) =>
        api.post<unknown>(`filestores/${idToString(file.path[0].id).elseThrow()}/delete`, { path: file.remotePath }),
    );
  }

  /** Move items to another folder within the same S3 filestore (POST /filestores/{id}/move per item). */
  async function moveRemoteFiles(sources: ReadonlyArray<RemoteFile>, destPath: string) {
    await runPerItemFilestoreOp(
      sources,
      {
        inProgress: i18n.t("gallery:actions.inProgress.moving"),
        success: (count) => i18n.t("gallery:actions.move.remoteSuccess", { count }),
        failure: (count) => i18n.t("gallery:actions.move.remoteFailed", { count }),
      },
      (api, file) =>
        api.post<unknown>(`filestores/${idToString(file.path[0].id).elseThrow()}/move`, {
          sourcePath: file.remotePath,
          destPath,
        }),
    );
  }

  async function deleteFilestore(filestore: Filestore) {
    const api = await remoteGalleryApi();
    try {
      await api.delete<unknown>(`filestores/${idToString(filestore.id).elseThrow()}`);
      addAlert(
        mkAlert({
          message: i18n.t("gallery:actions.delete.filestoreSuccess"),
          variant: "success",
        }),
      );
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: i18n.t("gallery:actions.delete.filestoreFailed"),
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
          title: i18n.t("gallery:actions.delete.filesFailed"),
          message: i18n.t("gallery:actions.delete.filesCannotBeDeleted"),
        }),
      );
      throw new Error("Some of the files cannot be deleted");
    }
    if (files.every((f) => f instanceof Filestore)) {
      await Promise.all(files.filterClass(Filestore).map(deleteFilestore));
      return;
    }
    if (files.every((f) => f instanceof RemoteFile)) {
      await deleteRemoteFiles(files.filterClass(RemoteFile));
      return;
    }
    try {
      if (files.some((f) => !(f instanceof LocalGalleryFile))) throw new Error("Can only delete local files");
      await deleteLocalFiles(files.filterClass(LocalGalleryFile));
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: i18n.t("gallery:actions.delete.itemsFailed", { count: files.size }),
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
          title: i18n.t("gallery:actions.duplicate.filesFailed"),
          message: i18n.t("gallery:actions.duplicate.filesCannotBeDuplicated"),
        }),
      );
      throw new Error("Some of the files cannot be duplicated");
    }
    const formData = new FormData();
    for (const file of files) {
      formData.append("idToCopy[]", idToString(file.id).elseThrow());
      formData.append(
        "newName[]",
        file.transformFilename((name) => `${name}_copy`),
      );
    }
    const duplicatingAlert = mkAlert({
      message: i18n.t("gallery:actions.inProgress.duplicating"),
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
            Parsers.objectPath(["data", "error", "errorMessages"], data).flatMap(Parsers.isArray).flatMap(firstResult),
          )
          .flatMap(Parsers.isString)
          .map((exceptionMessage) =>
            mkAlert({
              title: i18n.t("gallery:actions.duplicate.itemFailed"),
              message: exceptionMessage,
              variant: "error",
            }),
          )
          .orElse(
            mkAlert({
              message: i18n.t("gallery:actions.duplicate.itemsSuccess", { count: files.size }),
              variant: "success",
            }),
          ),
      );
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: i18n.t("gallery:actions.duplicate.itemsFailed", { count: files.size }),
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
          title: i18n.t("gallery:actions.rename.fileFailed"),
          message: i18n.t("gallery:actions.rename.fileCannotBeRenamed"),
        }),
      );
      throw new Error("The file cannot be renamed");
    }
    const renamingAlert = mkAlert({
      message: i18n.t("gallery:actions.inProgress.renaming"),
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
      if (typeof file.setName === "undefined") throw new Error("This file cannot be renamed");
      const setName = file.setName;
      const data = await structuredDocumentApi.post<unknown>("rename", formData, {
        headers: {
          "content-type": "multipart/form-data",
        },
      });

      Parsers.objectPath(["data", "exceptionMessage"], data)
        .flatMap(Parsers.isString)
        .do((exceptionMessage) => {
          throw new Error(exceptionMessage);
        });

      addAlert(
        mkAlert({
          message: i18n.t("gallery:actions.rename.success"),
          variant: "success",
        }),
      );

      setName(file.transformFilename(() => newName));
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: i18n.t("gallery:actions.rename.failed"),
          message: e.message,
        }),
      );
      throw e;
    } finally {
      removeAlert(renamingAlert);
    }
  }

  async function uploadNewVersion(folderId: Id, file: GalleryFile, newFile: File) {
    if (file.canUploadNewVersion.isError) {
      addAlert(
        mkAlert({
          variant: "error",
          title: i18n.t("gallery:actions.uploadNewVersion.fileFailed"),
          message: i18n.t("gallery:actions.uploadNewVersion.cannotBeSet"),
        }),
      );
      throw new Error("The selected file cannot be updated with a new version");
    }
    const formData = new FormData();
    formData.append("selectedMediaId", idToString(file.id).elseThrow());
    formData.append("xfile", newFile);
    formData.append("targetFolderId", idToString(folderId).elseThrow());
    const uploadingAlert = mkAlert({
      message: i18n.t("gallery:actions.inProgress.uploading"),
      variant: "notice",
      isInfinite: true,
    });
    try {
      addAlert(uploadingAlert);
      const { data } = await galleryApi.post<unknown>("uploadFile", formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
        timeout: UPLOAD_TIMEOUT_MS,
      });
      addAlert(
        Parsers.objectPath(["data", "exceptionMessage"], data)
          .flatMap(Parsers.isString)
          .orElseTry(() => Parsers.objectPath(["exceptionMessage"], data).flatMap(Parsers.isString))
          .map((exceptionMessage) =>
            mkAlert({
              title: i18n.t("gallery:actions.uploadNewVersion.failed"),
              message: exceptionMessage,
              variant: "error",
            }),
          )
          .orElse(
            mkAlert({
              message: i18n.t("gallery:actions.uploadNewVersion.success"),
              variant: "success",
            }),
          ),
      );
    } catch (e) {
      if (!(e instanceof Error)) throw new Error("Unexpected error occurred");
      addAlert(
        mkAlert({
          variant: "error",
          title: i18n.t("gallery:actions.uploadNewVersion.failed"),
          message: e.message,
        }),
      );
      throw e;
    } finally {
      removeAlert(uploadingAlert);
    }
  }

  async function changeDescription(file: GalleryFile, newDescription: Description) {
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
          title: i18n.t("gallery:actions.description.changeFailed"),
          message: i18n.t("gallery:actions.description.missing"),
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
      if (typeof file.setDescription === "undefined") throw new Error("Cannot edit description");
      const setDescription = file.setDescription;
      const data = await structuredDocumentApi.post<unknown>("description", formData, {
        headers: {
          "content-type": "multipart/form-data",
        },
      });

      Parsers.objectPath(["data", "exceptionMessage"], data)
        .flatMap(Parsers.isString)
        .do((exceptionMessage) => {
          throw new Error(exceptionMessage);
        });

      addAlert(
        mkAlert({
          message: i18n.t("gallery:actions.description.updateSuccess"),
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
          title: i18n.t("gallery:actions.description.updateFailed"),
          message: getErrorMessage(e, i18n.t("gallery:errors.unknownError")),
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
            if (!file.downloadHref) throw new Error(`Cannot download ${file.name}`);
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
            message: i18n.t("gallery:actions.download.success", { partial: rejected.length > 0 }),
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
              const data = Parsers.objectPath(["response", "data"], response).elseThrow();
              if (!(data instanceof Blob)) throw new Error("Response is not a blob");
              const json = JSON.parse(await data.text()) as unknown;
              return Parsers.objectPath(["message"], json).flatMap(Parsers.isString).elseThrow();
            } catch (e) {
              if (e instanceof Error) {
                return Promise.resolve(e.message);
              }
              return Promise.resolve(i18n.t("gallery:errors.unknownError"));
            }
          }),
        );
        addAlert(
          mkAlert({
            variant: "error",
            message: i18n.t("gallery:actions.download.failed", { partial: fulfilled.length > 0 }),
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
          title: i18n.t("gallery:actions.download.allFailed"),
          message: e.message,
        }),
      );
    }
  }

  return {
    uploadFiles,
    createFolder,
    createRemoteFolder,
    moveRemoteFiles,
    moveFiles,
    deleteFiles,
    duplicateFiles,
    rename,
    uploadNewVersion,
    changeDescription,
    download,
  };
}
