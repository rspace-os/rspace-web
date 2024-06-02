package com.researchspace.files.service.egnyte;

import com.researchspace.egnyte.api.clients.requests.SimpleFileUploadRequest;
import com.researchspace.egnyte.api.model.FileDownloadResult;
import com.researchspace.egnyte.api.model.FileUploadResult;
import com.researchspace.egnyte.api2.EgnyteResult;
import com.researchspace.files.service.ExtFileOperationStatus;
import com.researchspace.files.service.ExternalFileId;
import com.researchspace.files.service.ExternalFileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.FileDuplicateStrategy;
import java.io.File;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** Adapts Egnyte API class to work with RSpace ExternalFileStore interface */
@Slf4j
@Getter()
public class EgnyteFileStoreAdapter extends AbstractEgnyteAdapter implements ExternalFileStore {

  public EgnyteFileStoreAdapter(String fileStoreBaseUrl, String fileStoreRoot) {
    super(fileStoreBaseUrl, fileStoreRoot);
  }

  @Override
  public ExtFileOperationStatus<ExternalFileId> save(
      FileProperty fileProperty,
      File sourceFile,
      FileDuplicateStrategy behaviourOnDuplicate,
      UserConnection userConnection) {

    log.info(
        " Will save file '{}' for user '{}' to baseURL '{}'",
        fileProperty.getFileName(),
        userConnection.getId().getUserId(),
        fileStoreBaseUrl);
    try {
      SimpleFileUploadRequest req =
          new SimpleFileUploadRequest(createFilePath(fileProperty, true), sourceFile);
      EgnyteResult<FileUploadResult> fileUploadResult =
          egnyteApi.uploadFile(createToken(userConnection), req);
      if (!fileUploadResult.isSuccessful()) {
        return new ExtFileOperationStatus<ExternalFileId>(
            fileUploadResult.getStatusCode().value(),
            fileUploadResult.getError().getMessage(),
            null);
      }
      log.info("Saved file {}", fileUploadResult.getResult().getEntryId());
      return new ExtFileOperationStatus<ExternalFileId>(
          fileUploadResult.getStatusCode().value(),
          null,
          new ExternalFileId(fileUploadResult.getResult().getEntryId()));

    } catch (Exception e) {
      log.warn(e.getMessage());
      return new ExtFileOperationStatus<ExternalFileId>(500, e.getMessage(), null);
    }
  }

  @Override
  public boolean exists(FileProperty fileProperty, UserConnection userConnection) {
    return egnyteApi
        .listFolder(createToken(userConnection), createFilePath(fileProperty, true))
        .isSuccessful();
  }

  @Override
  public ExtFileOperationStatus<File> downloadFile(
      FileProperty fileProperty, UserConnection userConnection) {

    EgnyteResult<FileDownloadResult> fileDownloadResult =
        egnyteApi.downloadFile(createToken(userConnection), createFilePath(fileProperty, true));
    if (!fileDownloadResult.isSuccessful()) {
      log.error(
          "Couldn't download file {}, status code is {}",
          fileProperty.getRelPath(),
          fileDownloadResult.getStatusCode());
      return new ExtFileOperationStatus<File>(
          fileDownloadResult.getStatusCode().value(),
          fileDownloadResult.getError().getMessage(),
          null);
    }
    log.info("Downloaded file {}", fileDownloadResult.getResult().getDownloaded().getName());
    return new ExtFileOperationStatus<File>(
        fileDownloadResult.getStatusCode().value(),
        null,
        fileDownloadResult.getResult().getDownloaded());
  }
}
