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

  /** Operation names recorded in S3 object metadata for audit traceability. */
  String OPERATION_MOVE = "move";

  String OPERATION_COPY = "copy";
  String OPERATION_TRANSFER = "transfer";

  /**
   * Uploads RSpace media files to a writable filestore and creates an {@code
   * ExternalStorageLocation} row per success. The {@code operation} string (typically {@link
   * #OPERATION_MOVE} or {@link #OPERATION_COPY}) is recorded in backend-native audit metadata so
   * shared-IAM writes can be traced back to the originating RSpace user.
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
      User user,
      String operation)
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
}
