package com.researchspace.service.aws.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
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
  public void buildKeyFromFilePath_blankArchivePath_returnsFilenameOnly() {
    File file = new File("my_audio.wav");
    S3PutUploader uploader = new S3PutUploader(null, "bucket", "");
    assertEquals("my_audio.wav", uploader.buildKeyFromFilePath(file));
  }

  @Test
  public void buildKeyFromFilePath_nonBlankArchivePath_prependsPathWithSlash() {
    File file = new File("my_audio.wav");
    S3PutUploader uploader = new S3PutUploader(null, "bucket", "uploads/2024");
    assertEquals("uploads/2024/my_audio.wav", uploader.buildKeyFromFilePath(file));
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
    Map<String, String> metadata = Map.of("rspace-user", "alice", "rspace-record-id", "42");

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
  public void listFolderContents_surfacesEtagAndStorageClass() {
    S3Client mockS3Client = mock(S3Client.class);
    S3UtilitiesImpl impl = s3UtilitiesWithMockClient(mockS3Client, "test-bucket");

    S3Object file =
        S3Object.builder()
            .key("folder/data.dat")
            .size(2202009L)
            .lastModified(Instant.now())
            .eTag("\"b2c3d4e5f6789012\"")
            .storageClass("STANDARD")
            .build();
    ListObjectsV2Response response =
        ListObjectsV2Response.builder().contents(file).isTruncated(false).build();
    when(mockS3Client.listObjectsV2(argThat((ListObjectsV2Request r) -> r != null)))
        .thenReturn(response);

    List<S3FolderContentItem> items = impl.listFolderContents("folder");

    assertEquals(1, items.size());
    assertEquals("\"b2c3d4e5f6789012\"", items.get(0).getEtag());
    assertEquals("STANDARD", items.get(0).getStorageClass());
  }

  @Test
  public void listFolderContents_emptyPath_listsBucketRootWithEmptyPrefix() {
    // A filestore rooted at the bucket top level has an empty path; listing it must query S3 with
    // an empty prefix (the bucket root) and surface top-level files and folders.
    S3Client mockS3Client = mock(S3Client.class);
    S3UtilitiesImpl impl = s3UtilitiesWithMockClient(mockS3Client, "test-bucket");

    S3Object rootFile =
        S3Object.builder().key("file-at-root.txt").size(10L).lastModified(Instant.now()).build();
    CommonPrefix topFolder = CommonPrefix.builder().prefix("topfolder/").build();
    ListObjectsV2Response response =
        ListObjectsV2Response.builder()
            .contents(rootFile)
            .commonPrefixes(topFolder)
            .isTruncated(false)
            .build();
    when(mockS3Client.listObjectsV2(
            argThat(
                (ListObjectsV2Request r) ->
                    r != null && "".equals(r.prefix()) && "/".equals(r.delimiter()))))
        .thenReturn(response);

    List<S3FolderContentItem> items = impl.listFolderContents("");

    assertEquals(2, items.size());
    // common prefixes (folders) come before files, per implementation order
    assertEquals("topfolder", items.get(0).getName());
    assertTrue(items.get(0).isFolder());
    assertEquals("file-at-root.txt", items.get(1).getName());
    assertFalse(items.get(1).isFolder());
  }

  @Test
  public void getObjectDetails_blankPath_returnsBucketRootAsFolder() {
    // The bucket root (empty path) is a valid folder target, without an S3 call.
    S3UtilitiesImpl impl = s3UtilitiesWithMockClient(mock(S3Client.class), "test-bucket");

    S3FolderContentItem root = impl.getObjectDetails("");

    assertTrue(root.isFolder());
    assertEquals("", root.getName());
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
  public void getObjectDetails_exposesUserMetadataFromHeadObject() {
    S3Client mockS3Client = mock(S3Client.class);
    S3UtilitiesImpl impl = s3UtilitiesWithMockClient(mockS3Client, "test-bucket");
    Map<String, String> meta =
        Map.of("rspace-created-by", "alice", "rspace-created-at", "2026-06-18T10:00:00Z");
    when(mockS3Client.headObject(
            argThat(
                (HeadObjectRequest r) ->
                    r != null
                        && "test-bucket".equals(r.bucket())
                        && "folder/file.txt".equals(r.key()))))
        .thenReturn(HeadObjectResponse.builder().contentLength(10L).metadata(meta).build());

    assertEquals(meta, impl.getObjectDetails("folder/file.txt").getUserMetadata());
  }

  @Test
  public void createFolder_putsZeroByteObjectWithTrailingSlashAndMetadata() {
    S3Client mockS3Client = mock(S3Client.class);
    S3UtilitiesImpl impl = s3UtilitiesWithMockClient(mockS3Client, "test-bucket");
    Map<String, String> meta =
        Map.of("rspace-created-by", "alice", "rspace-created-at", "2026-06-18T10:00:00Z");

    impl.createFolder("parent/newfolder", meta);

    verify(mockS3Client)
        .putObject(
            argThat(
                (PutObjectRequest r) ->
                    r != null
                        && "test-bucket".equals(r.bucket())
                        && "parent/newfolder/".equals(r.key())
                        && meta.equals(r.metadata())),
            argThat(
                (RequestBody body) ->
                    body != null && body.optionalContentLength().orElse(-1L) == 0L));
  }

  @Test
  public void createFolder_appendsSingleTrailingSlashWhenAlreadyPresent() {
    S3Client mockS3Client = mock(S3Client.class);
    S3UtilitiesImpl impl = s3UtilitiesWithMockClient(mockS3Client, "test-bucket");

    impl.createFolder("parent/already/", Map.of());

    verify(mockS3Client)
        .putObject(
            argThat((PutObjectRequest r) -> r != null && "parent/already/".equals(r.key())),
            any(RequestBody.class));
  }

  @Test
  public void deleteObject_issuesDeleteObjectRequestWithExactKey() {
    S3Client mockS3Client = mock(S3Client.class);
    S3UtilitiesImpl impl = s3UtilitiesWithMockClient(mockS3Client, "test-bucket");

    impl.deleteObject("folder/placeholder/");

    verify(mockS3Client)
        .deleteObject(
            argThat(
                (DeleteObjectRequest r) ->
                    r != null
                        && "test-bucket".equals(r.bucket())
                        && "folder/placeholder/".equals(r.key())));
  }

  @Test
  public void deleteFromS3_joinsFolderAndFileAndDelegatesToDeleteObject() {
    S3Client mockS3Client = mock(S3Client.class);
    S3UtilitiesImpl impl = s3UtilitiesWithMockClient(mockS3Client, "test-bucket");

    impl.deleteFromS3("folder/sub", "file.txt");

    verify(mockS3Client)
        .deleteObject(
            argThat(
                (DeleteObjectRequest r) ->
                    r != null
                        && "test-bucket".equals(r.bucket())
                        && "folder/sub/file.txt".equals(r.key())));
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
    Map<String, String> metadata = Map.of("rspace-user", "alice");

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
