package com.researchspace.service.impl;

import com.researchspace.api.v1.controller.GalleryFilestoresCredentialsStore;
import com.researchspace.api.v1.model.ApiExternalStorageOperationInfo;
import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.api.v1.model.ApiGalleryFilestoreOperationRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreTransferRequest;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.ExternalStorageLocation;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemOption;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.netfiles.ApiNfsCredentials;
import com.researchspace.netfiles.DeletableTarget;
import com.researchspace.netfiles.FilestoreAuditMetadata;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFactory;
import com.researchspace.netfiles.WritableNfsClient;
import com.researchspace.netfiles.WriteAttribution;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.ExternalStorageManager;
import com.researchspace.service.FilestoreAclChecker;
import com.researchspace.service.FilestoreOperationForbiddenException;
import com.researchspace.service.FilestoreWriteManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.NfsManager;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

@Slf4j
@Service("filestoreWriteManager")
public class FilestoreWriteManagerImpl implements FilestoreWriteManager {

  @Autowired @Setter private NfsManager nfsManager;
  @Autowired @Setter private NfsFactory nfsFactory;
  @Autowired @Setter private BaseRecordManager baseRecordManager;
  @Autowired @Setter private ExternalStorageManager externalStorageManager;
  @Autowired @Setter private GalleryFilestoresCredentialsStore credentialsStore;
  @Autowired @Setter private FilestoreAclChecker aclChecker;

  @Autowired @Setter private IPropertyHolder properties;
  @Autowired @Setter private MessageSourceUtils messages;

  /** Source of the {@code rspace-created-at} write timestamp; overridable in tests. */
  @Setter private Clock clock = Clock.systemUTC();

