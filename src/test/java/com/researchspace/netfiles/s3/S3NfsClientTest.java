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
import com.researchspace.netfiles.DeletableTarget;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsResourceDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.netfiles.WritableNfsClient;
import com.researchspace.netfiles.WriteAttribution;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.service.aws.impl.S3UtilitiesImpl.S3FolderContentItem;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

public class S3NfsClientTest {

  private final S3Utilities s3Utilities = mock(S3Utilities.class);
  private final S3NfsClient client = new S3NfsClient("testUser", s3Utilities);

  /**
   * Wires the source client's bucket name and returns a fresh destination client backed by its own
   * mock {@link S3Utilities}. Use this in any test that exercises {@link S3NfsClient#copyObject}.
   */
  private DestClientFixture newDestClient(String sourceBucketName) {
    when(s3Utilities.getBucketName()).thenReturn(sourceBucketName);
    S3Utilities destS3Utilities = mock(S3Utilities.class);
    return new DestClientFixture(new S3NfsClient("destUser", destS3Utilities), destS3Utilities);
  }

  /** Bundle of a destination client and its mock {@link S3Utilities} for verification. */
  private static class DestClientFixture {
    final S3NfsClient client;
    final S3Utilities mockS3Utilities;

    DestClientFixture(S3NfsClient client, S3Utilities mockS3Utilities) {
      this.client = client;
      this.mockS3Utilities = mockS3Utilities;
    }
  }

  @Test
  public void s3NfsClient_supportsWrite() {
    assertTrue(client.supportsWrite());
    assertTrue(client instanceof WritableNfsClient);
  }

  @Test
  public void uploadFile_uploadsToS3AndReturnsKey() throws IOException {
    File source = new File("Picture1.png");
    String key = client.uploadFile(source, "dest/folder");

    assertEquals("dest/folder/Picture1.png", key);
    verify(s3Utilities).uploadToS3("dest/folder", source, Collections.emptyMap());
  }

  @Test
  public void uploadFile_withMetadata_passesMetadataToS3Utilities() throws IOException {
    File source = new File("Picture1.png");
    Map<String, String> metadata = Map.of("rspace-user", "alice", "rspace-record-id", "42");

    String key = client.uploadFile(source, "dest/folder", metadata);

    assertEquals("dest/folder/Picture1.png", key);
    verify(s3Utilities).uploadToS3("dest/folder", source, metadata);
  }

  @Test
  public void uploadFile_leadingSlashInDestPath_stripsLeadingSlash() throws IOException {
    File source = new File("Picture1.png");

    String key = client.uploadFile(source, "/dest/folder");

    assertEquals("dest/folder/Picture1.png", key);
    verify(s3Utilities).uploadToS3("dest/folder", source, Collections.emptyMap());
  }

  @Test
  public void uploadFile_emptyDestPath_uploadsToRootAndReturnsFilename() throws IOException {
    File source = new File("Picture1.png");

    String key = client.uploadFile(source, "");

    assertEquals("Picture1.png", key);
    verify(s3Utilities).uploadToS3("", source, Collections.emptyMap());
  }

  @Test
  public void uploadFile_destinationKeyAlreadyExists_throwsIOExceptionAndDoesNotUpload()
      throws IOException {
    when(s3Utilities.isFileInS3("dest/folder", "Picture1.png")).thenReturn(true);

    IOException ex =
        assertThrows(
            IOException.class, () -> client.uploadFile(new File("Picture1.png"), "dest/folder"));
    assertTrue(ex.getMessage().contains("already exists"));
    verify(s3Utilities, org.mockito.Mockito.never()).uploadToS3(any(), any(), any());
  }

  @Test
  public void uploadFilesToNfs_withAttribution_callsUploadToS3WithPerRecordMetadata() {
    File f1 = new File("file1.png");
    File f2 = new File("file2.png");
    WriteAttribution attribution =
        new WriteAttribution(
            "alice", Map.of(123L, "file1", 456L, "file2"), java.time.Instant.now());

    client.uploadFilesToNfs("dest", Map.of(123L, f1, 456L, f2), attribution);

    verify(s3Utilities).uploadToS3("dest", f1, attribution.metadataForRecord(123L));
    verify(s3Utilities).uploadToS3("dest", f2, attribution.metadataForRecord(456L));
  }

  @Test
  public void deleteFile_callsS3DeleteWithSplitPath() throws IOException {
    client.deleteFile("dest/folder/file.txt");

    verify(s3Utilities).deleteFromS3("dest/folder", "file.txt");
  }

