package com.researchspace.core.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileOperatorTest {

  FileOperator fileOps;

  @TempDir public File fileStoreRoot;
  File anyFile;

  @BeforeEach
  public void setUp() throws Exception {
    File fileSrc = new File("src/test/resources/exampleDoc.pdf");
    anyFile = File.createTempFile("testFile", ".pdf");
    FileUtils.copyFile(fileSrc, anyFile);
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testFileOperator() {
    createFileOperator();
    assertNotNull(fileOps.getFoldOp().getBaseDir());
  }

  @Test
  public void testAddFile() throws IOException {
    createFileOperator();
    URI inserted =
        fileOps.addFile(
            "a" + File.separator + "b" + File.separator + "c" + File.separator, anyFile, "xyz.pdf");
    String manualPath =
        (fileOps.getFoldOp().getBaseDir() + "/a/b/c/xyz.pdf").replaceAll("\\\\", "/");
    assertThat(inserted.toString(), containsString(manualPath));
  }

  private void createFileOperator() {
    fileOps = new FileOperator();
    fileOps.getFoldOp().setFileStoreRootDir(fileStoreRoot);
  }

  @Test
  public void testCopyFile() throws IOException {
    createFileOperator();
    File outfile = newFile(fileStoreRoot, "xxxx.pdf");
    fileOps.copyFile(outfile, anyFile, false);
    assertEquals(outfile.length(), anyFile.length());
    assertTrue(outfile.exists());
    assertTrue(anyFile.exists());

    // copy outfile into another file, removing the original
    File outfile2 = newFile(fileStoreRoot, "yyyy.pdf");
    fileOps.copyFile(outfile2, outfile, true);
    assertEquals(outfile2.length(), anyFile.length());
    assertTrue(outfile2.exists());
    assertTrue(anyFile.exists());
    assertFalse(outfile.exists());
  }

  @Test
  public void testCopyStream() throws IOException {
    createFileOperator();
    File outfile = File.createTempFile("junit", null, fileStoreRoot);
    FileOutputStream outStream = new FileOutputStream(outfile);
    FileInputStream fis = new FileInputStream(anyFile);
    long expectedCopiedByteCount = fileOps.copyStream(outStream, fis, 0L);
    assertEquals(241366L, expectedCopiedByteCount);
    assertEquals(outfile.length(), anyFile.length());
    assertTrue(outfile.exists());
    assertTrue(anyFile.exists());
    fis.close();
    outStream.close();

    // copy outfile into another file, removing the original
    File outfile2 = newFile(fileStoreRoot, "yyyy.pdf");
    FileOutputStream outStream2 = new FileOutputStream(outfile2);
    FileInputStream fis2 = new FileInputStream(outfile);
    fileOps.copyStream(outStream2, fis2, 0L);
    assertEquals(outfile2.length(), anyFile.length());
    assertTrue(outfile2.exists());
    assertTrue(anyFile.exists());
  }

  @Test
  public void testRemoveFile() throws IOException {
    createFileOperator();
    File outfile = File.createTempFile("junit", null, fileStoreRoot);
    assertTrue(outfile.exists());

    // try delete
    fileOps.deleteFile(outfile);
    assertFalse(outfile.exists());

    // try delete same path again
    try {
      fileOps.deleteFile(outfile);
      fail("expected exception on subsequent delete");
    } catch (IOException e) {
      boolean expectedMsg =
          e instanceof FileNotFoundException
              || e.getMessage().startsWith("File does not exist")
              || e.getMessage().startsWith("Cannot delete file");
      assertTrue(expectedMsg, "expected file not exists, but was:" + e.getMessage());
    }
  }

  private static File newFile(File parent, String child) throws IOException {
    File result = new File(parent, child);
    result.createNewFile();
    return result;
  }
}
