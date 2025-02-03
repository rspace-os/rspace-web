/**
 * RSpace API Access your RSpace Filestores programmatically. All requests require authentication
 * using an API key set as the value of the header `RSpace-API-Key`.
 */
package com.researchspace.api.v1;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileStoreInfo;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import com.researchspace.netfiles.ApiNfsCredentials;
import com.researchspace.netfiles.ApiNfsRemotePathBrowseResult;
import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
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
}
