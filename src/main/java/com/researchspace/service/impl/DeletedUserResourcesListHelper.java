package com.researchspace.service.impl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeletedUserResourcesListHelper {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Value("${sysadmin.delete.user.resourceList.folder}")
  private String deletedUserResourcesListFolderLocation;

  /**
   * @return true if resources list seems writable, false if not
   */
  public boolean isUserResourcesListWriteable() {
    if (StringUtils.isEmpty(deletedUserResourcesListFolderLocation)) {
      return false;
    }

    Path deletedResourcesListingDir = new File(deletedUserResourcesListFolderLocation).toPath();
    if (Files.notExists(deletedResourcesListingDir)) {
      try {
        log.info("creating deleted resources listing dir");
        Files.createDirectories(deletedResourcesListingDir);
      } catch (IOException e) {
        log.warn("cannot create directory at " + deletedUserResourcesListFolderLocation, e);
      }
    }
    return Files.isDirectory(deletedResourcesListingDir)
        && Files.isWritable(deletedResourcesListingDir);
  }

  /**
   * Method takes userId and the list of that user's filestore resources and saves them to the new
   * resource list file for further use.
   *
   * @return true if the paths were successfully written to the file
   */
  public boolean saveUserResourcesListToTemporaryFile(Long userId, List<File> deletedUserFiles) {
    File listingFile = getResourcesListFile(userId, true);
    try {
      // create the file if it doesn't exist, or remove it's content if it does
      FileUtils.write(listingFile, "", Charset.defaultCharset());
      // write file paths to the file
      if (deletedUserFiles != null) {
        List<String> filePaths =
            deletedUserFiles.stream().map(File::getAbsolutePath).collect(Collectors.toList());
        FileUtils.writeLines(listingFile, filePaths);
      }
    } catch (IOException ioe) {
      log.warn("couldn't write to resource listings file at " + listingFile.getAbsolutePath(), ioe);
      return false;
    }
    return true;
  }

  /**
   * Marks temporary resource listing file as final.
   *
   * @param userId
   * @return
   */
  public boolean markTempUserResourcesListAsFinal(Long userId) {
    File tempListingFile = getResourcesListFile(userId, true);
    File finalListingFile = getResourcesListFile(userId, false);
    return tempListingFile.renameTo(finalListingFile);
  }

  /**
   * @param removedUserId id of user for whom the list of filestore resources was saved
   * @return list of filestore resources saved during user deletion
   */
  public Optional<List<File>> retrieveUserResourcesList(Long removedUserId) {
    File resourcesListFile = getResourcesListFile(removedUserId, false);
    if (!resourcesListFile.exists()) {
      log.warn("file {} doesn't exist ", resourcesListFile.getAbsolutePath());
      return Optional.empty();
    }

    List<File> resourcesFromListFile = null;
    try {
      List<String> paths = FileUtils.readLines(resourcesListFile, Charset.defaultCharset());
      resourcesFromListFile = paths.stream().map(p -> new File(p)).collect(Collectors.toList());
    } catch (IOException ioe) {
      log.warn(
          "couldn't read the resource listsings file from " + resourcesListFile.getAbsolutePath(),
          ioe);
      return Optional.empty();
    }
    return Optional.of(resourcesFromListFile);
  }

  /**
   * Removes resources list file for the particular user. To be called when list is no longer
   * necessary, e.g. after successful deletion of all the resources.
   *
   * @param removedUserId
   * @param temp whether that's temp list file that should be deleted
   */
  public boolean removeResourcesListFile(Long removedUserId, boolean temp) {
    log.debug("deleting resources listing files for " + removedUserId);
    File deletedUserResourcesFile = getResourcesListFile(removedUserId, temp);
    return FileUtils.deleteQuietly(deletedUserResourcesFile);
  }

  protected File getResourcesListFile(Long userId, boolean temp) {
    String filename = userId + ".txt" + (temp ? ".tmp" : "");
    Path path = Paths.get(deletedUserResourcesListFolderLocation, filename);
    return path.toFile();
  }

  /*
   * for testing
   */
  String getDeletedUserResourcesListFolderLocation() {
    return deletedUserResourcesListFolderLocation;
  }

  void setDeletedUserResourcesListFolderLocation(String deletedUserResourcesListFolderLocation) {
    this.deletedUserResourcesListFolderLocation = deletedUserResourcesListFolderLocation;
  }
}
