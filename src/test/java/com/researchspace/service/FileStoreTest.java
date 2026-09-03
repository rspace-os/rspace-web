package com.researchspace.service;

import static com.researchspace.service.FileDuplicateStrategy.AS_NEW;
import static org.apache.commons.io.IOUtils.readLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test the File Store, as example to store a file Prerequisite: add environment variable
 * RS_FILE_BASE in .profile, then create a table file_meta using script
 */
public class FileStoreTest extends SpringTransactionalTest {

  final String testFilePath = "src/test/resources/TestResources/testTxt.txt";
  private @Autowired FileStoreMetaManager fileStoreMetaMgr;
  @TempDir Path tempFolder;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void storeFileTest() throws IOException {
    User user = createAndSaveRandomUser();
    FileProperty fp = createFileProperties(user);

    File sourceFile = new File(testFilePath);
    URI urix = fileStore.save(fp, sourceFile, FileDuplicateStrategy.REPLACE);
    assertNotNull(urix);

    // retrieve meta data
    Map<String, String> wheres =
        Map.of(
            "fileCategory", "Image",
            "fileGroup", "something",
            "fileUser", user.getUsername(),
            "fileVersion", "v1");
    List<FileProperty> flst = fileStoreMetaMgr.findProperties(wheres);
    assertTrue(flst.size() >= 0);

    // retrieve file from the file Property file

    FileInputStream fis = fileStore.retrieve(flst.get(0)).get();
    assertTrue(fis != null);
    assertTrue(readLines(fis, "UTF-8").size() > 0);
  }

  @Test
  public void storeFileStreamTest() throws Exception {
    User user = createAndSaveRandomUser();
    FileProperty fp = createFileProperties(user);

    File sourceFile = new File(testFilePath);
    FileInputStream fis = new FileInputStream(sourceFile);
    URI uri = fileStore.save(fp, fis, RandomStringUtils.randomAlphanumeric(5) + "aaaa", AS_NEW);
    assertNotNull(uri);
    assertTrue(getFileContentAsStringFromRepo(fp).length() > 0);
  }

  @Test
  public void testVersion() throws IOException {
    User user = createAndSaveRandomUser();
    FileProperty fp = new FileProperty();
    fp.setFileCategory("Image");
    fp.setFileGroup(RandomStringUtils.randomAlphanumeric(12));
    fp.setFileUser(user.getUsername());
    fp.setFileVersion("v1");
    Path source = tempFolder.resolve("testText.txt");
    Files.writeString(source, "first version");
    URI uri = fileStore.save(fp, source.toFile(), AS_NEW);
    assertNotNull(uri);

    FileProperty fp2 = fp.copy();
    fp2.setFileVersion("v2");
    Map<String, String> wheres =
        Map.of(
            "fileCategory", fp2.getFileCategory(),
            "fileGroup", fp2.getFileGroup(),
            "fileUser", fp2.getFileUser(),
            "fileVersion", "v2");
    assertEquals(0, fileStoreMetaMgr.findProperties(wheres).size());
    URI version2Uri = fileStore.save(fp2, source.toFile(), FileDuplicateStrategy.ERROR);
    assertEquals(1, fileStoreMetaMgr.findProperties(wheres).size());
    assertNotNull(version2Uri);
    assertTrue(version2Uri.toString().contains("v2"));

    // update file content
    String newContent = "replacement content";
    Files.writeString(source, newContent);
    URI replacedUri = fileStore.save(fp2, source.toFile(), FileDuplicateStrategy.REPLACE);
    assertEquals(version2Uri, replacedUri);
    assertEquals(newContent, getFileContentAsStringFromRepo(fp2));
    assertEquals(1, fileStoreMetaMgr.findProperties(wheres).size());
  }

  String getFileContentAsStringFromRepo(FileProperty fp) throws IOException {
    try (FileInputStream fis = fileStore.retrieve(fp).get()) {
      List<String> lines = IOUtils.readLines(fis, "UTF-8");
      return StringUtils.join(lines, "\n");
    }
  }

  @Test
  public void testRetrieveBasedOnProperties() throws IOException {
    User user = createAndSaveRandomUser();
    FileProperty fp = createFileProperties(user);
    File file = new File(testFilePath);
    URI stored = fileStore.save(fp, file, FileDuplicateStrategy.AS_NEW);
    assertTrue(fileStore.exists(fp));
    File found = fileStore.findFile(fp);

    assertEquals(found.getName(), file.getName(), "found file isn't the same as original!");

    // change properties so doesn't exist...
    fp.setFileCategory("pdf2");
    fp.setRelPath(null);
    String relpath = fp.makeTargetPath(true);
    fp.setRelPath(relpath);
    assertFalse(fileStore.exists(fp));
    // not null, but doesn't exist
    File found2 = fileStore.findFile(fp);
    assertNotNull(found2);
    assertFalse(found2.exists());
  }

  private FileProperty createFileProperties(User user) {
    FileProperty fp = new FileProperty();
    fp.setFileCategory("Image");
    fp.setFileGroup("something");
    fp.setFileUser(user.getUsername());
    fp.setFileVersion("v1");
    return fp;
  }

  @Test
  public void testUserFilesDeletion() throws IOException {

    User user = createAndSaveRandomUser();
    FileProperty fp = createFileProperties(user);
    File file = new File(testFilePath);
    fileStore.save(fp, file, FileDuplicateStrategy.AS_NEW);

    FileProperty fp2 = createFileProperties(user);
    fp2.setFileVersion("v2");
    File file2 = new File(testFilePath);
    fileStore.save(fp2, file2, FileDuplicateStrategy.AS_NEW);

    assertTrue(fileStore.exists(fp));
    assertTrue(fileStore.exists(fp2));
    File found = fileStore.findFile(fp);
    File parentFolder = found.getParentFile();

    // with folder on a list, the removal action shouldn't do anything
    List<File> filestoreFileAndFolder = Arrays.asList(found, parentFolder);
    Optional<Integer> folderOnListRemovedFilesCount =
        fileStore.removeUserFilestoreFiles(filestoreFileAndFolder);
    assertFalse(folderOnListRemovedFilesCount.isPresent());

    // let's try with just a file on a list
    List<File> filestoreFile = Arrays.asList(found);
    Optional<Integer> removedFilesCount = fileStore.removeUserFilestoreFiles(filestoreFile);
    assertTrue(removedFilesCount.isPresent());
    assertEquals(1, removedFilesCount.get().intValue());
    assertFalse(fileStore.exists(fp));
    assertTrue(fileStore.exists(fp2));

    // subsequent remove does nothing
    Optional<Integer> subsequentRemovedFilesCount =
        fileStore.removeUserFilestoreFiles(filestoreFile);
    assertTrue(subsequentRemovedFilesCount.isPresent());
    assertEquals(0, subsequentRemovedFilesCount.get().intValue());
  }
}
