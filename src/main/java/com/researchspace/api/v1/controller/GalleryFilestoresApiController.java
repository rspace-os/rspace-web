package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.GalleryFilestoresApi;
import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.api.v1.model.ApiGalleryFilestoreOperationRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreTransferRequest;
import com.researchspace.model.DeploymentPropertyType;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileStoreInfo;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import com.researchspace.netfiles.ApiNfsCredentials;
import com.researchspace.netfiles.ApiNfsRemotePathBrowseResult;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFactory;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.service.FilestoreWriteManager;
import com.researchspace.service.NfsFileHandler;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.webapp.controller.DeploymentProperty;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@NoArgsConstructor
@ApiController
public class GalleryFilestoresApiController extends GalleryFilestoresBaseApiController
    implements GalleryFilestoresApi {

  @Autowired @Setter NfsFileHandler nfsFileHandler;
  @Autowired @Setter NfsFactory nfsFactory;
  @Autowired RecordDeletionManager deletionManager;
  @Autowired FilestoreWriteManager filestoreWriteManager;

  @Override
  public List<NfsFileStoreInfo> getUserFilestores(@RequestAttribute(name = "user") User user) {
    assertFilestoresApiEnabled(user);
    return nfsManager.getFileStoreInfosForUser(user);
  }

  @Override
  public ApiNfsRemotePathBrowseResult browseFilestore(
      @PathVariable Long filestoreId,
      @RequestParam(value = "remotePath", required = false) String browsePath,
      @RequestAttribute(name = "user") User user)
      throws IOException {

    assertFilestoresApiEnabled(user);
    NfsFileStore filestore = nfsManager.getNfsFileStore(filestoreId);
    NfsFileSystem filesystem = filestore.getFileSystem();
    NfsClient nfsClient = getNfsClientForUserAndFilesystem(user, filesystem);

    String combinedPath = filestore.getPath();
    if (StringUtils.isNotBlank(browsePath)) {
      combinedPath += browsePath;
    }

    NfsFileTreeNode fileTree = nfsClient.createFileTree(combinedPath, null, filestore);
    return getRemotePathBrowseResult(filesystem, nfsClient, fileTree);
  }

  private NfsClient getNfsClientForUserAndFilesystem(User user, NfsFileSystem filesystem) {
    if (NfsAuthenticationType.NONE.equals(filesystem.getAuthType())) {
      return nfsFactory.getNfsClient(user.getUsername(), null, filesystem);
    }
    return credentialsStore.getNfsClientWithStoredCredentials(user, filesystem);
  }

  private ApiNfsRemotePathBrowseResult getRemotePathBrowseResult(
      NfsFileSystem filesystem, NfsClient nfsClient, NfsFileTreeNode fileTree) {

    return new ApiNfsRemotePathBrowseResult(
        fileTree,
        filesystem.toFileSystemInfo(),
        nfsClient.supportsExtraDirs() && !filesystem.fileSystemRequiresUserRootDirs(),
        nfsClient.supportsCurrentDir(),
        nfsClient.getUsername());
  }

  @Override
  public void downloadFromFilestore(
      @PathVariable Long filestoreId,
      @RequestParam(value = "remotePath", required = false) String remotePath,
      @RequestParam(name = "remoteId", required = false) Long remoteId,
      @RequestAttribute(name = "user") User user,
      HttpServletResponse response)
      throws IOException {

    assertFilestoresApiEnabled(user);
    if (StringUtils.isBlank(remotePath) && remoteId == null) {
      throw new IllegalArgumentException("Neither 'remotePath' nor 'remoteId' param is provided");
    }

    NfsFileStore filestore = nfsManager.getNfsFileStore(filestoreId);
    NfsFileSystem filesystem = filestore.getFileSystem();
    NfsClient nfsClient = getNfsClientForUserAndFilesystem(user, filesystem);

    String fullPath = filestore.getAbsolutePath(remotePath);
    NfsFileDetails nfsFileDetails =
        nfsFileHandler.downloadNfsFileToRSpace(new NfsTarget(fullPath, remoteId), nfsClient);
    File downloadedFile = nfsFileDetails.getLocalFile();
    log.info("downloaded to: " + downloadedFile.getCanonicalPath());

    response.setContentType("application/octet-stream");
    response.setContentLength((int) downloadedFile.length());
    response.setHeader(
        "Content-Disposition", "attachment; filename=\"" + downloadedFile.getName() + "\"");
    try (InputStream is = new FileInputStream(downloadedFile);
        ServletOutputStream out = response.getOutputStream()) {
      IOUtils.copy(is, out);
    }
  }

  @Override
  public NfsFileStoreInfo createFilestore(
      @RequestParam("filesystemId") Long filesystemId,
      @RequestParam("name") String filestoreName,
      @RequestParam("pathToSave") String pathToSave,
      @RequestAttribute(name = "user") User user) {

    assertFilestoresApiEnabled(user);
    boolean filestoreNameUnique = nfsManager.verifyFileStoreNameUniqueForUser(filestoreName, user);
    if (!filestoreNameUnique) {
      throw new IllegalArgumentException(
          "User already has a filestore named '"
              + filestoreName
              + "' - filestore name must be unique");
    }

    NfsFileStore userStore =
        nfsManager.createAndSaveNewFileStore(filesystemId, filestoreName, pathToSave, user);
    return userStore.toFileStoreInfo();
  }

  @Override
  public void deleteFilestore(
      @PathVariable Long filestoreId, @RequestAttribute(name = "user") User user) {

    assertFilestoresApiEnabled(user);
    NfsFileStore filestore = nfsManager.getNfsFileStore(filestoreId);
    assertFilestoreOwnedByUser(filestoreId, user, filestore);

    nfsManager.markFileStoreAsDeleted(filestore);
  }

  private void assertFilestoreOwnedByUser(Long filestoreId, User user, NfsFileStore filestore) {
    if (filestore == null || !user.getUsername().equals(filestore.getUser().getUsername())) {
      throw new NotFoundException(
          "Filestore '" + filestoreId + "' not found " + "or not accessible to the user");
    }
  }

  @Override
  public List<NfsFileSystemInfo> getFilesystems(@RequestAttribute(name = "user") User user) {
    assertFilestoresApiEnabled(user);
    return nfsManager.getActiveFileSystemInfos();
  }

  @Override
  public ApiNfsRemotePathBrowseResult browseFilesystem(
      @PathVariable Long filesystemId,
      @RequestParam(value = "remotePath", required = false) String browsePath,
      @RequestAttribute(name = "user") User user)
      throws IOException {

    assertFilestoresApiEnabled(user);
    NfsFileSystem filesystem = nfsManager.getFileSystem(filesystemId);
    NfsClient nfsClient = getNfsClientForUserAndFilesystem(user, filesystem);

    NfsFileTreeNode fileTree = nfsClient.createFileTree(browsePath, null, null);
    return getRemotePathBrowseResult(filesystem, nfsClient, fileTree);
  }

  @Override
  @DeploymentProperty(DeploymentPropertyType.NET_FILE_STORES_ENABLED)
  public void loginToFilesystem(
      @PathVariable Long filesystemId,
      @RequestBody @Valid ApiNfsCredentials credentials,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    assertFilestoresApiEnabled(user);
    NfsFileSystem filesystem = nfsManager.getFileSystem(filesystemId);
    NfsClient nfsClient =
        credentialsStore.validateCredentialsAndLoginNfs(credentials, errors, user, filesystem);
    try {
      nfsClient.tryConnectAndReadTarget("");
    } catch (Exception e) {
      credentialsStore.removeUserCredentialsForFilesystem(user, filesystemId);
      throw new ExternalApiAuthorizationException(
          "Error connecting to filesystem ["
              + filesystem.getName()
              + "] (id: "
              + filesystemId
              + "). Wrong credentials?");
    }
  }

  @Override
  public void logoutFromFilesystem(
      @PathVariable Long filesystemId, @RequestAttribute(name = "user") User user) {

    assertFilestoresApiEnabled(user);
    credentialsStore.removeUserCredentialsForFilesystem(user, filesystemId);
  }

  @Override
  public ApiExternalStorageOperationResult moveToFilestore(
      @PathVariable Long filestoreId,
      @RequestBody @Valid ApiGalleryFilestoreOperationRequest request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    assertFilestoresApiEnabled(user);
    throwBindExceptionIfErrors(errors);
    FilestoreWriteManager.UploadOutcome outcome =
        filestoreWriteManager.uploadToFilestore(filestoreId, request, errors, user);

    ApiExternalStorageOperationResult moveResult;
    try {
      moveResult =
          ApiExternalStorageOperationResult.of(
              deletionManager.deleteMediaFileSet(outcome.getSucceededMediaFiles(), user));
    } catch (AuthorizationException | ObjectRetrievalFailureException e) {
      log.error("Error deleting media files from RSpace: ", e);
      errors.addError(new ObjectError("mediaFile", e.getMessage()));
      throwBindExceptionIfErrors(errors);
      return outcome.getOperationResult();
    }
    moveResult.addAll(outcome.getOperationResult().getFailedRecords());
    return moveResult;
  }

  @Override
  public ApiExternalStorageOperationResult copyToFilestore(
      @PathVariable Long filestoreId,
      @RequestBody @Valid ApiGalleryFilestoreOperationRequest request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    assertFilestoresApiEnabled(user);
    throwBindExceptionIfErrors(errors);
    return filestoreWriteManager
        .uploadToFilestore(filestoreId, request, errors, user)
        .getOperationResult();
  }

  @Override
  public ApiExternalStorageOperationResult transferBetweenFilestores(
      @PathVariable Long filestoreId,
      @RequestBody @Valid ApiGalleryFilestoreTransferRequest request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    assertFilestoresApiEnabled(user);
    throwBindExceptionIfErrors(errors);
    return filestoreWriteManager.transferBetweenFilestores(filestoreId, request, errors, user);
  }
}