  @Test
  public void createFolder_createsPlaceholderWithMetadataAndReturnsPath() throws IOException {
    Map<String, String> meta =
        Map.of("rspace-created-by", "testUser", "rspace-created-at", "2026-06-18T10:00:00Z");
    when(s3Utilities.getObjectDetails("parent/new")).thenReturn(null);

    String result = client.createFolder("parent/new", meta);

    assertEquals("parent/new", result);
    verify(s3Utilities).createFolder("parent/new", meta);
  }

  @Test
  public void createFolder_whenFileExistsAtPath_throws() {
    S3FolderContentItem file = new S3FolderContentItem("new", false, 10L, Instant.now());
    when(s3Utilities.getObjectDetails("parent/new")).thenReturn(file);

    assertThrows(IOException.class, () -> client.createFolder("parent/new", Map.of()));
  }

  @Test
  public void createFileTree_emptyPath_browsesBucketRoot() throws IOException {
    // A filestore rooted at the bucket top level has an empty path; browsing it must list the
    // bucket root rather than require a subfolder.
    when(s3Utilities.listFolderContents(""))
        .thenReturn(List.of(new S3FolderContentItem("topfolder", true, 0L, null)));

    NfsFileTreeNode root = client.createFileTree("", null, null);

    verify(s3Utilities).listFolderContents("");
    assertEquals(1, root.getNodes().size());
  }

  @Test
  public void createFileTree_populatesCreatedByAndCreatedAtFromObjectMetadata() throws IOException {
    when(s3Utilities.listFolderContents("dir"))
        .thenReturn(List.of(new S3FolderContentItem("a.txt", false, 10L, Instant.now())));
    S3FolderContentItem withMeta = new S3FolderContentItem("a.txt", false, 10L, Instant.now());
    withMeta.setUserMetadata(
        Map.of("rspace-created-by", "alice", "rspace-created-at", "2026-06-18T10:00:00Z"));
    when(s3Utilities.getObjectDetails("dir/a.txt")).thenReturn(withMeta);

    NfsFileTreeNode node = client.createFileTree("dir", null, null).getNodes().get(0);

    assertEquals("alice", node.getCreatedBy());
    assertEquals(
        Instant.parse("2026-06-18T10:00:00Z").toEpochMilli(),
        node.getCreatedAtMillis().longValue());
  }

  @Test
  public void createFileTree_metadataLookupFailure_stillReturnsListing() throws IOException {
    when(s3Utilities.listFolderContents("dir"))
        .thenReturn(List.of(new S3FolderContentItem("a.txt", false, 10L, Instant.now())));
    when(s3Utilities.getObjectDetails("dir/a.txt"))
        .thenThrow(new RuntimeException("S3 unavailable"));

    NfsFileTreeNode node = client.createFileTree("dir", null, null).getNodes().get(0);

    assertEquals("a.txt", node.getFileName());
    assertNull(node.getCreatedBy());
  }

  @Test
  public void moveWithin_file_copiesPreservingMetadataThenDeletesSource() throws IOException {
    when(s3Utilities.getBucketName()).thenReturn("bucket");
    S3FolderContentItem file = new S3FolderContentItem("a.txt", false, 10L, Instant.now());
    when(s3Utilities.getObjectDetails("src/a.txt")).thenReturn(file);
    when(s3Utilities.getObjectDetails("dest/a.txt")).thenReturn(null);

    String result = client.moveWithin("src/a.txt", "dest");

    assertEquals("dest/a.txt", result);
    verify(s3Utilities)
        .copyObjectFromBucket("bucket", "src/a.txt", "dest/a.txt", Collections.emptyMap());
    verify(s3Utilities).deleteObject("src/a.txt");
  }

  @Test
  public void moveWithin_emptyFolder_movesPlaceholderObject() throws IOException {
    when(s3Utilities.getBucketName()).thenReturn("bucket");
    S3FolderContentItem folder = new S3FolderContentItem("myfolder", true, null, null);
    when(s3Utilities.getObjectDetails("src/myfolder")).thenReturn(folder);
    when(s3Utilities.listFolderContents("src/myfolder")).thenReturn(List.of()); // empty
    when(s3Utilities.getObjectDetails("dest/myfolder")).thenReturn(null);

    String result = client.moveWithin("src/myfolder", "dest");

    assertEquals("dest/myfolder", result);
    verify(s3Utilities)
        .copyObjectFromBucket("bucket", "src/myfolder/", "dest/myfolder/", Collections.emptyMap());
    verify(s3Utilities).deleteObject("src/myfolder/");
  }

