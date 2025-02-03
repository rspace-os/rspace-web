package com.researchspace.files.service;

import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.User;
import com.researchspace.service.FileDuplicateStrategy;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * A composite file store that can delegate to an external FS if it is configured in
 * deployment.properties
 */
@Slf4j
public class FileStoreImpl implements FileStore {

  private InternalFileStore localFileStore;
  private ExternalFileStoreLocator externalFileStoreLocator;
  private ExternalFileService externalFileService;

  public FileStoreImpl(
      InternalFileStore localFileStore,
      ExternalFileStoreLocator externalFileStoreLocator,
      ExternalFileService externalFileService) {
    super();
    this.localFileStore = localFileStore;
    this.externalFileStoreLocator = externalFileStoreLocator;
    this.externalFileService = externalFileService;
  }

  @Override
  public URI save(
      FileProperty fileProperty, File sourceFile, FileDuplicateStrategy behaviourOnDuplicate)
      throws IOException {
    //  save to local file storage first
    URI localFile = localFileStore.save(fileProperty, sourceFile, behaviourOnDuplicate);
    // see if we've got external FS setup.
    Optional<ExternalFileStoreWithCredentials> extFileStoreOpt = getExternalFS(fileProperty);
    extFileStoreOpt.map(
        exFS -> doExternalSave(fileProperty, sourceFile, behaviourOnDuplicate, exFS));
    return localFile;
  }

  @Override
  public URI save(
      FileProperty fileProperty,
      InputStream inStream,
      String fileName,
      FileDuplicateStrategy behaviourOnDuplicate)
      throws IOException {
    URI localFile = localFileStore.save(fileProperty, inStream, fileName, behaviourOnDuplicate);
    Optional<ExternalFileStoreWithCredentials> extFileStoreOpt = getExternalFS(fileProperty);
    extFileStoreOpt.map(
        exFS -> doExternalSave(fileProperty, new File(localFile), behaviourOnDuplicate, exFS));
    return localFile;
  }

  @Override
  public Optional<FileInputStream> retrieve(FileProperty fileProperty) {
    FileInputStream fis = null;
    if (!fileProperty.isExternal()) {
      fis = localFileStore.retrieve(fileProperty).orElse(null);
    } else {
      log.info("Retrieving external file:  {}", fileProperty.getRelPath());
      Optional<ExternalFileStoreWithCredentials> extFileStoreOpt = getExternalFS(fileProperty);
      if (extFileStoreOpt.isPresent()) {
        ExternalFileStoreWithCredentials exFS = extFileStoreOpt.get();
        // launch async, this encapsulates file saving,
        try {
          fis = externalFileService.getFileStream(exFS, fileProperty);
        } catch (FileNotFoundException e) {
          log.error("Cannot find external file: ", e);
        }
      }
    }
    return Optional.ofNullable(fis);
  }

  @Override
  public File findFile(FileProperty fileProperty) throws IOException {
    File rc = null;
    if (!fileProperty.isExternal()) {
      rc = localFileStore.findFile(fileProperty);
    } else {
      log.info("Retrieving external file:  {}", fileProperty.getRelPath());
      Optional<ExternalFileStoreWithCredentials> extFileStoreOpt = getExternalFS(fileProperty);
      if (extFileStoreOpt.isPresent()) {
        ExternalFileStoreWithCredentials exFS = extFileStoreOpt.get();
        rc = externalFileService.getFile(exFS, fileProperty);
      }
    }
    return rc;
  }

  @Override
  public boolean exists(FileProperty fileProperty) throws IOException {
    Optional<ExternalFileStoreWithCredentials> extFileStoreOpt = getExternalFS(fileProperty);
    if (extFileStoreOpt.isPresent()) {
      log.info("Checking external FS for existence of {}", fileProperty.getRelPath());
      ExternalFileStoreWithCredentials exFS = extFileStoreOpt.get();
      return externalFileService.exists(exFS, fileProperty);
    } else {
      return localFileStore.exists(fileProperty);
    }
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

  private ExternalFileStoreWithCredentials doExternalSave(
      FileProperty fileProperty,
      File sourceFile,
      FileDuplicateStrategy behaviourOnDuplicate,
      ExternalFileStoreWithCredentials exFS) {
    externalFileService.save(exFS, fileProperty, sourceFile, behaviourOnDuplicate);
    return exFS;
  }

  private Optional<ExternalFileStoreWithCredentials> getExternalFS(FileProperty fileProperty) {
    Optional<ExternalFileStoreWithCredentials> extFileStoreOpt =
        externalFileStoreLocator.getExternalFileStoreForUser(fileProperty.getFileOwner());
    return extFileStoreOpt;
  }

  @Override
  public FileProperty createAndSaveFileProperty(
      String fileCategory, User user, String originalFileName, InputStream inputStream)
      throws IOException {
    return localFileStore.createAndSaveFileProperty(
        fileCategory, user, originalFileName, inputStream);
  }
}
