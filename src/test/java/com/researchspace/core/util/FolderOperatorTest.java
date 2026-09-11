package com.researchspace.core.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FolderOperatorTest {
  FolderOperator folderOps;
  @TempDir public File fStoreRoot;
  @TempDir public File fStoreRootToSet;

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testFolderOperator() {
    folderOps = new FolderOperator();
    // after construction, base and root dir are set based on runtime values
    assertNotNull(folderOps.getBaseDir());
    assertNotNull(folderOps.getFileRoot());
    assertTrue(
        folderOps.getBaseDir().getAbsolutePath().contains(FolderOperator.FILE_STORE_DIR_NAME),
        folderOps.getBaseDir().getAbsolutePath());
    assertFalse(
        folderOps.getFileRoot().getAbsolutePath().contains(FolderOperator.FILE_STORE_DIR_NAME),
        folderOps.getFileRoot().getAbsolutePath());
  }

  @Test
  public void testFolderOperatorString() throws IOException {
    folderOps = new FolderOperator(fStoreRoot.getAbsolutePath());
    assertNotNull(folderOps.getBaseDir());
    assertNotNull(folderOps.getFileRoot());
    String baseDir = folderOps.getBaseDir().getAbsolutePath();
    String rootDir = folderOps.getFileRoot().getAbsolutePath();
    assertTrue(baseDir.contains(FolderOperator.FILE_STORE_DIR_NAME), baseDir);
    assertFalse(
        folderOps.getFileRoot().getAbsolutePath().contains(FolderOperator.FILE_STORE_DIR_NAME),
        folderOps.getFileRoot().getAbsolutePath());
    assertTrue(baseDir.contains(rootDir), baseDir);
    assertTrue(baseDir.contains(fStoreRoot.getName()), baseDir);
  }

  @Test
  public void testSetFileStoreRootDir() {
    folderOps = new FolderOperator();
    folderOps.setFileStoreRootDir(fStoreRootToSet.getAbsolutePath());
    String baseDir = folderOps.getBaseDir().getAbsolutePath();
    assertTrue(baseDir.contains(fStoreRootToSet.getName()), baseDir);
    assertFalse(baseDir.contains(fStoreRoot.getName()), baseDir);
  }

  @Test
  public void testCreateNullPathThrowsNPE() throws IOException {
    String fileStoreRoot = fStoreRoot.getAbsolutePath();
    folderOps = new FolderOperator(fileStoreRoot);

    assertThrows(NullPointerException.class, () -> folderOps.createPath(null));
  }

  @Test
  public void testCreatePath() throws IOException {
    folderOps = new FolderOperator(fStoreRoot.getAbsolutePath());
    folderOps.createPath("a");
    assertTrue(new File(folderOps.getBaseDir(), "a").exists());
    assertTrue(new File(folderOps.getBaseDir(), "a").isDirectory());

    folderOps.createPath("a" + File.separator + "b" + File.separator + "c");
    assertTrue(new File(folderOps.getBaseDir(), "a/b/c").exists());
    assertTrue(new File(folderOps.getBaseDir(), "a/b/c").isDirectory());
    assertTrue(new File(folderOps.getBaseDir(), "a/b").isDirectory());
  }
}
