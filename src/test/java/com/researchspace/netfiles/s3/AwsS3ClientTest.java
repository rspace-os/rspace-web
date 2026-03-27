package com.researchspace.netfiles.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.service.aws.impl.S3UtilitiesImpl.S3FolderContentItem;
import java.time.Instant;
import org.junit.jupiter.api.Test;

public class AwsS3ClientTest {

  private final AwsS3Client client = new AwsS3Client("testUser", null);

  @Test
  public void testNfsFileTreeNodeConversion() {

    NfsFileStore testFileStore = new NfsFileStore();
    testFileStore.setId(1L);
    testFileStore.setPath("");

    S3FolderContentItem fileResource =
        new S3FolderContentItem("test.txt", false, 12L, Instant.now());
    NfsFileTreeNode nodeForFileResource =
        client.getNodeFromS3Item(fileResource, null, testFileStore);
    assertEquals("test.txt", nodeForFileResource.getFileName());
    assertFalse(nodeForFileResource.getIsFolder());
    assertEquals("1:test.txt", nodeForFileResource.getLogicPath());
    assertEquals("12", nodeForFileResource.getFileSize());
    assertEquals(12L, nodeForFileResource.getFileSizeBytes());
    assertNotNull(nodeForFileResource.getModificationDateMillis());

    S3FolderContentItem folderResource = new S3FolderContentItem("test2.txt", true, null, null);
    NfsFileTreeNode nodeForFolderResource =
        client.getNodeFromS3Item(folderResource, null, testFileStore);
    assertEquals("test2.txt", nodeForFolderResource.getFileName());
    assertTrue(nodeForFolderResource.getIsFolder());
    assertEquals("1:test2.txt", nodeForFolderResource.getLogicPath());
    assertEquals("0", nodeForFolderResource.getFileSize());
    assertNull(nodeForFolderResource.getFileSizeBytes());
    assertNull(nodeForFolderResource.getModificationDateMillis());
  }
}
