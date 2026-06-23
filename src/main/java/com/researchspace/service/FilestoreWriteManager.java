package com.researchspace.service;

import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.api.v1.model.ApiGalleryFilestoreOperationRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreTransferRequest;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.netfiles.FilestoreAuditMetadata;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

/**
 * Service that handles write operations against gallery filestores: uploading RSpace media files
 * into a filestore and server-side transfers between filestores. Encapsulates the resolution of the
 * writable client, the path-prefix logic, and the {@code ExternalStorageLocation} bookkeeping that
 * move/copy/transfer have in common across S3, iRODS and future backends.
 */
public interface FilestoreWriteManager {

  /** Result of an upload operation: per-file outcome plus the media files actually uploaded. */
  @Data
  @AllArgsConstructor
  class UploadOutcome {
    private ApiExternalStorageOperationResult operationResult;
    private Set<EcatMediaFile> succeededMediaFiles;
  }

  /**
   * Uploads RSpace media files to a writable filestore and creates an {@code
   * ExternalStorageLocation} row per success.
   *
   * @param filestoreId destination filestore id
   * @return per-file results plus the media files that succeeded (caller may delete them for a move
   *     operation)
   * @throws BindException when input is invalid or upload fails
   */
  UploadOutcome uploadToFilestore(
      Long filestoreId,
      ApiGalleryFilestoreOperationRequest request,
      BindingResult errors,
      User user)
      throws BindException;

  /**
   * Server-side transfers a single object between two filestores. Currently supports S3↔S3 only;
   * other source/destination backends throw {@link UnsupportedOperationException}.
   *
   * @param sourceFilestoreId source filestore id (the one being read from)
   */
  ApiExternalStorageOperationResult transferBetweenFilestores(
      Long sourceFilestoreId,
      ApiGalleryFilestoreTransferRequest request,
      BindingResult errors,
      User user)
      throws BindException;

  /**
   * Deletes a file or empty folder within an S3 filestore, subject to the creator/age gate: the
   * object must carry {@code rspace-created-by} equal to {@code user} and a {@code
   * rspace-created-at} within the configured window, otherwise a {@link
   * FilestoreOperationForbiddenException} (HTTP 403) is thrown. A non-empty folder is rejected.
   *
   * @param filestoreId the filestore containing the item
   * @param path filestore-relative path of the file or folder to delete
   */
  void deleteFromFilestore(Long filestoreId, String path, BindingResult errors, User user)
      throws BindException;

  /**
   * Reads the creator/creation-time audit metadata of the file or folder at {@code path}
   * (filestore-relative), for on-demand display in the Gallery info panel. Requires read access.
   * Returns empty metadata for backends without native metadata (e.g. iRODS) or objects lacking it.
   *
   * @param filestoreId the filestore containing the item
   * @param path filestore-relative path of the file or folder
   */
  FilestoreAuditMetadata getAuditMetadata(Long filestoreId, String path, User user)
      throws BindException;

  /**
   * Creates a new subfolder within a filestore, stamping the creator/creation-time audit metadata
   * so it is subject to the same delete gate as files.
   *
   * @param parentPath filestore-relative path of the parent folder ({@code ""} for the root)
   * @param folderName name of the new folder
   * @return the filestore-relative path of the created folder
   */
  String createFolderInFilestore(
      Long filestoreId, String parentPath, String folderName, BindingResult errors, User user)
      throws BindException;

  /**
   * Moves a file or folder to another folder within the same filestore (server-side, preserving the
   * item's creator/creation-time metadata).
   *
   * <p>Authorized by the filesystem write-allowlist only. Unlike {@link #deleteFromFilestore}, move
   * is deliberately <em>not</em> subject to the per-object creator/age gate: relocating an item
   * within the same filestore is a reorganisation rather than a destruction (the data is preserved,
   * along with its original {@code rspace-created-by}/{@code -at} attribution), so it is intended
   * to be more lenient than delete. Any user on the write-allowlist may move any item in the
   * filestore.
   *
   * @param sourcePath filestore-relative path of the item to move
   * @param destFolderPath filestore-relative path of the destination folder
   * @return the filestore-relative path of the moved item at its new location
   */
  String moveWithinFilestore(
      Long filestoreId, String sourcePath, String destFolderPath, BindingResult errors, User user)
      throws BindException;
}
