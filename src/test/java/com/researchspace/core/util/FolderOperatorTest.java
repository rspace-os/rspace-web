package com.researchspace.core.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FolderOperatorTest {
  FolderOperator folderOps;
  @Rule public TemporaryFolder fStoreRoot = new TemporaryFolder();
  @Rule public TemporaryFolder fStoreRootToSet = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testFolderOperator() {
    folderOps = new FolderOperator();
    // after construction, base and root dir are set based on runtime values
    assertNotNull(folderOps.getBaseDir());
    assertNotNull(folderOps.getFileRoot());
    assertThat(
        folderOps.getBaseDir().getAbsolutePath(),
        containsString(FolderOperator.FILE_STORE_DIR_NAME));
    assertThat(
        folderOps.getFileRoot().getAbsolutePath(),
        not(containsString(FolderOperator.FILE_STORE_DIR_NAME)));
  }

  @Test
  public void testFolderOperatorString() throws IOException {
    folderOps = new FolderOperator(fStoreRoot.getRoot().getAbsolutePath());
    assertNotNull(folderOps.getBaseDir());
    assertNotNull(folderOps.getFileRoot());
    String baseDir = folderOps.getBaseDir().getAbsolutePath();
    String rootDir = folderOps.getFileRoot().getAbsolutePath();
    assertThat(baseDir, containsString(FolderOperator.FILE_STORE_DIR_NAME));
    assertThat(
        folderOps.getFileRoot().getAbsolutePath(),
        not(containsString(FolderOperator.FILE_STORE_DIR_NAME)));
    assertThat(baseDir, containsString(rootDir));
    assertThat(baseDir, containsString(fStoreRoot.getRoot().getName()));
  }

  @Test
  public void testSetFileStoreRootDir() {
    folderOps = new FolderOperator();
    folderOps.setFileStoreRootDir(fStoreRootToSet.getRoot().getAbsolutePath());
    String baseDir = folderOps.getBaseDir().getAbsolutePath();
    assertThat(baseDir, containsString(fStoreRootToSet.getRoot().getName()));
    assertThat(baseDir, not(containsString(fStoreRoot.getRoot().getName())));
  }

  @Test(expected = NullPointerException.class)
  public void testCreateNullPathThrowsNPE() throws IOException {
    folderOps = new FolderOperator(fStoreRoot.getRoot().getAbsolutePath());
    folderOps.createPath(null);
  }

  @Test
  public void testCreatePath() throws IOException {
    folderOps = new FolderOperator(fStoreRoot.getRoot().getAbsolutePath());
    folderOps.createPath("a");
    assertTrue(new File(folderOps.getBaseDir(), "a").exists());
    assertTrue(new File(folderOps.getBaseDir(), "a").isDirectory());

    folderOps.createPath("a" + File.separator + "b" + File.separator + "c");
    assertTrue(new File(folderOps.getBaseDir(), "a/b/c").exists());
    assertTrue(new File(folderOps.getBaseDir(), "a/b/c").isDirectory());
    assertTrue(new File(folderOps.getBaseDir(), "a/b").isDirectory());
  }
}
