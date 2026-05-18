package com.researchspace.service.aws.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.service.aws.impl.S3UtilitiesImpl.S3FolderContentItem;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
public class S3UtilitiesTest {

  /** Builds an S3UtilitiesImpl wired with a mock S3Client and the given bucket name. */
  private static S3UtilitiesImpl s3UtilitiesWithMockClient(S3Client mockClient, String bucketName) {
    S3UtilitiesImpl impl = new S3UtilitiesImpl();
    impl.setS3Client(mockClient);
    impl.setBucketName(bucketName);
    return impl;
  }

  @Test
  public void getUploadStrategyForFileSize() {
    S3UtilitiesImpl impl = new S3UtilitiesImpl();
    impl.setChunkedUploadMbThreshold(1);
    impl.setChunkedUploadMbSize(5);
    File subThreshold = RSpaceTestUtils.getResource("adrenaline.smiles"); // 90bytes
    assertTrue(impl.getS3Uploader(null, subThreshold) instanceof S3PutUploader);

    File requiresChunking = RSpaceTestUtils.getResource("weather_data2.csv"); // 7 Mb
    assertTrue(impl.getS3Uploader(null, requiresChunking) instanceof S3MultipartChunkedUploader);
  }

  @Test
  public void getS3Uploader_withMetadata_propagatesMetadataToUploader() {
    S3UtilitiesImpl impl = new S3UtilitiesImpl();
    impl.setChunkedUploadMbThreshold(1);
    impl.setChunkedUploadMbSize(5);
    File subThreshold = RSpaceTestUtils.getResource("adrenaline.smiles");
    File requiresChunking = RSpaceTestUtils.getResource("weather_data2.csv");
    Map<String, String> metadata = Map.of("rspace-user", "alice", "rspace-op", "copy");

    S3PutUploader putUploader = (S3PutUploader) impl.getS3Uploader(null, subThreshold, metadata);
    S3MultipartChunkedUploader chunkedUploader =
        (S3MultipartChunkedUploader) impl.getS3Uploader(null, requiresChunking, metadata);

    assertEquals(metadata, putUploader.getObjectMetadata());
    assertEquals(metadata, chunkedUploader.getObjectMetadata());
  }

  @Test
  public void listFolderContents_paginatesWhenTruncated() {
    S3Client mockS3Client = mock(S3Client.class);
    S3UtilitiesImpl impl = s3UtilitiesWithMockClient(mockS3Client, "test-bucket");

    S3Object file1 =
        S3Object.builder().key("folder/file1.txt").size(100L).lastModified(Instant.now()).build();
    S3Object file2 =
        S3Object.builder().key("folder/file2.txt").size(200L).lastModified(Instant.now()).build();
    CommonPrefix subFolder = CommonPrefix.builder().prefix("folder/subfolder/").build();

    ListObjectsV2Response firstPage =
        ListObjectsV2Response.builder()
            .contents(file1)
            .isTruncated(true)
            .nextContinuationToken("page2token")
            .build();
    ListObjectsV2Response secondPage =
        ListObjectsV2Response.builder()
            .contents(file2)
            .commonPrefixes(subFolder)
            .isTruncated(false)
            .build();

    when(mockS3Client.listObjectsV2(
            argThat(
                (ListObjectsV2Request r) ->
                    r != null && r.prefix().equals("folder/") && r.continuationToken() == null)))
        .thenReturn(firstPage);
    when(mockS3Client.listObjectsV2(
            argThat(
                (ListObjectsV2Request r) ->
                    r != null
                        && r.prefix().equals("folder/")
                        && "page2token".equals(r.continuationToken()))))
        .thenReturn(secondPage);

    List<S3FolderContentItem> items = impl.listFolderContents("folder");

    assertEquals(3, items.size());
    // Page 1: file1.txt
    assertEquals("file1.txt", items.get(0).getName());
    assertFalse(items.get(0).isFolder());
    // Page 2: subfolder (common prefix) comes before file, per implementation order
    assertEquals("subfolder", items.get(1).getName());
    assertTrue(items.get(1).isFolder());
    assertEquals("file2.txt", items.get(2).getName());
    assertFalse(items.get(2).isFolder());
  }

  @Test
  public void copyObjectFromBucket_issuesS3CopyObjectRequest() {
    S3Client mockS3Client = mock(S3Client.class);
    S3UtilitiesImpl impl = s3UtilitiesWithMockClient(mockS3Client, "dest-bucket");

    impl.copyObjectFromBucket("source-bucket", "src/file.txt", "dst/file.txt");

    verify(mockS3Client)
        .copyObject(
            argThat(
                (CopyObjectRequest r) ->
                    r != null
                        && "source-bucket".equals(r.sourceBucket())
                        && "src/file.txt".equals(r.sourceKey())
                        && "dest-bucket".equals(r.destinationBucket())
                        && "dst/file.txt".equals(r.destinationKey())));
  }

  @Test
  public void getBucketName_returnsConfiguredBucket() {
    S3UtilitiesImpl impl = new S3UtilitiesImpl();
    impl.setBucketName("my-bucket");
    assertEquals("my-bucket", impl.getBucketName());
  }

  @Test
  public void copyObjectFromBucket_withMetadata_attachesMetadataAndReplaceDirective() {
    S3Client mockS3Client = mock(S3Client.class);
    S3UtilitiesImpl impl = s3UtilitiesWithMockClient(mockS3Client, "dest-bucket");
    Map<String, String> metadata = Map.of("rspace-user", "alice", "rspace-op", "transfer");

    impl.copyObjectFromBucket("source-bucket", "src/file.txt", "dst/file.txt", metadata);

    verify(mockS3Client)
        .copyObject(
            argThat(
                (CopyObjectRequest r) ->
                    r != null
                        && metadata.equals(r.metadata())
                        && r.metadataDirective() == MetadataDirective.REPLACE));
  }

  @Test
  public void copyObjectFromBucket_withEmptyMetadata_doesNotSetReplaceDirective() {
    // Empty metadata should preserve the source object's metadata (S3 default COPY directive).
    S3Client mockS3Client = mock(S3Client.class);
    S3UtilitiesImpl impl = s3UtilitiesWithMockClient(mockS3Client, "dest-bucket");

    impl.copyObjectFromBucket("source-bucket", "src/file.txt", "dst/file.txt", Map.of());

    verify(mockS3Client)
        .copyObject(
            argThat(
                (CopyObjectRequest r) ->
                    r != null && r.metadataDirective() != MetadataDirective.REPLACE));
  }
}