  @Override
  public UploadOutcome uploadToFilestore(
      Long filestoreId,
      ApiGalleryFilestoreOperationRequest request,
      BindingResult errors,
      User user)
      throws BindException {

    Set<Long> recordIds = request.getRecordIds();
    NfsFileStore filestore = validateInputAndGetFilestore(recordIds, filestoreId, errors);
    aclChecker.assertCanWrite(user, filestore.getFileSystem());
    WritableNfsClient writableClient =
        resolveWritableClient(user, filestore, request.getCredentials(), errors);
    String absolutePath = resolveAbsoluteFilestorePath(filestore);

    Map<Long, EcatMediaFile> mediaFileMapById =
        retrieveMediaFiles(recordIds, user, errors).stream()
            .collect(Collectors.toMap(EcatMediaFile::getId, m -> m));

    // S3 (and any other backend that overrides the attribution-aware batch) gets per-object audit
    // metadata; backends that ignore attribution just see the legacy upload path.
    Map<Long, String> recordNames =
        mediaFileMapById.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName()));
    WriteAttribution attribution =
        new WriteAttribution(user.getUsername(), recordNames, Instant.now(clock));

    ApiExternalStorageOperationResult operationResult = new ApiExternalStorageOperationResult();
    Set<EcatMediaFile> succeededMediaFiles = new LinkedHashSet<>();
    try {
      operationResult =
          nfsManager.uploadFilesToNfs(
              mediaFileMapById.values(), absolutePath, writableClient, attribution);
      for (ApiExternalStorageOperationInfo info : operationResult.getFileInfoDetails()) {
        if (!Boolean.TRUE.equals(info.getSucceeded())) {
          continue;
        }
        EcatMediaFile mediaFile = mediaFileMapById.get(info.getRecordId());
        if (mediaFile == null) {
          continue;
        }
        succeededMediaFiles.add(mediaFile);

        if (info.getExternalStorageId() != null) {
          ExternalStorageLocation loc = new ExternalStorageLocation();
          loc.setFileStore(filestore);
          loc.setOperationUser(user);
          loc.setExternalStorageId(info.getExternalStorageId());
          loc.setConnectedMediaFile(mediaFile);
          externalStorageManager.saveExternalStorageLocation(loc);
        }
      }
    } catch (Exception e) {
      log.error("Error uploading files to filestore: ", e);
      errors.addError(new ObjectError("nfsClient", e.getMessage()));
      throwBindExceptionIfErrors(errors);
    }
    return new UploadOutcome(operationResult, succeededMediaFiles);
  }

  @Override
  public ApiExternalStorageOperationResult transferBetweenFilestores(
      Long sourceFilestoreId,
      ApiGalleryFilestoreTransferRequest request,
      BindingResult errors,
      User user)
      throws BindException {

    // request-level field validation comes from @Valid on the controller (jakarta bean validation
    // annotations on the DTO); just propagate any errors Spring already populated.
    throwBindExceptionIfErrors(errors);

    if (sourceFilestoreId.equals(request.getDestFilestoreId())) {
      throw new UnsupportedOperationException(
          "Within-filestore transfers are not supported by this endpoint "
              + "(source and destination filestore ids are identical)");
    }

    NfsFileStore sourceFilestore = nfsManager.getNfsFileStore(sourceFilestoreId);
    NfsFileStore destFilestore = nfsManager.getNfsFileStore(request.getDestFilestoreId());
    if (sourceFilestore == null) {
      errors.addError(
          new ObjectError(
              "sourceFilestore", "Could not find file store with id: " + sourceFilestoreId));
    }
    if (destFilestore == null) {
      errors.addError(
          new ObjectError(
              "destFilestore",
              "Could not find file store with id: " + request.getDestFilestoreId()));
    }
    throwBindExceptionIfErrors(errors);

    // the transfer reads the source and writes the destination; the source is only modified
    // when deleteSource is set, so it needs read access unless we are deleting from it.
    if (request.isDeleteSource()) {
      aclChecker.assertCanWrite(user, sourceFilestore.getFileSystem());
    } else {
      aclChecker.assertCanRead(user, sourceFilestore.getFileSystem());
    }
    aclChecker.assertCanWrite(user, destFilestore.getFileSystem());

    WritableNfsClient sourceClient = resolveWritableClient(user, sourceFilestore, null, errors);
    WritableNfsClient destClient = resolveWritableClient(user, destFilestore, null, errors);

    if (!sourceClient.supportsServerSideTransfer() || !destClient.supportsServerSideTransfer()) {
      throw new UnsupportedOperationException(
          "Filestore-to-filestore transfer currently supports only S3↔S3; "
              + "source and destination filestores must both be S3");
    }

    // /transfer has no RSpace record context, so recordNames and recordId are both null.
    WriteAttribution attribution =
        new WriteAttribution(user.getUsername(), null, Instant.now(clock));
    // The request paths are relative to each filestore's configured root (logicPath strips the
    // filestore path prefix). Prepend the filestore root to recover the absolute S3 key.
    String sourceFilestoreRootPath = resolveAbsoluteFilestorePath(sourceFilestore);
    String destFilestoreRootPath = resolveAbsoluteFilestorePath(destFilestore);
    String absoluteSourcePath =
        StringUtils.isBlank(sourceFilestoreRootPath)
            ? request.getSourcePath()
            : sourceFilestoreRootPath + "/" + StringUtils.stripStart(request.getSourcePath(), "/");
    String absoluteDestPath =
        StringUtils.isBlank(destFilestoreRootPath)
            ? request.getDestPath()
            : destFilestoreRootPath + "/" + StringUtils.stripStart(request.getDestPath(), "/");
    if (absoluteSourcePath.equals(absoluteDestPath)) {
      throw new UnsupportedOperationException(
          "Source and destination resolve to the same S3 key: " + absoluteSourcePath);
    }
    ApiExternalStorageOperationResult result = new ApiExternalStorageOperationResult();
    try {
      sourceClient.copyObject(
          absoluteSourcePath, destClient, absoluteDestPath, attribution.metadataForRecord(null));
      if (request.isDeleteSource()) {
        sourceClient.deleteFile(absoluteSourcePath);
      }
      result.add(
          new ApiExternalStorageOperationInfo(null, null, request.getSourcePath(), true, null));
    } catch (IOException e) {
      log.error("Error transferring object between filestores: ", e);
      result.add(
          new ApiExternalStorageOperationInfo(
              null, request.getSourcePath(), false, e.getMessage()));
    }
    return result;
  }

  @Override
  public void deleteFromFilestore(Long filestoreId, String path, BindingResult errors, User user)
      throws BindException {
    NfsFileStore filestore = getFilestoreOrThrow(filestoreId, errors);
    assertNotFilestoreRoot(path, "path", errors);
    aclChecker.assertCanWrite(user, filestore.getFileSystem());
    WritableNfsClient client = resolveWritableClient(user, filestore, null, errors);
    String absolutePath = resolveAbsolute(filestore, path);

    try {
      DeletableTarget target = client.resolveDeletableTarget(absolutePath);
      assertDeletable(target.audit(), user);
      client.deleteByKey(target.objectKey());
    } catch (IOException e) {
      log.error("Error deleting object from filestore: ", e);
      errors.addError(new ObjectError("path", e.getMessage()));
      throw new BindException(errors);
    }
  }

  /**
   * Enforces the delete gate on the target object (a file, or an empty folder's placeholder): it
   * must be created by {@code user} and still within the configured deletion window, else throws
   * {@link FilestoreOperationForbiddenException} (→ 403) before anything is deleted.
   */
  private void assertDeletable(FilestoreAuditMetadata audit, User user) {
    if (!audit.isCreatedBy(user.getUsername())) {
      throw new FilestoreOperationForbiddenException(
          messages.getMessage("netfilestores.s3.delete.denied.notCreator"));
    }
    if (!audit.hasTimestamp()) {
      throw new FilestoreOperationForbiddenException(
          messages.getMessage("netfilestores.s3.delete.denied.noMetadata"));
    }
    int windowMinutes = properties.getS3DeleteWindowMinutes();
    if (!audit.isWithin(Instant.now(clock).minus(Duration.ofMinutes(windowMinutes)))) {
      throw new FilestoreOperationForbiddenException(
          messages.getMessage(
              "netfilestores.s3.delete.denied.tooOld", new Object[] {windowMinutes}));
    }
  }

  @Override
  public String createFolderInFilestore(
      Long filestoreId, String parentPath, String folderName, BindingResult errors, User user)
      throws BindException {
    if (StringUtils.isBlank(folderName)) {
      errors.addError(new ObjectError("folderName", "folderName is mandatory"));
    }
    throwBindExceptionIfErrors(errors);
    NfsFileStore filestore = getFilestoreOrThrow(filestoreId, errors);
    aclChecker.assertCanWrite(user, filestore.getFileSystem());
    WritableNfsClient client = resolveWritableClient(user, filestore, null, errors);

    String absoluteParent = resolveAbsolute(filestore, parentPath);
    String absoluteFolder =
        StringUtils.isBlank(absoluteParent) ? folderName : absoluteParent + "/" + folderName;
    WriteAttribution attribution =
        new WriteAttribution(user.getUsername(), null, Instant.now(clock));
    try {
      client.createFolder(absoluteFolder, attribution.metadataForRecord(null));
    } catch (IOException e) {
      log.error("Error creating folder in filestore: ", e);
      errors.addError(new ObjectError("folderName", e.getMessage()));
      throw new BindException(errors);
    }
    return toFilestoreRelative(filestore, absoluteFolder);
  }

  @Override
  public String moveWithinFilestore(
      Long filestoreId, String sourcePath, String destFolderPath, BindingResult errors, User user)
      throws BindException {
    NfsFileStore filestore = getFilestoreOrThrow(filestoreId, errors);
    assertNotFilestoreRoot(sourcePath, "sourcePath", errors);
    aclChecker.assertCanWrite(user, filestore.getFileSystem());
    WritableNfsClient client = resolveWritableClient(user, filestore, null, errors);

    String absoluteSource = resolveAbsolute(filestore, sourcePath);
    String absoluteDestFolder = resolveAbsolute(filestore, destFolderPath);
    try {
      return toFilestoreRelative(filestore, client.moveWithin(absoluteSource, absoluteDestFolder));
    } catch (IOException e) {
      log.error("Error moving object within filestore: ", e);
      errors.addError(new ObjectError("sourcePath", e.getMessage()));
      throw new BindException(errors);
    }
  }

  /** Strips the filestore's configured root prefix, so returned paths are filestore-relative. */
  private String toFilestoreRelative(NfsFileStore filestore, String absolutePath) {
    String root = resolveAbsoluteFilestorePath(filestore);
    if (StringUtils.isBlank(root)) {
      return absolutePath;
    }
    return StringUtils.stripStart(StringUtils.removeStart(absolutePath, root), "/");
  }

  /**
   * Rejects operations whose target path resolves to the filestore root itself (e.g. {@code ""} or
   * {@code "/"}); the root cannot be moved or deleted.
   */
  private void assertNotFilestoreRoot(String relativePath, String field, BindingResult errors)
      throws BindException {
    if (StringUtils.isBlank(StringUtils.strip(relativePath, "/"))) {
      errors.addError(new ObjectError(field, "Cannot move or delete the filestore root"));
      throw new BindException(errors);
    }
  }

  private NfsFileStore getFilestoreOrThrow(Long filestoreId, BindingResult errors)
      throws BindException {
    NfsFileStore filestore = nfsManager.getNfsFileStore(filestoreId);
    if (filestore == null) {
      errors.addError(
          new ObjectError("nfsFileStore", "Could not find file store with id: " + filestoreId));
      throw new BindException(errors);
    }
    return filestore;
  }

  /** Prepends the filestore's configured root to a filestore-relative path. */
  private String resolveAbsolute(NfsFileStore filestore, String relativePath) {
    String root = resolveAbsoluteFilestorePath(filestore);
    String rel = relativePath == null ? "" : StringUtils.stripStart(relativePath, "/");
    if (StringUtils.isBlank(root)) {
      return rel;
    }
    // When rel is empty (the filestore root), return root as-is. Appending "/" + rel here would
    // leave a trailing slash, and a caller that then joins a leaf with another "/" (e.g.
    // createFolderInFilestore) would produce a "root//leaf" double slash -> an empty-named S3
    // folder.
    if (StringUtils.isBlank(rel)) {
      return root;
    }
    return root + "/" + rel;
  }

  private NfsFileStore validateInputAndGetFilestore(
      Set<Long> recordIds, Long filestorePathId, BindingResult errors) throws BindException {
    if (CollectionUtils.isEmpty(recordIds)) {
      errors.addError(new ObjectError("recordIds", "recordIds is mandatory"));
    }
    if (filestorePathId == null) {
      errors.addError(new ObjectError("filestorePathId", "filestorePathId is mandatory"));
    }
    throwBindExceptionIfErrors(errors);
    NfsFileStore filestore = nfsManager.getNfsFileStore(filestorePathId);
    if (filestore == null) {
      errors.addError(
          new ObjectError("nfsFileStore", "Could not find file store with id: " + filestorePathId));
    }
    throwBindExceptionIfErrors(errors);
    return filestore;
  }

  private Set<EcatMediaFile> retrieveMediaFiles(
      Set<Long> recordIds, User user, BindingResult errors) throws BindException {
    Set<EcatMediaFile> filesRetrieved = new LinkedHashSet<>();
    for (Long recordId : recordIds) {
      try {
        BaseRecord record = baseRecordManager.get(recordId, user);
        if (record.isFolder()) {
          errors.addError(
              new ObjectError(
                  "recordIds",
                  new String[] {"gallery.filestore.folder.upload.rejected"},
                  null,
                  "Cannot move folders to filestores."));
        } else if (record.isMediaRecord()) {
          // baseRecordManager.get() bypasses READ permission for record IDs; re-fetch via
          // retrieveMediaFile() which asserts the permission before returning the file.
          filesRetrieved.add(baseRecordManager.retrieveMediaFile(user, recordId));
        } else {
          errors.addError(
              new ObjectError(
                  "recordIds",
                  new String[] {"gallery.filestore.not.media.file"},
                  null,
                  "Only media files can be moved to filestores."));
        }
      } catch (ObjectRetrievalFailureException ex) {
        errors.addError(new ObjectError("recordIds", ex.getMessage()));
      }
    }
    throwBindExceptionIfErrors(errors);
    return filesRetrieved;
  }

  private WritableNfsClient resolveWritableClient(
      User user, NfsFileStore filestore, ApiNfsCredentials credentials, BindingResult errors)
      throws BindException {
    NfsFileSystem fileSystem = filestore.getFileSystem();
    NfsClient nfsClient;
    if (NfsAuthenticationType.NONE.equals(fileSystem.getAuthType())) {
      nfsClient = nfsFactory.getNfsClient(user.getUsername(), null, fileSystem);
    } else {
      nfsClient =
          credentialsStore.validateCredentialsAndLoginNfs(credentials, errors, user, fileSystem);
    }
    if (!(nfsClient instanceof WritableNfsClient)) {
      throw new UnsupportedOperationException(
          "Filestore backend does not support write operations: " + filestore.getName());
    }
    return (WritableNfsClient) nfsClient;
  }

  /**
   * Resolves an absolute path on the filestore's underlying filesystem. iRODS filestores require
   * the {@code IRODS_HOME_DIR} prefix to be applied; other backends use the filestore's stored path
   * verbatim.
   */
  private String resolveAbsoluteFilestorePath(NfsFileStore filestore) {
    String filestorePath = filestore.getPath() == null ? "" : filestore.getPath();
    String irodsHome =
        filestore.getFileSystem().getClientOption(NfsFileSystemOption.IRODS_HOME_DIR);
    if (StringUtils.isBlank(irodsHome) || filestorePath.startsWith(irodsHome)) {
      return filestorePath;
    }
    if (StringUtils.endsWith(irodsHome, "/") && StringUtils.startsWith(filestorePath, "/")) {
      return StringUtils.stripEnd(irodsHome, "/") + filestorePath;
    }
    return irodsHome + filestorePath;
  }

  /** Inlined equivalent of {@code BaseApiController.throwBindExceptionIfErrors}. */
  private void throwBindExceptionIfErrors(BindingResult errors) throws BindException {
    if (errors != null && errors.hasErrors()) {
      throw new BindException(errors);
    }
  }
}
