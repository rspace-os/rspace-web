package com.researchspace.files.service;

import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.FileDuplicateStrategy;
import com.researchspace.service.FileStoreMetaManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/** Possible implementation of doing external call */
@Slf4j
public class ExternalFileServiceImpl implements ExternalFileService {

  private @Autowired InternalFileStore localFileStore;
  private @Autowired CommunicationManager commMgr;
  private @Autowired FileStoreMetaManager fileMetaMgr;

  /** This would be the same sequence regardless of the external file store */
  @Override
  public final void save(
      ExternalFileStoreWithCredentials extFileStore,
      FileProperty fileProperty,
      File sourceFile,
      FileDuplicateStrategy behaviourOnDuplicate) {
    ExtFileOperationStatus<ExternalFileId> status =
        extFileStore
            .getExtFileStore()
            .save(fileProperty, sourceFile, behaviourOnDuplicate, extFileStore.getUserConnection());
    log.info("Response status is {}", status.getHttpCode());
    handleResponseFromExtFS(extFileStore, fileProperty, status);
  }

  @Override
  public boolean exists(ExternalFileStoreWithCredentials exFS, FileProperty fileProperty) {
    return exFS.getExtFileStore().exists(fileProperty, exFS.getUserConnection());
  }

  @Override
  public FileInputStream getFileStream(
      ExternalFileStoreWithCredentials exFS, FileProperty fileProperty)
      throws FileNotFoundException {
    ExtFileOperationStatus<File> status = downloadFile(exFS, fileProperty);
    if (status.isOK()) {
      return new FileInputStream(status.getResponse());
    } else {
      return null;
    }
  }

  @Override
  public File getFile(ExternalFileStoreWithCredentials exFS, FileProperty fileProperty) {
    ExtFileOperationStatus<File> status = downloadFile(exFS, fileProperty);
    if (status.isOK()) {
      return status.getResponse();
    } else {
      return null;
    }
  }

  private ExtFileOperationStatus<File> downloadFile(
      ExternalFileStoreWithCredentials exFS, FileProperty fileProperty) {
    return exFS.getExtFileStore().downloadFile(fileProperty, exFS.getUserConnection());
  }

  private void updateFileStoreMetaDataInDBToPointToExternalFile(
      ExternalFileStoreWithCredentials extFileStore, FileProperty fileProperty) {
    // we have to 1) mark as external, 2 update fileStore root.
    fileProperty.setExternal(true);
    FileStoreRoot extFSR = fileMetaMgr.getCurrentFileStoreRoot(true);
    fileProperty.setRoot(extFSR);
    fileMetaMgr.save(fileProperty);
  }

  private void handleResponseFromExtFS(
      ExternalFileStoreWithCredentials extFileStore,
      FileProperty fileProperty,
      ExtFileOperationStatus<ExternalFileId> status) {
    if (status.isOK()) {
      log.info("Saved ID is {}", status.getResponse());
      localFileStore.removeFile(fileProperty);
      updateFileStoreMetaDataInDBToPointToExternalFile(extFileStore, fileProperty);
    } else if (status.isAuthError()) {
      commMgr.systemNotify(
          NotificationType.PROCESS_FAILED,
          "Saving to external filestore failed. Please reauthenticate.",
          fileProperty.getFileOwner(),
          true);
    } else if (status.isOtherError()) {
      commMgr.systemNotify(
          NotificationType.PROCESS_FAILED,
          "Something went wrong when saving file to external filestore.",
          fileProperty.getFileOwner(),
          true);
    }
  }
}
