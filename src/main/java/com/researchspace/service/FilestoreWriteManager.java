package com.researchspace.service;

import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.api.v1.model.ApiGalleryFilestoreOperationRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreTransferRequest;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
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
   * Deletes a file or folder within an S3 filestore, subject to the creator/age gate: every object
   * removed must carry {@code rspace-created-by} equal to {@code user} and a {@code
   * rspace-created-at} within the configured window. For a folder this is evaluated atomically over
   * the placeholder and every descendant — if any object fails, nothing is deleted and an {@link
   * org.apache.shiro.authz.AuthorizationException} is thrown (mapped to HTTP 403).
   *
   * @param filestoreId the filestore containing the item
   * @param path filestore-relative path of the file or folder to delete
   */
  void deleteFromFilestore(Long filestoreId, String path, BindingResult errors, User user)
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
   * @param sourcePath filestore-relative path of the item to move
   * @param destFolderPath filestore-relative path of the destination folder
   * @return the filestore-relative path of the moved item at its new location
   */
  String moveWithinFilestore(
      Long filestoreId, String sourcePath, String destFolderPath, BindingResult errors, User user)
      throws BindException;
}
