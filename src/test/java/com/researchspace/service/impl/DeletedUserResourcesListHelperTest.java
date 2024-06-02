package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class DeletedUserResourcesListHelperTest {

  private DeletedUserResourcesListHelper resourcesListHelper;
  private static File tmpDir;

  @Before
  public void before() throws IOException {
    resourcesListHelper = new DeletedUserResourcesListHelper();

    if (tmpDir == null) {
      tmpDir = Files.createTempDirectory("userRemovalListings").toFile();
    }
    resourcesListHelper.setDeletedUserResourcesListFolderLocation(tmpDir.getAbsolutePath());
  }

  @Test
  public void checkSavingRetrivingDeletingFilestoreResourcesList() throws IOException {

    Long userId = 1515L;
    File testFile = Paths.get("asdf").toAbsolutePath().toFile();
    List<File> testFileList = Arrays.asList(testFile);

    File tempResourcesFile = resourcesListHelper.getResourcesListFile(userId, true);
    assertEquals(
        tmpDir.getAbsoluteFile() + File.separator + userId + ".txt.tmp",
        tempResourcesFile.getAbsolutePath());
    File finalResourcesFile = resourcesListHelper.getResourcesListFile(userId, false);
    assertEquals(
        tmpDir.getAbsoluteFile() + File.separator + userId + ".txt",
        finalResourcesFile.getAbsolutePath());

    // save temp list
    boolean saveTempListResult =
        resourcesListHelper.saveUserResourcesListToTemporaryFile(userId, testFileList);
    assertTrue(saveTempListResult);
    assertTrue(tempResourcesFile.exists());
    assertTrue(tempResourcesFile.length() > 1);
    assertFalse(finalResourcesFile.exists());

    // mark as final
    boolean makeListFinalResult = resourcesListHelper.markTempUserResourcesListAsFinal(userId);
    assertTrue(makeListFinalResult);
    assertFalse(tempResourcesFile.exists());
    assertTrue(finalResourcesFile.exists());
    assertTrue(finalResourcesFile.length() > 1);

    // retrieve
    Optional<List<File>> retrievedListOpt = resourcesListHelper.retrieveUserResourcesList(userId);
    assertTrue(retrievedListOpt.isPresent());
    assertEquals(testFileList, retrievedListOpt.get());
    assertTrue(finalResourcesFile.exists());

    // delete list file
    boolean listFileRemovalResult = resourcesListHelper.removeResourcesListFile(userId, false);
    assertTrue(listFileRemovalResult);
    assertFalse(finalResourcesFile.exists());
  }

  @Test
  public void checkErrorOnFilestoreResourcesFolderMisconfigurations() throws IOException {

    // null path should fail
    resourcesListHelper.setDeletedUserResourcesListFolderLocation(null);
    boolean folderWriteable = resourcesListHelper.isUserResourcesListWriteable();
    assertFalse(folderWriteable);

    // path pointing to the file should fail
    resourcesListHelper.setDeletedUserResourcesListFolderLocation(
        Files.createTempFile("asdf", ".txt").toFile().getAbsolutePath());
    folderWriteable = resourcesListHelper.isUserResourcesListWriteable();
    assertFalse(folderWriteable);

    // writable folder should fail
    resourcesListHelper.setDeletedUserResourcesListFolderLocation(tmpDir.getAbsolutePath());
    folderWriteable = resourcesListHelper.isUserResourcesListWriteable();
    assertTrue(folderWriteable);
  }
}
