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
import com.researchspace.netfiles.ApiNfsCredentials;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFactory;
import com.researchspace.netfiles.WritableNfsClient;
import com.researchspace.netfiles.WriteAttribution;
import com.researchspace.netfiles.s3.S3NfsClient;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.ExternalStorageManager;
import com.researchspace.service.FilestoreWriteManager;
import com.researchspace.service.NfsManager;
import java.io.IOException;
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

  @Override
  public UploadOutcome uploadToFilestore(
      Long filestoreId,
      ApiGalleryFilestoreOperationRequest request,
      BindingResult errors,
      User user,
      String operation)
      throws BindException {

    Set<Long> recordIds = request.getRecordIds();
    NfsFileStore filestore = validateInputAndGetFilestore(recordIds, filestoreId, errors);
    WritableNfsClient writableClient =
        resolveWritableClient(user, filestore, request.getCredentials(), errors);
    String absolutePath = resolveAbsoluteFilestorePath(filestore);

    Map<Long, EcatMediaFile> mediaFileMapById =
        retrieveMediaFiles(recordIds, user, errors).stream()
            .collect(Collectors.toMap(EcatMediaFile::getId, m -> m));

    // S3 (and any other backend that overrides the attribution-aware batch) gets per-object audit
    // metadata; backends that ignore attribution just see the legacy upload path.
    WriteAttribution attribution = new WriteAttribution(user.getUsername(), operation);

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

    WritableNfsClient sourceClient = resolveWritableClient(user, sourceFilestore, null, errors);
    WritableNfsClient destClient = resolveWritableClient(user, destFilestore, null, errors);

    // /transfer has no RSpace record context, so the result entry's recordId is null.
    WriteAttribution attribution = new WriteAttribution(user.getUsername(), OPERATION_TRANSFER);
    ApiExternalStorageOperationResult result = new ApiExternalStorageOperationResult();
    try {
      if (sourceClient instanceof S3NfsClient) {
        ((S3NfsClient) sourceClient)
            .copyObject(
                request.getSourcePath(),
                destClient,
                request.getDestPath(),
                attribution.metadataForRecord(null));
      } else {
        sourceClient.copyObject(request.getSourcePath(), destClient, request.getDestPath());
      }
      if (request.isDeleteSource()) {
        sourceClient.deleteFile(request.getSourcePath());
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
        filesRetrieved.add(baseRecordManager.retrieveMediaFile(user, recordId));
      } catch (ObjectRetrievalFailureException ex) {
        errors.addError(new ObjectError("recordIds", ex.getMessage()));
      } finally {
        throwBindExceptionIfErrors(errors);
      }
    }
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
