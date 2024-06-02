package com.researchspace.files.service;

import com.researchspace.model.FileProperty;
import com.researchspace.service.FileDuplicateStrategy;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/** Top-level interface for saving remote files */
public interface ExternalFileService {

  void save(
      ExternalFileStoreWithCredentials extFileStore,
      FileProperty fileProperty,
      File sourceFile,
      FileDuplicateStrategy behaviourOnDuplicate);

  boolean exists(ExternalFileStoreWithCredentials exFS, FileProperty fileProperty);

  public static final ExternalFileService NOOP =
      new ExternalFileService() {

        @Override
        public void save(
            ExternalFileStoreWithCredentials extFileStore,
            FileProperty fileProperty,
            File sourceFile,
            FileDuplicateStrategy behaviourOnDuplicate) {}

        @Override
        public boolean exists(ExternalFileStoreWithCredentials exFS, FileProperty fileProperty) {
          return false;
        }

        @Override
        public FileInputStream getFileStream(
            ExternalFileStoreWithCredentials exFS, FileProperty fileProperty) {
          return null;
        }

        @Override
        public File getFile(ExternalFileStoreWithCredentials exFS, FileProperty fileProperty) {
          return null;
        }
      };

  /**
   * Gets a FileInputStream. Implementations can either provide a stream direct from external
   * resource or download to a local file first. <br>
   * Clients should close the input stream after use.
   *
   * @param exFS
   * @param fileProperty
   * @return A FileInputStream on the downloaded file
   * @throws FileNotFoundException if file couldn't be found
   */
  FileInputStream getFileStream(ExternalFileStoreWithCredentials exFS, FileProperty fileProperty)
      throws FileNotFoundException;

  /**
   * Downloads external file to local system and returns it.
   *
   * @param exFS
   * @param fileProperty
   * @return A File of the downloaded file
   */
  File getFile(ExternalFileStoreWithCredentials exFS, FileProperty fileProperty);
}
