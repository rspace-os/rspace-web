/**
 * RSpace API Access your RSpace Filestores programmatically. All requests require authentication
 * using an API key set as the value of the header `RSpace-API-Key`.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.api.v1.model.ApiGalleryFilestoreOperationRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreTransferRequest;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileStoreInfo;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import com.researchspace.netfiles.ApiNfsCredentials;
import com.researchspace.netfiles.ApiNfsRemotePathBrowseResult;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
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
   * Moves Gallery items into the filestore identified by {@code filestoreId}. After successful
   * upload, the RSpace media file records are deleted and {@code ExternalStorageLocation} link rows
   * are created. Unwritable backends produce 501 Not Implemented.
   */
  @PostMapping(
      value = "/filestores/{filestoreId}/move",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  ApiExternalStorageOperationResult moveToFilestore(
      Long filestoreId,
      ApiGalleryFilestoreOperationRequest request,
      BindingResult errors,
      User user)
      throws BindException;

  /**
   * Copies Gallery items into the filestore identified by {@code filestoreId} (does not delete from
   * RSpace). An {@code ExternalStorageLocation} link row is created per success. Unwritable
   * backends produce 501.
   */
  @PostMapping(
      value = "/filestores/{filestoreId}/copy",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  ApiExternalStorageOperationResult copyToFilestore(
      Long filestoreId,
      ApiGalleryFilestoreOperationRequest request,
      BindingResult errors,
      User user)
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
