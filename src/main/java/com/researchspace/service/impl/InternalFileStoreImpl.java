package com.researchspace.service.impl;

import com.researchspace.core.util.EscapeReplacement;
import com.researchspace.core.util.FileOperator;
import com.researchspace.core.util.StringAbbreviationUtils;
import com.researchspace.core.util.UnhandledUTF8FileFilter;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.files.service.InternalFileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.FileDuplicateStrategy;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FileStore implementation for storing files locally on RSpace server */
@Service
@Transactional(rollbackFor = IOException.class)
public class InternalFileStoreImpl implements InternalFileStore {

  public void setBaseDir(File rootDir) throws IOException {
    fileOp.getFoldOp().setFileStoreRootDir(rootDir.getAbsolutePath());
    this.baseDir = fileOp.getFoldOp().getBaseDir();
  }

  FileOperator fileOp;
  private File baseDir;
  Logger log = LoggerFactory.getLogger(InternalFileStoreImpl.class);

  public InternalFileStoreImpl() {
    fileOp = new FileOperator();
    baseDir = fileOp.getFoldOp().getBaseDir();
  }

  private boolean initialised = false;
  private FileStoreRoot currentRootFs;

  @Override
  public FileStoreRoot setupInternalFileStoreRoot() {
    if (!initialised || currentRootFs == null) {
      FileStoreRoot root = fileMetadataDao.findByFileStorePath(baseDir.getAbsolutePath());
      if (root == null) {
        log.info(baseDir.getAbsolutePath() + " isn't in the DB, adding");
        fileMetadataDao.resetCurrentFileStoreRoot(false);
        root = new FileStoreRoot(baseDir.toURI().toString());
        root.setCurrent(true);
        fileMetadataDao.saveFileStoreRoot(root);
      } else {
        log.info(baseDir.getAbsolutePath() + " is in the DB, setting as current root");
        if (!root.isCurrent()) {
          log.info("root is not current filestore, setting as current..");
          fileMetadataDao.resetCurrentFileStoreRoot(false);
          root.setCurrent(true);
          fileMetadataDao.saveFileStoreRoot(root);
        }
      }
      initialised = true;
      currentRootFs = root;
    }
    return currentRootFs;
  }

  @Autowired FileMetadataDao fileMetadataDao;

  public void setFileMetadataDao(FileMetadataDao fileMetadataDao) {
    this.fileMetadataDao = fileMetadataDao;
  }

  // ------------ facade ----------------------------------
  @Override
  public URI save(FileProperty meta, File sourceFile, FileDuplicateStrategy behaviourOnDuplicate)
      throws IOException {
    try (InputStream input = new FileInputStream(sourceFile)) {
      return save(meta, input, parseFileName(sourceFile), behaviourOnDuplicate);
    }
  }

