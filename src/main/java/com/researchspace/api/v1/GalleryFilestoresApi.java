/**
 * RSpace API Access your RSpace Filestores programmatically. All requests require authentication
 * using an API key set as the value of the header `RSpace-API-Key`.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.api.v1.model.ApiGalleryFilestoreAuditInfo;
import com.researchspace.api.v1.model.ApiGalleryFilestoreDeleteRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreFolderRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreMoveRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreOperationRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreTransferRequest;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileStoreInfo;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import com.researchspace.netfiles.ApiNfsCredentials;
import com.researchspace.netfiles.ApiNfsRemotePathBrowseResult;
import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/api/v1/gallery")
public interface GalleryFilestoresApi {

  @GetMapping("/filestores")
  List<NfsFileStoreInfo> getUserFilestores(User user);

  @GetMapping("/filestores/{filestoreId}/browse")
  ApiNfsRemotePathBrowseResult browseFilestore(Long filestoreId, String browsePath, User user)
      throws IOException;

  @GetMapping("/filestores/{filestoreId}/download")
  @ResponseBody
  void downloadFromFilestore(
      Long filestoreId, String downloadPath, Long remoteId, User user, HttpServletResponse response)
      throws IOException;

  /**
   * Returns the RSpace write-provenance (created-by / created-at) of a single filestore item,
   * fetched on demand for the Gallery info panel. Both fields are null when the object carries no
   * such metadata. Requires read access.
   */
  @GetMapping("/filestores/{filestoreId}/metadata")
  ApiGalleryFilestoreAuditInfo getFilestoreItemMetadata(
      Long filestoreId, String remotePath, User user) throws BindException;

  @PostMapping("/filestores")
  @ResponseBody
  @ResponseStatus(HttpStatus.CREATED)
  NfsFileStoreInfo createFilestore(
      Long filesystemId, String filestoreName, String remotePath, User user);

  @DeleteMapping("/filestores/{filestoreId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteFilestore(Long filestoreId, User user);

  /* ========= filesystems =========== */

  @GetMapping("/filesystems")
  List<NfsFileSystemInfo> getFilesystems(User user);

  @GetMapping("/filesystems/{filesystemId}/browse")
  ApiNfsRemotePathBrowseResult browseFilesystem(Long filesystemId, String browsePath, User user)
      throws IOException;

  @PostMapping("/filesystems/{filesystemId}/login")
  @ResponseBody
  void loginToFilesystem(
      Long filesystemId, ApiNfsCredentials credentials, BindingResult errors, User user)
      throws BindException;

  @PostMapping("/filesystems/{filesystemId}/logout")
  @ResponseBody
  void logoutFromFilesystem(Long filesystemId, User user);

  /* ========= filestore write ops (unified across S3, iRODS, ...) =========== */

  /**
   * Uploads (imports) Gallery items into the filestore identified by {@code filestoreId}, creating
   * an {@code ExternalStorageLocation} link row per success. When {@code removeOriginalFromRspace}
   * in the request is true the RSpace media records are deleted after a successful upload (a
   * "move"); when false they are kept (a "copy"). Unwritable backends produce 501 Not Implemented.
   */
  @PostMapping(
      value = "/filestores/{filestoreId}/uploadFromGallery",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  ApiExternalStorageOperationResult uploadFromGallery(
      Long filestoreId,
      ApiGalleryFilestoreOperationRequest request,
      BindingResult errors,
      User user)
      throws BindException;

  /**
   * Moves a file or folder to another folder within the same filestore (server-side; S3 only).
   * Paths are relative to the filestore root; the moved item keeps its leaf name. Non-S3 backends
   * produce 501.
   */
  @PostMapping(
      value = "/filestores/{filestoreId}/move",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  ApiExternalStorageOperationResult moveWithinFilestore(
      Long filestoreId, ApiGalleryFilestoreMoveRequest request, BindingResult errors, User user)
      throws BindException;

  /**
   * Creates a new subfolder within the filestore identified by {@code filestoreId} (S3 only; a
   * zero-byte placeholder object carrying creator/creation-time metadata). Non-S3 backends produce
   * 501.
   */
  @PostMapping(
      value = "/filestores/{filestoreId}/folder",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  ApiExternalStorageOperationResult createFolder(
      Long filestoreId, ApiGalleryFilestoreFolderRequest request, BindingResult errors, User user)
      throws BindException;

  /**
   * Deletes a file or folder within the filestore identified by {@code filestoreId} (S3 only),
   * subject to the creator/age gate: only the creating user may delete, and only within the
   * configured window after creation. A non-empty folder is rejected. Returns 204 on success; 403
   * when the gate denies the deletion; 501 for non-S3 backends.
   */
  @PostMapping(
      value = "/filestores/{filestoreId}/delete",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteFromFilestore(
      Long filestoreId, ApiGalleryFilestoreDeleteRequest request, BindingResult errors, User user)
      throws BindException;

  /**
   * Server-side transfers a single object from the filestore identified by {@code filestoreId} (the
   * source) to the filestore identified by {@code destFilestoreId} in the body. Currently supports
   * S3↔S3 only; other source/destination backends produce 501. Rejects same source+dest filestore
   * id (within-filestore moves are out of scope of this subtask). When {@code deleteSource} is
   * true, the source object is deleted after a successful copy.
   */
  @PostMapping(
      value = "/filestores/{filestoreId}/transfer",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  ApiExternalStorageOperationResult transferBetweenFilestores(
      Long filestoreId, ApiGalleryFilestoreTransferRequest request, BindingResult errors, User user)
      throws BindException;
}
