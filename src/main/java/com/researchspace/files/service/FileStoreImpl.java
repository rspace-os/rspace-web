package com.researchspace.files.service;

import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.User;
import com.researchspace.service.FileDuplicateStrategy;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/** The application's {@link FileStore}, backed by the local file store. */
public class FileStoreImpl implements FileStore {

  private final InternalFileStore localFileStore;

  public FileStoreImpl(InternalFileStore localFileStore) {
    this.localFileStore = localFileStore;
  }

  @Override
  public URI save(
      FileProperty fileProperty, File sourceFile, FileDuplicateStrategy behaviourOnDuplicate)
      throws IOException {
    return localFileStore.save(fileProperty, sourceFile, behaviourOnDuplicate);
  }

  @Override
  public URI save(
      FileProperty fileProperty,
      InputStream inStream,
      String fileName,
      FileDuplicateStrategy behaviourOnDuplicate)
      throws IOException {
    return localFileStore.save(fileProperty, inStream, fileName, behaviourOnDuplicate);
  }

  @Override
  public Optional<FileInputStream> retrieve(FileProperty fileProperty) {
    return localFileStore.retrieve(fileProperty);
  }

  @Override
  public File findFile(FileProperty fileProperty) throws IOException {
    return localFileStore.findFile(fileProperty);
  }

  @Override
  public boolean exists(FileProperty fileProperty) throws IOException {
    return localFileStore.exists(fileProperty);
  }

  @Override
  public FileStoreRoot getCurrentFileStoreRoot() {
    return localFileStore.getCurrentFileStoreRoot();
  }

  @Override
  public FileStoreRoot getCurrentLocalFileStoreRoot() {
    return localFileStore.getCurrentFileStoreRoot();
  }

  @Override
  public boolean removeFile(FileProperty fileProperty) {
    return false;
  }

  @Override
  public Optional<Integer> removeUserFilestoreFiles(List<File> filestoreFiles) {
    return localFileStore.removeUserFilestoreFiles(filestoreFiles);
  }

  @Override
  public boolean verifyUserFilestoreFiles(List<File> filestoreFiles) {
    return localFileStore.verifyUserFilestoreFiles(filestoreFiles);
  }

  @Override
  public FileProperty createAndSaveFileProperty(
      String fileCategory, User user, String originalFileName, InputStream inputStream)
      throws IOException {
    return localFileStore.createAndSaveFileProperty(
        fileCategory, user, originalFileName, inputStream);
  }
}
