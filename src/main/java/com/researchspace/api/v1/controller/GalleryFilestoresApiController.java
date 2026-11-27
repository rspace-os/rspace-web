package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.GalleryFilestoresApi;
import com.researchspace.api.v1.model.ApiExternalStorageOperationInfo;
import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.api.v1.model.ApiGalleryFilestoreAuditInfo;
import com.researchspace.api.v1.model.ApiGalleryFilestoreDeleteRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreFolderRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreLinkedDocument;
import com.researchspace.api.v1.model.ApiGalleryFilestoreMoveRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreOperationRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreSidecarFile;
import com.researchspace.api.v1.model.ApiGalleryFilestoreSidecarFileRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreTransferRequest;
import com.researchspace.model.DeploymentPropertyType;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
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
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.service.FilestoreWriteManager;
import com.researchspace.service.NfsFieldLinkManager;
import com.researchspace.service.NfsFileHandler;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.service.inventory.FilestoreLinkingItem;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.service.metadata.GeneratedSidecarFile;
import com.researchspace.service.metadata.S3SidecarFileService;
import com.researchspace.webapp.controller.DeploymentProperty;
import com.researchspace.webapp.controller.ResponseHeaders;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.ws.rs.NotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
  @Autowired S3SidecarFileService s3SidecarFileService;
  @Autowired NfsFieldLinkManager nfsFieldLinkManager;
  @Autowired InventoryLinkManager inventoryLinkManager;

  @Value("${gallery.actions.metadata.sidecarFile.enabled}")
  boolean metadataSidecarFileEnabled;

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

  @Override
  public ApiGalleryFilestoreAuditInfo getFilestoreItemMetadata(
      @PathVariable Long filestoreId,
      @RequestParam("remotePath") String remotePath,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    assertFilestoresApiEnabled(user);
    return new ApiGalleryFilestoreAuditInfo(
        filestoreWriteManager.getAuditMetadata(filestoreId, remotePath, user));
  }

  @Override
  public List<ApiGalleryFilestoreLinkedDocument> getFilestoreItemLinkedDocuments(
      @PathVariable Long filestoreId,
      @RequestParam("remotePath") String remotePath,
      @RequestAttribute(name = "user") User user) {

    assertFilestoresApiEnabled(user);
    NfsFileStore filestore = nfsManager.getNfsFileStore(filestoreId);
    // a path without its leading slash would run into a base not ending with one; a path under
    // any other base is taken as sent, as a link stores it, and a doubled slash names another key
    String base = filestore == null ? "" : NfsFileStore.validateTargetPath(filestore.getPath());
    String path =
        base.isEmpty() || base.endsWith("/")
            ? remotePath
            : StringUtils.prependIfMissing(remotePath, "/");
    assertItemReadableWithOwnLogin(user, filestore, path);
    // first, as it refuses an unknown filestore and asserts read access
    var documents = nfsFieldLinkManager.getLinkedDocuments(filestoreId, path, user);
    var items =
        inventoryLinkManager.findItemsLinkingFilestorePaths(
            filestore.getFileSystem(), List.of(filestore.getAbsolutePath(path)), user);
    // the item rows come once per relation, which only the metadata sidecar uses
    Set<String> listedItems = new HashSet<>();
    return Stream.concat(
            documents.stream().map(ApiGalleryFilestoreLinkedDocument::new),
            items.stream()
                .filter(item -> !item.isReadable() || listedItems.add(item.globalId()))
                .map(GalleryFilestoresApiController::toLinkedDocument))
        .toList();
  }

  private static ApiGalleryFilestoreLinkedDocument toLinkedDocument(FilestoreLinkingItem item) {
    ApiGalleryFilestoreLinkedDocument row = new ApiGalleryFilestoreLinkedDocument();
    row.setId(item.isReadable() ? new GlobalIdentifier(item.globalId()).getDbId() : null);
    row.setGlobalId(item.globalId());
    row.setName(item.name());
    row.setOwnerFullName(item.ownerFullName());
    row.setType(item.type());
    return row;
  }

  /**
   * On filesystems with per-user logins the caller's own login must read the item: a folder must
   * browse, a file must open. Listing the parent tells which it is, since some backends (SFTP) list
   * a file the caller cannot open. Filesystems without per-user logins are gated by their
   * allowlists alone, and an unknown filestore is left to the lookup's 404.
   */
  private void assertItemReadableWithOwnLogin(
      User user, NfsFileStore filestore, String remotePath) {
    if (filestore == null
        || NfsAuthenticationType.NONE.equals(filestore.getFileSystem().getAuthType())) {
      return;
    }
    NfsClient client = getNfsClientForUserAndFilesystem(user, filestore.getFileSystem());
    String path = StringUtils.removeEnd(filestore.getAbsolutePath(remotePath), "/");
    try {
      if (StringUtils.strip(remotePath, "/").isEmpty() || isFolderIn(client, path)) {
        // the folder query answers on SMB even when listing is denied; browsing fails instead
        if (client.createFileTree(path, null, filestore) != null) {
          return;
        }
      } else {
        NfsFileDetails file = client.queryNfsFileForDownload(new NfsTarget(path, null));
        // closing the stream alone leaves an SMB file handle open
        try (InputStream opened = file.getRemoteInputStream()) {
          if (opened != null) {
            return;
          }
        } finally {
          client.releaseNfsFileAfterDownload(file);
        }
      }
    } catch (IOException | RuntimeException e) {
      log.warn(
          "Filestore {} item {} not readable for {}: {}",
          filestore.getId(),
          remotePath,
          user.getUsername(),
          e.getMessage());
    } finally {
      // the credentials store logs in afresh for each request
      client.closeSession();
    }
    throw new NotFoundException(getMessage("netFileStores.export.resourceNotAccessible"));
  }

  /**
   * Whether {@code path} is listed as a folder in its parent; throws if the parent cannot be
   * listed.
   */
  private static boolean isFolderIn(NfsClient client, String path) throws IOException {
    int slash = path.lastIndexOf('/');
    String parentPath = slash < 0 ? "" : slash == 0 ? "/" : path.substring(0, slash);
    NfsFolderDetails parent = client.queryForNfsFolder(new NfsTarget(parentPath, null));
    String name = path.substring(slash + 1);
    // an item its parent does not list is opened as a file: SMBJ glues the folder a server URL
    // names after the share to a path without a slash, so that file is not in the listed folder
    return parent.getContent().stream()
        // iRODS names a listed collection by its absolute path
        .filter(item -> name.equals(StringUtils.substringAfterLast("/" + item.getName(), "/")))
        .anyMatch(NfsFolderDetails.class::isInstance);
  }

  /**
   * Returns an NfsClient for the given user and filesystem after asserting the user has read
   * access.
   */
  private NfsClient getNfsClientForUserAndFilesystem(User user, NfsFileSystem filesystem) {
    aclChecker.assertCanRead(user, filesystem);
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
      throw new IllegalArgumentException(getMessage("netFileStores.errors.missingRemotePathOrId"));
    }

    NfsFileStore filestore = nfsManager.getNfsFileStore(filestoreId);
    NfsFileSystem filesystem = filestore.getFileSystem();
    NfsClient nfsClient = getNfsClientForUserAndFilesystem(user, filesystem);

    String fullPath = filestore.getAbsolutePath(remotePath);
    NfsFileDetails nfsFileDetails =
        nfsFileHandler.downloadNfsFileToRSpace(new NfsTarget(fullPath, remoteId), nfsClient);
    File downloadedFile = nfsFileDetails.getLocalFile();
    log.info("downloaded to: " + downloadedFile.getCanonicalPath());

    ResponseHeaders.setContentTypeAndPreventSniffing(response, "application/octet-stream");
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
    NfsFileSystem filesystem = nfsManager.getFileSystem(filesystemId);
    aclChecker.assertCanRead(user, filesystem);
    boolean filestoreNameUnique = nfsManager.verifyFileStoreNameUniqueForUser(filestoreName, user);
    if (!filestoreNameUnique) {
      throw new IllegalArgumentException(
          getMessage("netFileStores.errors.filestoreNameNotUnique", new Object[] {filestoreName}));
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
    return nfsManager.getActiveFileSystemInfos(user);
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
    aclChecker.assertCanRead(user, filesystem);
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
  public ApiExternalStorageOperationResult uploadFromGallery(
      @PathVariable Long filestoreId,
      @RequestBody @Valid ApiGalleryFilestoreOperationRequest request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    assertFilestoresApiEnabled(user);
    throwBindExceptionIfErrors(errors);
    FilestoreWriteManager.UploadOutcome outcome =
        filestoreWriteManager.uploadToFilestore(filestoreId, request, errors, user);

    if (!request.isRemoveOriginalFromRspace()) {
      // copy: keep the RSpace originals
      return outcome.getOperationResult();
    }

    // move: delete the RSpace originals that uploaded successfully
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
  public ApiExternalStorageOperationResult moveWithinFilestore(
      @PathVariable Long filestoreId,
      @RequestBody @Valid ApiGalleryFilestoreMoveRequest request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    assertFilestoresApiEnabled(user);
    throwBindExceptionIfErrors(errors);
    String movedPath =
        filestoreWriteManager.moveWithinFilestore(
            filestoreId, request.getSourcePath(), request.getDestPath(), errors, user);
    return singleSuccess(movedPath);
  }

  @Override
  public ApiExternalStorageOperationResult createFolder(
      @PathVariable Long filestoreId,
      @RequestBody @Valid ApiGalleryFilestoreFolderRequest request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    assertFilestoresApiEnabled(user);
    throwBindExceptionIfErrors(errors);
    String folderPath =
        filestoreWriteManager.createFolderInFilestore(
            filestoreId, request.getPath(), request.getName(), errors, user);
    return singleSuccess(folderPath);
  }

  @Override
  public void deleteFromFilestore(
      @PathVariable Long filestoreId,
      @RequestBody @Valid ApiGalleryFilestoreDeleteRequest request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    assertFilestoresApiEnabled(user);
    throwBindExceptionIfErrors(errors);
    filestoreWriteManager.deleteFromFilestore(filestoreId, request.getPath(), errors, user);
  }

  private static ApiExternalStorageOperationResult singleSuccess(String path) {
    ApiExternalStorageOperationResult result = new ApiExternalStorageOperationResult();
    result.add(new ApiExternalStorageOperationInfo(null, null, path, true, null));
    return result;
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

  @Override
  public ApiGalleryFilestoreSidecarFile previewSidecarFile(
      @PathVariable Long filestoreId,
      @RequestBody @Valid ApiGalleryFilestoreSidecarFileRequest request,
      @RequestAttribute(name = "user") User user) {

    assertFilestoresApiEnabled(user);
    assertSidecarFileEnabled();
    return toApiSidecarFile(s3SidecarFileService.preview(filestoreId, request.getPath(), user));
  }

  @Override
  public ApiGalleryFilestoreSidecarFile saveSidecarFile(
      @PathVariable Long filestoreId,
      @RequestBody @Valid ApiGalleryFilestoreSidecarFileRequest request,
      @RequestAttribute(name = "user") User user) {

    assertFilestoresApiEnabled(user);
    assertSidecarFileEnabled();
    return toApiSidecarFile(s3SidecarFileService.save(filestoreId, request.getPath(), user));
  }

  // Honour the feature flag on the backend too, so a disabled sidecar feature is off end to end.
  private void assertSidecarFileEnabled() {
    if (!metadataSidecarFileEnabled) {
      throw new UnsupportedOperationException(
          getMessage("netFileStores.sidecarFile.errors.notEnabled"));
    }
  }

  private static ApiGalleryFilestoreSidecarFile toApiSidecarFile(GeneratedSidecarFile sidecarFile) {
    return new ApiGalleryFilestoreSidecarFile(sidecarFile.getFilename(), sidecarFile.getContent());
  }
}
