package com.researchspace.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    assertThat(folderOps.getBaseDir().getAbsolutePath())
        .as(folderOps.getBaseDir().getAbsolutePath())
        .contains(FolderOperator.FILE_STORE_DIR_NAME);
    assertThat(folderOps.getFileRoot().getAbsolutePath())
        .as(folderOps.getFileRoot().getAbsolutePath())
        .doesNotContain(FolderOperator.FILE_STORE_DIR_NAME);
  }

  @Test
  public void testFolderOperatorString() throws IOException {
    folderOps = new FolderOperator(fStoreRoot.getAbsolutePath());
    assertNotNull(folderOps.getBaseDir());
    assertNotNull(folderOps.getFileRoot());
    String baseDir = folderOps.getBaseDir().getAbsolutePath();
    String rootDir = folderOps.getFileRoot().getAbsolutePath();
    assertThat(baseDir).as(baseDir).contains(FolderOperator.FILE_STORE_DIR_NAME);
    assertThat(folderOps.getFileRoot().getAbsolutePath())
        .as(folderOps.getFileRoot().getAbsolutePath())
        .doesNotContain(FolderOperator.FILE_STORE_DIR_NAME);
    assertThat(baseDir).as(baseDir).contains(rootDir);
    assertThat(baseDir).as(baseDir).contains(fStoreRoot.getName());
  }

  @Test
  public void testSetFileStoreRootDir() {
    folderOps = new FolderOperator();
    folderOps.setFileStoreRootDir(fStoreRootToSet.getAbsolutePath());
    String baseDir = folderOps.getBaseDir().getAbsolutePath();
    assertThat(baseDir).as(baseDir).contains(fStoreRootToSet.getName());
    assertThat(baseDir).as(baseDir).doesNotContain(fStoreRoot.getName());
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
    assertThat(new File(folderOps.getBaseDir(), "a")).exists();
    assertThat(new File(folderOps.getBaseDir(), "a")).isDirectory();

    folderOps.createPath("a" + File.separator + "b" + File.separator + "c");
    assertThat(new File(folderOps.getBaseDir(), "a/b/c")).exists();
    assertThat(new File(folderOps.getBaseDir(), "a/b/c")).isDirectory();
    assertThat(new File(folderOps.getBaseDir(), "a/b")).isDirectory();
  }
}
