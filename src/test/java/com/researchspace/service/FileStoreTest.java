package com.researchspace.service;

import static com.researchspace.service.FileDuplicateStrategy.AS_NEW;
import static org.apache.commons.io.IOUtils.readLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test the File Store, as example to store a file Prerequisite: add environment variable
 * RS_FILE_BASE in .profile, then create a table file_meta using script
 */
public class FileStoreTest extends SpringTransactionalTest {

  final String testFilePath = "src/test/resources/TestResources/testTxt.txt";
  private @Autowired FileStoreMetaManager fileStoreMetaMgr;

  @Before
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
    Map<String, String> wheres = new HashMap<String, String>();
    wheres.put("fileCategory", "Image");
    wheres.put("fileGroup", "something");
    wheres.put("fileUser", user.getUsername());
    wheres.put("fileVersion", "v1");
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
  @Ignore
  public void testVersion() throws IOException {
    User user = createAndSaveRandomUser();
    // a text file

    FileProperty fp = new FileProperty();
    fp.setFileCategory("Image");
    fp.setFileGroup("any");
    fp.setFileUser(user.getUsername() + "x");
    fp.setFileVersion("v1");
    File resource = new File(testFilePath);
    FileInputStream fis = new FileInputStream(resource);

    URI uri = fileStore.save(fp, fis, "testText.txt", AS_NEW);

    fis.close();

    // copy prserves id
    FileProperty fp2 = fp.copy();
    File resource2 = new File(testFilePath);
    FileInputStream fis2 = new FileInputStream(resource2);
    // will return null as is duplicate

    URI uri2 = fileStore.save(fp2, fis2, "testText.txt", FileDuplicateStrategy.ERROR);

    fis2.close();
    assertNull(uri2);

    // causes a new file to be created as ID is now different.
    fp2.setFileVersion("v2");
    // create a search map to retrieve version 2
    Map<String, String> wheres = new HashMap<String, String>();
    wheres.put("fileVersion", "v2");

    FileInputStream fis3 = new FileInputStream(resource2);

    assertEquals(0, fileStoreMetaMgr.findProperties(wheres).size());
    URI uri3 = fileStore.save(fp2, fis3, "testText.txt", FileDuplicateStrategy.ERROR);
    assertEquals(1, fileStoreMetaMgr.findProperties(wheres).size());

    assertNotNull(uri3);
    assertTrue(uri3.toString().contains("v2"));
    fis3.close();

    // update file content
    String newContent = System.currentTimeMillis() + "";
    FileUtils.writeStringToFile(resource2, newContent);

    // and write, but forcing update
    FileInputStream fis4 = new FileInputStream(resource2);

    URI uri4 = fileStore.save(fp2, fis4, "testText.txt", FileDuplicateStrategy.REPLACE);

    assertNotNull(uri4);
    assertEquals(uri4, uri3); // uri is unchanged

    // check that content has been updated in the repo
    assertEquals(newContent, getFileContentAsStringFromRepo(fp2));

    // v2 file property should still be in DB?

    // assertEquals(1, fileStore.find(wheres).size());
    fis4.close();

    FileProperty fp4 = fp2.copy();
    // now we'll save, but forcing a new file to be saved
    FileInputStream fis5 = new FileInputStream(resource2);
    URI uri5 = fileStore.save(fp2, fis5, "testText.txt", AS_NEW); // this returned uri is not the
    // modified one.
    assertNotNull(uri5);
    assertEquals(newContent, getFileContentAsStringFromRepo(fp2));

    // assertFalse(uri5.equals(uri4));
    fis5.close();
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

    assertEquals("found file isn't the same as original!", found.getName(), file.getName());

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
