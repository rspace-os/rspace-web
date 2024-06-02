package com.researchspace.files.service;

import com.researchspace.model.FileProperty;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.FileDuplicateStrategy;
import java.io.File;

/** Top-level interface for RSpace to interact with external file services. */
public interface ExternalFileStore {

  /**
   * Saves a file to external file store
   *
   * @param fileProperty
   * @param sourceFile
   * @param behaviourOnDuplicate
   * @return An ExtFileOperationStatus, with, if successful, and identifier of the newly created
   *     external file resource.
   */
  public ExtFileOperationStatus<ExternalFileId> save(
      FileProperty fileProperty,
      File sourceFile,
      FileDuplicateStrategy behaviourOnDuplicate,
      UserConnection userConnection);

  /**
   * Gets top-level folder that is at the root of rspace files
   *
   * @return
   */
  public String getFileStoreRoot();

  /**
   * Boolean test as to whether the file with path defined in the FileProperty exists on remote File
   * store.
   *
   * @param fileProperty
   * @param userConnection
   * @return <code>true</code> if exists, <code>false</code> otherwise.
   */
  boolean exists(FileProperty fileProperty, UserConnection userConnection);

  /**
   * Retreives file from external service
   *
   * @param fileProperty
   * @param userConnection
   * @return
   */
  ExtFileOperationStatus<File> downloadFile(
      FileProperty fileProperty, UserConnection userConnection);
}