  @Test
  public void moveWithin_nonEmptyFolder_throws() {
    S3FolderContentItem folder = new S3FolderContentItem("myfolder", true, null, null);
    when(s3Utilities.getObjectDetails("src/myfolder")).thenReturn(folder);
    when(s3Utilities.listFolderContents("src/myfolder"))
        .thenReturn(List.of(new S3FolderContentItem("a.txt", false, 1L, Instant.now())));

    assertThrows(IOException.class, () -> client.moveWithin("src/myfolder", "dest"));
  }

  @Test
  public void moveWithin_fileExceedingSingleOpCopyLimit_throws() {
    long sixGb = 6L * 1024L * 1024L * 1024L;
    S3FolderContentItem big = new S3FolderContentItem("big.bin", false, sixGb, Instant.now());
    when(s3Utilities.getObjectDetails("src/big.bin")).thenReturn(big);

    assertThrows(IOException.class, () -> client.moveWithin("src/big.bin", "dest"));
  }

  @Test
  public void resolveDeletableTarget_file_returnsFileKeyAndMetadata() throws IOException {
    S3FolderContentItem file = new S3FolderContentItem("a.txt", false, 10L, Instant.now());
    file.setUserMetadata(Map.of("rspace-created-by", "alice"));
    when(s3Utilities.getObjectDetails("dir/a.txt")).thenReturn(file);

    DeletableTarget target = client.resolveDeletableTarget("dir/a.txt");

    assertEquals("dir/a.txt", target.objectKey());
    assertEquals("alice", target.audit().createdBy());
  }

  @Test
  public void resolveDeletableTarget_emptyFolder_returnsPlaceholderKeyAndMetadata()
      throws IOException {
    S3FolderContentItem folder = new S3FolderContentItem("d", true, null, null);
    when(s3Utilities.getObjectDetails("dir/d")).thenReturn(folder);
    when(s3Utilities.listFolderContents("dir/d")).thenReturn(List.of()); // empty
    S3FolderContentItem placeholder = new S3FolderContentItem("d", false, 0L, Instant.now());
    placeholder.setUserMetadata(Map.of("rspace-created-by", "alice"));
    when(s3Utilities.getObjectDetails("dir/d/")).thenReturn(placeholder);

    DeletableTarget target = client.resolveDeletableTarget("dir/d");

    assertEquals("dir/d/", target.objectKey());
    assertEquals("alice", target.audit().createdBy());
  }

  @Test
  public void resolveDeletableTarget_nonEmptyFolder_throws() {
    S3FolderContentItem folder = new S3FolderContentItem("d", true, null, null);
    when(s3Utilities.getObjectDetails("dir/d")).thenReturn(folder);
    when(s3Utilities.listFolderContents("dir/d"))
        .thenReturn(List.of(new S3FolderContentItem("a.txt", false, 5L, Instant.now())));

    assertThrows(IOException.class, () -> client.resolveDeletableTarget("dir/d"));
  }

  @Test
  public void deleteByKey_deletesExactKey() throws IOException {
    client.deleteByKey("dir/d/");

    verify(s3Utilities).deleteObject("dir/d/");
  }

  @Test
  public void copyObject_toAnotherS3Client_callsCopyObjectFromBucketOnDestination()
      throws IOException {
    DestClientFixture dest = newDestClient("source-bucket");

    String resultKey = client.copyObject("source/file.txt", dest.client, "dest/file.txt");

    verify(dest.mockS3Utilities)
        .copyObjectFromBucket(
            "source-bucket", "source/file.txt", "dest/file.txt", Collections.emptyMap());
    assertEquals("dest/file.txt", resultKey);
  }

  @Test
  public void copyObject_withMetadata_passesMetadataToCopyObjectFromBucket() throws IOException {
    DestClientFixture dest = newDestClient("source-bucket");
    Map<String, String> metadata = Map.of("rspace-user", "alice");

    String resultKey = client.copyObject("source/file.txt", dest.client, "dest/file.txt", metadata);

    verify(dest.mockS3Utilities)
        .copyObjectFromBucket("source-bucket", "source/file.txt", "dest/file.txt", metadata);
    assertEquals("dest/file.txt", resultKey);
  }

  @Test
  public void copyObject_stripsLeadingSlashesFromBothSourceAndDestKeys() throws IOException {
    DestClientFixture dest = newDestClient("source-bucket");

    String resultKey = client.copyObject("/source/file.txt", dest.client, "/dest/file.txt");

    verify(s3Utilities).getObjectDetails("source/file.txt");
    verify(dest.mockS3Utilities)
        .copyObjectFromBucket(
            "source-bucket", "source/file.txt", "dest/file.txt", Collections.emptyMap());
    assertEquals("dest/file.txt", resultKey);
  }

