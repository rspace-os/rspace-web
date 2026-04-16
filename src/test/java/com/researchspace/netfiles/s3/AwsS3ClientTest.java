package com.researchspace.netfiles.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsResourceDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.service.aws.impl.S3UtilitiesImpl.S3FolderContentItem;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

public class AwsS3ClientTest {

  private final S3Utilities s3Utilities = mock(S3Utilities.class);
  private final AwsS3Client client = new AwsS3Client("testUser", s3Utilities);

  @Test
  public void testNfsFileTreeNodeConversion() {

    NfsFileStore testFileStore = new NfsFileStore();
    testFileStore.setId(1L);
    testFileStore.setPath("test");

    S3FolderContentItem fileResource =
        new S3FolderContentItem("test.txt", false, 12L, Instant.now());
    NfsFileTreeNode nodeForFileResource =
        client.getNodeFromS3Item(fileResource, "test", null, testFileStore);
    assertEquals("test.txt", nodeForFileResource.getFileName());
    assertFalse(nodeForFileResource.getIsFolder());
    assertEquals("test/test.txt", nodeForFileResource.getNodePath());
    assertEquals("1:/test.txt", nodeForFileResource.getLogicPath());
    assertEquals("12", nodeForFileResource.getFileSize());
    assertEquals(12L, nodeForFileResource.getFileSizeBytes());
    assertNotNull(nodeForFileResource.getModificationDateMillis());

    S3FolderContentItem folderResource = new S3FolderContentItem("test2.txt", true, null, null);
    NfsFileTreeNode nodeForFolderResource =
        client.getNodeFromS3Item(folderResource, "test", null, testFileStore);
    assertEquals("test2.txt", nodeForFolderResource.getFileName());
    assertTrue(nodeForFolderResource.getIsFolder());
    assertEquals("test/test2.txt", nodeForFolderResource.getNodePath());
    assertEquals("1:/test2.txt", nodeForFolderResource.getLogicPath());
    assertEquals("0", nodeForFolderResource.getFileSize());
    assertEquals(0, nodeForFolderResource.getFileSizeBytes());
    assertNull(nodeForFolderResource.getModificationDateMillis());
  }

  @Test
  public void testCreateFileTree() throws IOException {
    NfsFileStore testFileStore = new NfsFileStore();
    testFileStore.setId(1L);
    testFileStore.setPath("/testTopLevelFolder");

    S3FolderContentItem file1 = new S3FolderContentItem("test1.txt", false, 100L, Instant.now());
    when(s3Utilities.listFolderContents("testTopLevelFolder/testTarget"))
        .thenReturn(Collections.singletonList(file1));

    NfsFileTreeNode rootNode =
        client.createFileTree("/testTopLevelFolder/testTarget", null, testFileStore);
    assertNotNull(rootNode);
    assertEquals("testTarget", rootNode.getFileName());
    assertEquals("testTopLevelFolder/testTarget", rootNode.getNodePath());
    assertTrue(rootNode.getIsFolder());
    assertEquals(1, rootNode.getNodes().size());

    NfsFileTreeNode child = rootNode.getNodes().get(0);
    assertEquals("test1.txt", child.getFileName());
    assertEquals("testTopLevelFolder/testTarget/test1.txt", child.getNodePath());
    assertEquals("1:/testTarget/test1.txt", child.getLogicPath());
    assertFalse(child.getIsFolder());
  }

  @Test
  public void testQueryNfsFileForDownload() throws IOException {
    String testPath = "/test/file.txt";
    String expectedS3Path = "test/file.txt";

    when(s3Utilities.isFileInS3("", expectedS3Path)).thenReturn(true);
    doAnswer(
            invocation -> {
              File file = invocation.getArgument(1);
              FileUtils.writeStringToFile(file, "test content", StandardCharsets.UTF_8);
              return null;
            })
        .when(s3Utilities)
        .downloadFromS3(eq(expectedS3Path), any(File.class));

    NfsFileDetails details = client.queryNfsFileForDownload(new NfsTarget(testPath));

    assertNotNull(details);
    assertEquals("file.txt", details.getName());
    try (InputStream is = details.getRemoteInputStream()) {
      assertNotNull(is);
      String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      assertEquals("test content", content);
    }
    verify(s3Utilities).isFileInS3("", expectedS3Path);
    verify(s3Utilities).downloadFromS3(eq(expectedS3Path), any(File.class));
  }

  @Test
  public void testQueryNfsFileForDownloadFileNotFound() {
    String testPath = "non-existent.txt";
    NfsTarget target = new NfsTarget(testPath);

    when(s3Utilities.isFileInS3("", testPath)).thenReturn(false);
    assertThrows(IllegalArgumentException.class, () -> client.queryNfsFileForDownload(target));
  }

  @Test
  public void testQueryNfsFileForDownloadWithLeadingSlashes() throws IOException {
    String testPath = "//leading/slashes.txt/";
    NfsTarget target = new NfsTarget(testPath);
    String expectedS3Path = "leading/slashes.txt";

    when(s3Utilities.isFileInS3("", expectedS3Path)).thenReturn(true);

    NfsFileDetails details = client.queryNfsFileForDownload(target);

    assertNotNull(details);
    assertEquals("slashes.txt", details.getName());
    verify(s3Utilities).isFileInS3("", expectedS3Path);
  }

  @Test
  public void testQueryForNfsFile() {
    String testPath = "test/file.txt";
    S3FolderContentItem item = new S3FolderContentItem("file.txt", false, 100L, Instant.now());
    when(s3Utilities.getObjectDetails("test/file.txt")).thenReturn(item);

    NfsFileDetails details = client.queryForNfsFile(new NfsTarget(testPath));

    assertNotNull(details);
    assertEquals("file.txt", details.getName());
    assertEquals("test/file.txt", details.getFileSystemFullPath());
    assertEquals("test", details.getFileSystemParentPath());
    assertEquals(100L, details.getSize());
  }

  @Test
  public void testQueryForNfsFolder() throws IOException {
    String testPath = "test/folder";
    S3FolderContentItem folderItem = new S3FolderContentItem("folder", true, null, null);
    when(s3Utilities.getObjectDetails("test/folder")).thenReturn(folderItem);

    S3FolderContentItem file1 = new S3FolderContentItem("file1.txt", false, 50L, Instant.now());
    S3FolderContentItem subfolder = new S3FolderContentItem("subfolder", true, null, null);
    when(s3Utilities.listFolderContents("test/folder")).thenReturn(List.of(file1, subfolder));

    NfsFolderDetails details = client.queryForNfsFolder(new NfsTarget(testPath));

    assertNotNull(details);
    assertEquals("folder", details.getName());
    assertEquals("test/folder", details.getFileSystemFullPath());
    assertEquals("test", details.getFileSystemParentPath());
    assertEquals(2, details.getContent().size());

    NfsResourceDetails res1 = details.getContent().get(0);
    assertEquals("file1.txt", res1.getName());
    assertEquals("test/folder/file1.txt", res1.getFileSystemFullPath());
    assertEquals("test/folder", res1.getFileSystemParentPath());
    assertTrue(res1.isFile());
    assertEquals(50L, res1.getSize());

    NfsResourceDetails res2 = details.getContent().get(1);
    assertEquals("subfolder", res2.getName());
    assertEquals("test/folder/subfolder", res2.getFileSystemFullPath());
    assertEquals("test/folder", res2.getFileSystemParentPath());
    assertTrue(res2.isFolder());
  }
}