  @Override
  public URI save(
      FileProperty fileProperty,
      InputStream inStream,
      String fnm,
      FileDuplicateStrategy behaviourOnDuplicate)
      throws IOException {
    checkInitialised();
    fnm = EscapeReplacement.replaceChars(fnm); // get ride funny characters
    int suc = addMetadata(fileProperty, fnm, behaviourOnDuplicate);
    if (suc < 0) {
      return null;
    }
    File out = new File(baseDir, fileProperty.getRelPath());
    Path replacement = null;
    try {
      if (suc == 0) {
        // Keep the existing file intact until the replacement has been written successfully.
        replacement = Files.createTempFile(out.toPath().getParent(), ".replacement-", ".tmp");
      }
      long size;
      try (FileOutputStream output =
          new FileOutputStream(replacement == null ? out : replacement.toFile())) {
        size = fileOp.copyStream(output, inStream, 0);
      }
      fileProperty.setFileSize(Long.toString(size));
      fileMetadataDao.save(fileProperty);
      if (replacement != null) {
        Files.move(
            replacement,
            out.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      }
      return out.toURI();
    } catch (IOException | RuntimeException e) {
      if (replacement != null) {
        removeReservedFile(replacement, e);
      }
      if (suc == 100) {
        removeReservedFile(out.toPath(), e);
      }
      log.warn("Could not write file :{}", fileProperty.getRelPath(), e);
      throw e;
    }
  }

  private void checkInitialised() {
    if (!initialised) {
      setupInternalFileStoreRoot();
    }
  }

  @Override
  public Optional<FileInputStream> retrieve(FileProperty meta) {
    checkInitialised();
    FileInputStream fis = null;
    try {
      File retrieved = findFile(meta);
      fis = new FileInputStream(retrieved);

    } catch (FileNotFoundException fnfe) {
      log.warn("Couldn't retrieve file {}", meta.getRelPath());
      try {
        fis = handlePossibleUTF8Error(meta, fnfe);
      } catch (IOException e) {
        log.error("Unable to handle possible UTF8 error for file: {}.", meta.getRelPath(), e);
      }
    } catch (Exception ex) {
      log.error("Error retrieving file {}", meta.getRelPath(), ex);
    }
    return Optional.ofNullable(fis);
  }

  //
  FileInputStream handlePossibleUTF8Error(FileProperty meta, FileNotFoundException fnfe)
      throws IOException {
    String absPath = meta.getAbsolutePathUri();
    String fname = FilenameUtils.getName(absPath);
    if (!fname.contains("?")) {
      throw fnfe; // it's not a character encoding problem, so just rethrow, we can't fix this
    }
    log.info("Looking for UTF8 unrendered character in database version of filename: {}", absPath);
    Collection<File> matching = getUTF8misMatchFiles(absPath, fname);
    if (matching.isEmpty()) {
      throw fnfe;
    } else if (matching.size() > 1) {
      throw new IllegalStateException("There was more than 1 file; cannot distinguish match");
    } else {
      return new FileInputStream(matching.iterator().next());
    }
  }

  // package scoped for testing
  Collection<File> getUTF8misMatchFiles(String absPath, String fname) {
    if (absPath.startsWith("file:")) {
      absPath = absPath.substring(5, absPath.lastIndexOf(File.separator) + 1);
    } else {
      absPath = absPath.substring(0, absPath.lastIndexOf(File.separator) + 1);
    }
    return FileUtils.listFiles(
        new File(absPath),
        FileFilterUtils.and(new UnhandledUTF8FileFilter(fname), FileFilterUtils.fileFileFilter()),
        null);
  }

  @Override
  public File findFile(FileProperty meta) throws IOException {
    checkInitialised();
    File retrieved = null;
    String f_str = meta.getAbsolutePathUri();
    f_str = f_str.replaceAll("\\\\", "/");
    if (StringUtils.isEmpty(meta.getRelPath())) {
      String tgPath = meta.makeTargetPath(true);
      fileOp.getFoldOp().createPath(meta.makeTargetPath(false));
      retrieved = new File(fileOp.getFoldOp().getBaseDir(), tgPath);
    } else {
      try {
        URI f_uri = new URI(f_str);
        retrieved = new File(f_uri.toURL().getFile());
      } catch (MalformedURLException | URISyntaxException e) {
        log.warn("Invalid URI, could not generate filepath from {}", f_str, e);
        return null;
      }
    }
    return retrieved;
  }

  @Override
  public boolean exists(FileProperty meta) throws IOException {
    checkInitialised();
    return findFile(meta).exists();
  }

  // ------------ support methods --------------------------
  String parseFileName(File localFile) {
    String path = localFile.getAbsolutePath();
    int idx = path.lastIndexOf(File.separator);
    return path.substring(idx + 1);
  }

  /** Reserves a destination for new files before saving their metadata. */
  int addMetadata(FileProperty meta, String fnm, FileDuplicateStrategy duplicateBehaviour)
      throws IOException {
    meta.setRoot(currentRootFs);
    if (StringUtils.isEmpty(meta.getFileName())) {
      meta.setFileName(fnm);
    }
    meta.generateURIFromProperties(baseDir);
    Path destination = baseDir.toPath().resolve(meta.getRelPath());
    Files.createDirectories(destination.getParent());
    boolean reserved = true;
    try {
      Files.createFile(destination);
    } catch (FileAlreadyExistsException e) {
      if (duplicateBehaviour == FileDuplicateStrategy.ERROR) {
        return -1;
      }
      if (duplicateBehaviour == FileDuplicateStrategy.REPLACE) {
        reserved = false;
      } else {
        String extension = FilenameUtils.getExtension(meta.getFileName());
        // Keep ordinary extensions intact; omit extensions exceeding 20 UTF-8 bytes.
        if (extension.getBytes(StandardCharsets.UTF_8).length > 20) {
          extension = "";
        }
        meta.setFileName(
            EscapeReplacement.replaceChars(
                UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension)));
        meta.generateURIFromProperties(baseDir);
        destination = baseDir.toPath().resolve(meta.getRelPath());
        Files.createFile(destination);
      }
    }
    try {
      fileMetadataDao.save(meta);
    } catch (RuntimeException e) {
      if (reserved) {
        removeReservedFile(destination, e);
      }
      log.warn("Could not save file metadata for file :{}", meta.getRelPath(), e);
      throw e;
    }
    return reserved ? 100 : 0;
  }