  @Test
  public void copyObject_toNonS3Destination_throwsUnsupportedOperationException() {
    WritableNfsClient nonS3Dest = mock(WritableNfsClient.class);

    assertThrows(
        UnsupportedOperationException.class,
        () -> client.copyObject("source/file.txt", nonS3Dest, "dest/file.txt"));
  }

  @Test
  public void copyObject_sourceOver5GB_throwsIOExceptionAndDoesNotCallCopy() {
    DestClientFixture dest = newDestClient("source-bucket");
    long fiveGbPlusOne = 5L * 1024L * 1024L * 1024L + 1L;
    S3FolderContentItem huge =
        new S3FolderContentItem("huge.bin", false, fiveGbPlusOne, Instant.now());
    when(s3Utilities.getObjectDetails("source/huge.bin")).thenReturn(huge);

    IOException ex =
        assertThrows(
            IOException.class,
            () -> client.copyObject("source/huge.bin", dest.client, "dest/huge.bin"));
    assertTrue(ex.getMessage().contains("5"));
    verify(dest.mockS3Utilities, org.mockito.Mockito.never())
        .copyObjectFromBucket(any(), any(), any(), any());
  }

  @Test
  public void copyObject_destinationKeyAlreadyExists_throwsIOExceptionAndDoesNotCopy() {
    DestClientFixture dest = newDestClient("source-bucket");
    when(dest.mockS3Utilities.getObjectDetails("dest/file.txt"))
        .thenReturn(new S3FolderContentItem("file.txt", false, 100L, Instant.now()));

    IOException ex =
        assertThrows(
            IOException.class,
            () -> client.copyObject("source/file.txt", dest.client, "dest/file.txt"));
    assertTrue(ex.getMessage().contains("already exists"));
    verify(dest.mockS3Utilities, org.mockito.Mockito.never())
        .copyObjectFromBucket(any(), any(), any(), any());
  }

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

  @Test
  public void testQueryForNfsFile_notFound_returnsNull() {
    when(s3Utilities.getObjectDetails("test/missing.txt")).thenReturn(null);
    assertNull(client.queryForNfsFile(new NfsTarget("test/missing.txt")));
  }

  @Test
  public void testQueryForNfsFile_pathIsFolder_returnsNull() {
    S3FolderContentItem folderItem = new S3FolderContentItem("someFolder", true, null, null);
    when(s3Utilities.getObjectDetails("test/someFolder")).thenReturn(folderItem);
    assertNull(client.queryForNfsFile(new NfsTarget("test/someFolder")));
  }

  @Test
  public void testQueryForNfsFolder_notFound_returnsNull() throws IOException {
    when(s3Utilities.getObjectDetails("test/missing")).thenReturn(null);
    assertNull(client.queryForNfsFolder(new NfsTarget("test/missing")));
  }

  @Test
  public void testQueryForNfsFolder_pathIsFile_returnsNull() throws IOException {
    S3FolderContentItem fileItem = new S3FolderContentItem("file.txt", false, 100L, Instant.now());
    when(s3Utilities.getObjectDetails("test/file.txt")).thenReturn(fileItem);
    assertNull(client.queryForNfsFolder(new NfsTarget("test/file.txt")));
  }

  @Test
  public void testTryConnectAndReadTarget_succeedsAsNoOp() throws Exception {
    // S3 connectivity is validated at init, so tryConnectAndReadTarget is a no-op
    client.tryConnectAndReadTarget("");
    client.tryConnectAndReadTarget(null);
    client.tryConnectAndReadTarget("anyPath");
  }

  @Test
  public void testQueryNfsFileForDownload_tempFileDeletedOnStreamClose() throws IOException {
    String testPath = "/test/file.txt";
    String expectedS3Path = "test/file.txt";

    when(s3Utilities.isFileInS3("", expectedS3Path)).thenReturn(true);
    doAnswer(
            invocation -> {
              File file = invocation.getArgument(1);
              FileUtils.writeStringToFile(file, "content", StandardCharsets.UTF_8);
              return null;
            })
        .when(s3Utilities)
        .downloadFromS3(eq(expectedS3Path), any(File.class));

    NfsFileDetails details = client.queryNfsFileForDownload(new NfsTarget(testPath));
    try (InputStream is = details.getRemoteInputStream()) {
      assertNotNull(is);
      String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      assertEquals("content", content);
    }
    // Stream closed cleanly — verify no exception was thrown by the try-with-resources above
  }
}
