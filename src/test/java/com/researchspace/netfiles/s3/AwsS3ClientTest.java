package com.researchspace.netfiles.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.service.aws.impl.S3UtilitiesImpl.S3FolderContentItem;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
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
    testFileStore.setPath("testTopLevelFolder");

    S3FolderContentItem file1 = new S3FolderContentItem("test1.txt", false, 100L, Instant.now());
    when(s3Utilities.listFolderContents("testTopLevelFolder"))
        .thenReturn(Collections.singletonList(file1));

    NfsFileTreeNode rootNode = client.createFileTree(testFileStore.getPath(), null, testFileStore);
    assertNotNull(rootNode);
    assertEquals("testTopLevelFolder", rootNode.getFileName());
    assertTrue(rootNode.getIsFolder());

    assertEquals(1, rootNode.getNodes().size());
    NfsFileTreeNode child = rootNode.getNodes().get(0);
    assertEquals("test1.txt", child.getFileName());
    assertEquals("testTopLevelFolder/test1.txt", child.getNodePath());
    assertEquals("1:/test1.txt", child.getLogicPath());
    assertFalse(child.getIsFolder());
  }
}