  private void removeReservedFile(Path destination, Exception failure) {
    try {
      Files.deleteIfExists(destination);
    } catch (IOException cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
  }

  public FileStoreRoot getCurrentFileStoreRoot() {
    return fileMetadataDao.getCurrentFileStoreRoot(false);
  }

  @Override
  public FileStoreRoot getCurrentLocalFileStoreRoot() {
    return getCurrentFileStoreRoot();
  }

  @Override
  public boolean removeFile(FileProperty fileProperty) {
    return false;
  }

  @Override
  public Optional<Integer> removeUserFilestoreFiles(List<File> filestoreFiles) {
    if (filestoreFiles == null) {
      return Optional.empty();
    }
    if (!verifyUserFilestoreFiles(filestoreFiles)) {
      return Optional.empty();
    }

    int result = 0;
    for (File file : filestoreFiles) {
      if (!file.exists()) {
        log.warn("file doesn't exist, skipping: " + file.getAbsolutePath());
        continue;
      }

      log.debug("deleting filestore file: " + file.getAbsolutePath());
      try {
        fileOp.deleteFile(file);
        result++;
      } catch (IOException e) {
        log.warn("Couldn't delete file " + file.getAbsolutePath(), e);
      }
    }
    return Optional.of(result);
  }

  @Override
  public boolean verifyUserFilestoreFiles(List<File> filestoreFiles) {
    boolean result = true;

    // check if the list contains only regular files
    if (filestoreFiles != null) {
      for (File file : filestoreFiles) {
        if (file.exists()) {
          if (!file.isAbsolute()) {
            log.warn("Expected only absoulte paths, but got relative path: " + file.getPath());
            result = false;
          }
          if (!file.isFile()) {
            log.warn("Expected only filestore files, but found non-file: " + file.getPath());
            result = false;
          }
        }
      }
    }
    return result;
  }

  /**
   * Facade to generate and persist the FileProperty and save the file to the file store.
   *
   * @param fileCategory
   * @param user
   * @param originalFileName
   * @param inputStream
   * @return FileProperty the persisted FileProperty
   * @throws IOException
   */
  @Override
  public FileProperty createAndSaveFileProperty(
      String fileCategory, User user, String originalFileName, InputStream inputStream)
      throws IOException {

    FileProperty fileProperty =
        FileProperty.builder()
            .fileCategory(fileCategory)
            .fileGroup(
                user.getGroups().isEmpty()
                    ? user.getUsername()
                    : user.getGroups().iterator().next().getUniqueName())
            .fileOwner(user.getUsername())
            .fileUser(user.getUsername())
            .fileVersion("1")
            .build();
    // we always save locally on initial upload
    fileProperty.setRoot(getCurrentLocalFileStoreRoot());
    String fileName = makeUniqueFileNameForFileProperty(originalFileName);

    URI uri = save(fileProperty, inputStream, fileName, FileDuplicateStrategy.AS_NEW);
    log.debug("File property {} created at URI {}", fileProperty.getId(), uri);
    // log.info("URI is {}", fileProperty.getAbsolutePathUri().toString());
    return fileProperty;
  }

  private String makeUniqueFileNameForFileProperty(String originalFileName) {
    String baseName = FilenameUtils.getBaseName(originalFileName);
    String extension = FilenameUtils.getExtension(originalFileName);
    String newSuffix = "_" + Instant.now().getMillis() + "." + extension;
    // ensure that with new suffix filename is still within limit
    if (baseName.length() + newSuffix.length() > BaseRecord.DEFAULT_VARCHAR_LENGTH) {
      if (extension.length() > 20) {
        // unusually long extension (part after last .), cut new suffix
        newSuffix =
            StringUtils.substring(
                newSuffix, 0, BaseRecord.DEFAULT_VARCHAR_LENGTH - baseName.length());
      } else {
        // normal extension, must be basename that is long, abbreviate it
        baseName =
            StringAbbreviationUtils.abbreviate(
                baseName, BaseRecord.DEFAULT_VARCHAR_LENGTH - newSuffix.length());
      }
    }
    return baseName + newSuffix;
  }
}
