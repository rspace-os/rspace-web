package com.researchspace.service.aws.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemOption;
import com.researchspace.netfiles.WriteAttribution;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.service.aws.impl.S3UtilitiesImpl.S3FolderContentItem;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * The tests running a real connection to AWS and Cloudflare S3 buckets, require bucket details and
 * iam authentication secrets in deployment properties.
 *
 * <p>Test buckets need to contain expected files/folders structure for tests to pass.
 */
@RunWith(ConditionalTestRunner.class)
@Slf4j
public class S3UtilitiesRealConnectionTest extends SpringTransactionalTest {

  @Autowired private S3UtilitiesFactory s3UtilitiesFactory;

  @Value("${s3.realConnectionTest.aws.region}")
  private String awsS3region;

  @Value("${s3.realConnectionTest.aws.bucketName}")
  private String awsS3bucketName;

  @Value("${s3.realConnectionTest.aws.bucketName2}")
  private String awsS3bucketName2;

  @Value("${s3.realConnectionTest.aws.accessKey}")
  private String awsAccessKey;

  @Value("${s3.realConnectionTest.aws.secretKey}")
  private String awsSecretKey;

  @Value("${s3.realConnectionTest.cloudflare.url}")
  private String cloudflareS3Url;

  @Value("${s3.realConnectionTest.cloudflare.bucketName}")
  private String cloudflareS3BucketName;

  @Value("${s3.realConnectionTest.cloudflare.accessKey}")
  private String cloudflareAccessKey;

  @Value("${s3.realConnectionTest.cloudflare.secretKey}")
  private String cloudflareSecretKey;

  private static final int TOP_LEVEL_FOLDER_CONTENT_COUNT = 3;
  private static final String UNIT_TESTS_FOLDER_NAME = "unitTests";
  private static final int UNIT_TESTS_FOLDER_CONTENT_COUNT = 3;
  private static final String UNIT_TESTS_SUBFOLDER_NAME = "unitTests subfolder";
  private static final int UNIT_TESTS_SUBFOLDER_CONTENT_COUNT = 2;
  private static final String S3_WRITE_TESTS_FOLDER_NAME = "s3writeTests";
  private static final String S3_WRITE_TESTS_FOLDER_PATH =
      UNIT_TESTS_FOLDER_NAME + "/" + UNIT_TESTS_SUBFOLDER_NAME + "/" + S3_WRITE_TESTS_FOLDER_NAME;

  private S3Utilities s3Utilities;

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testDownloadFileAWS() throws IOException {
    initializeS3UtilitiesWithAWS();
    downloadFileScenario("testS3File-AWS.txt");
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testListFolderContentAWS() {
    initializeS3UtilitiesWithAWS();
    listFolderContentsScenario("testS3File-AWS.txt");
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testDownloadFileCloudflare() throws IOException {
    initializeS3UtilitiesWithCloudflare();
    downloadFileScenario("testS3File-Cloudflare.txt");
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testListFolderContentCloudflare() {
    initializeS3UtilitiesWithCloudflare();
    listFolderContentsScenario("testS3File-Cloudflare.txt");
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testUploadAndDeleteAWS() throws IOException {
    initializeS3UtilitiesWithAWS();
    uploadAndDeleteScenario();
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testUploadAndDeleteCloudflare() throws IOException {
    initializeS3UtilitiesWithCloudflare();
    uploadAndDeleteScenario();
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testTopLevelRootFilestoreAWS() throws IOException {
    initializeS3UtilitiesWithAWS();
    topLevelRootScenario();
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testTopLevelRootFilestoreCloudflare() throws IOException {
    initializeS3UtilitiesWithCloudflare();
    topLevelRootScenario();
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testCopyObjectFromBucketAWS() throws IOException {
    initializeS3UtilitiesWithAWS();
    copyObjectFromBucketScenario();
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testCopyObjectFromBucketCloudflare() throws IOException {
    initializeS3UtilitiesWithCloudflare();
    copyObjectFromBucketScenario();
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testCreateFolderAndMetadataAWS() {
    initializeS3UtilitiesWithAWS();
    createFolderScenario();
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testCreateFolderAndMetadataCloudflare() {
    initializeS3UtilitiesWithCloudflare();
    createFolderScenario();
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testDeleteEmptyFolderAWS() throws IOException {
    initializeS3UtilitiesWithAWS();
    deleteEmptyFolderScenario();
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testDeleteEmptyFolderCloudflare() throws IOException {
    initializeS3UtilitiesWithCloudflare();
    deleteEmptyFolderScenario();
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testCrossBucketCopyObjectAWS() throws IOException {
    // Destination = primary AWS bucket (test instance). Source = rspace-s3-integration-dev,
    // object at unit_test_do_not_remove/transfer_test.txt (content: "s3 transfer test 1").
    initializeS3UtilitiesWithAWS();

    String sourceBucket = awsS3bucketName2;
    String sourceKey = "unit_test_do_not_remove/transfer_test.txt";
    String expectedContent = "s3 transfer test 1";
    String destFileName = "cross-bucket-copy-" + System.currentTimeMillis() + ".txt";
    String destKey = S3_WRITE_TESTS_FOLDER_PATH + "/" + destFileName;

    try {
      s3Utilities.copyObjectFromBucket(sourceBucket, sourceKey, destKey);

      // verify the copy landed in the primary bucket with correct size
      S3FolderContentItem dest = s3Utilities.getObjectDetails(destKey);
      assertTrue("cross-bucket copy destination should exist", dest != null && !dest.isFolder());
      assertEquals(
          "cross-bucket copy size mismatch",
          Long.valueOf(expectedContent.getBytes().length),
          dest.getSizeInBytes());

      // download and verify content matches the source file
      File roundTrip = File.createTempFile("cross-bucket-copy", ".txt");
      try {
        s3Utilities.downloadFromS3(destKey, roundTrip);
        assertEquals(expectedContent, Files.readString(roundTrip.toPath()));
      } finally {
        roundTrip.delete();
      }
    } finally {
      safeDeleteFromS3(S3_WRITE_TESTS_FOLDER_PATH, destFileName);
    }
  }

  private void initializeS3UtilitiesWithAWS() {
    NfsFileSystem testFileSystem = new NfsFileSystem();
    testFileSystem.setClientOption(NfsFileSystemOption.S3_REGION, awsS3region);
    testFileSystem.setClientOption(NfsFileSystemOption.S3_BUCKET_NAME, awsS3bucketName);

    // switch from default aws credentials, if provided
    if (StringUtils.isNotEmpty(awsAccessKey)) {
      s3UtilitiesFactory.updateNfsS3Credentials(awsAccessKey, awsSecretKey);
    }

    s3Utilities = s3UtilitiesFactory.createS3UtilitiesForNfsConnector(testFileSystem);
  }

  private void initializeS3UtilitiesWithCloudflare() {
    NfsFileSystem testFileSystem = new NfsFileSystem();
    testFileSystem.setUrl(cloudflareS3Url);
    testFileSystem.setClientOption(NfsFileSystemOption.S3_REGION, "auto");
    testFileSystem.setClientOption(NfsFileSystemOption.S3_BUCKET_NAME, cloudflareS3BucketName);

    // switch from default cloudflare credentials, if provided
    if (StringUtils.isNotEmpty(cloudflareAccessKey)) {
      s3UtilitiesFactory.updateNfsS3Credentials(cloudflareAccessKey, cloudflareSecretKey);
    }

    s3Utilities = s3UtilitiesFactory.createS3UtilitiesForNfsConnector(testFileSystem);
  }

  private void downloadFileScenario(String testTxtFilename) throws IOException {
    File tmpFile = File.createTempFile("downloaded", ".tmp");

    // download txt file from the top folder
    s3Utilities.downloadFromS3(UNIT_TESTS_FOLDER_NAME + "/" + testTxtFilename, tmpFile);
    String downloadedContent = Files.readString(tmpFile.toPath());
    assertEquals("testS3Content", downloadedContent);

    // download img file from subfolder
    s3Utilities.downloadFromS3(
        UNIT_TESTS_FOLDER_NAME + "/" + UNIT_TESTS_SUBFOLDER_NAME + "/" + "test image.png", tmpFile);
    assertEquals(168434L, tmpFile.length());

    // cleanup
    tmpFile.delete();
  }

  private void listFolderContentsScenario(String testTxtFilename) {

    // check the root folder
    List<S3FolderContentItem> items = s3Utilities.listFolderContents("");
    assertEquals(TOP_LEVEL_FOLDER_CONTENT_COUNT, items.size());

    // check the top-level unit tests folder
    items = s3Utilities.listFolderContents(UNIT_TESTS_FOLDER_NAME);
    assertEquals(UNIT_TESTS_FOLDER_CONTENT_COUNT, items.size());

    // find the expected test file and verify its size
    Long testS3TxtFileSize = 13L;
    Optional<S3FolderContentItem> testS3File =
        items.stream()
            .filter(item -> item.getName().equals(testTxtFilename) && !item.isFolder())
            .findFirst();
    assertTrue("Expected to find file 'testS3File.txt'", testS3File.isPresent());
    assertEquals(
        "Unexpected testS3File.txt size", testS3TxtFileSize, testS3File.get().getSizeInBytes());
    assertEquals(
        LocalDate.of(2026, 5, 20),
        testS3File.get().getLastModified().atZone(ZoneOffset.UTC).toLocalDate());

    // find the subfolder
    Optional<S3FolderContentItem> subfolder =
        items.stream()
            .filter(item -> item.getName().equals(UNIT_TESTS_SUBFOLDER_NAME) && item.isFolder())
            .findFirst();
    assertTrue("Expected to find subfolder 'unitTests subfolder'", subfolder.isPresent());

    // check the content of the subfolder
    items =
        s3Utilities.listFolderContents(UNIT_TESTS_FOLDER_NAME + "/" + UNIT_TESTS_SUBFOLDER_NAME);
    assertEquals(UNIT_TESTS_SUBFOLDER_CONTENT_COUNT, items.size());

    // find the expected test image in subfolder
    String testS3PngFile = "test image.png";
    Long testS3PngFileSize = 168434L;
    testS3File = items.stream().filter(item -> item.getName().equals(testS3PngFile)).findFirst();
    assertTrue("Expected to find file 'test image.png'", testS3File.isPresent());
    assertEquals(
        "Unexpected test image size", testS3PngFileSize, testS3File.get().getSizeInBytes());
  }

  private void uploadAndDeleteScenario() throws IOException {
    String testContent = "real-connection-upload-test";
    String fileName = "upload-test-" + System.currentTimeMillis() + ".txt";
    File source = createTempFileWithName(fileName, testContent);
    String s3Key = S3_WRITE_TESTS_FOLDER_PATH + "/" + fileName;
    Map<String, String> metadata =
        Map.of(
            WriteAttribution.META_CREATED_BY,
            "realConnTestUser",
            WriteAttribution.META_CREATED_AT,
            Instant.now().toString());

    try {
      s3Utilities.uploadToS3(S3_WRITE_TESTS_FOLDER_PATH, source, metadata);

      // verify object is present with correct size
      S3FolderContentItem uploaded = s3Utilities.getObjectDetails(s3Key);
      assertTrue("uploaded object should exist", uploaded != null && !uploaded.isFolder());
      assertEquals(
          "uploaded object size mismatch",
          Long.valueOf(testContent.getBytes().length),
          uploaded.getSizeInBytes());

      // verify the user metadata round-trips (the path uploadFromGallery uses for audit metadata)
      assertEquals(
          "uploaded object should carry creator metadata",
          "realConnTestUser",
          uploaded.getUserMetadata().get(WriteAttribution.META_CREATED_BY));
      assertEquals(
          "uploaded object should carry creation-time metadata",
          metadata.get(WriteAttribution.META_CREATED_AT),
          uploaded.getUserMetadata().get(WriteAttribution.META_CREATED_AT));

      // round-trip: download and verify content
      File roundTrip = File.createTempFile("upload-roundtrip", ".txt");
      try {
        s3Utilities.downloadFromS3(s3Key, roundTrip);
        assertEquals(testContent, Files.readString(roundTrip.toPath()));
      } finally {
        roundTrip.delete();
      }
    } finally {
      safeDeleteFromS3(S3_WRITE_TESTS_FOLDER_PATH, fileName);
      source.delete();
    }

    // verify object is gone after delete
    assertTrue(
        "object should no longer exist after delete", s3Utilities.getObjectDetails(s3Key) == null);
  }

  private void copyObjectFromBucketScenario() throws IOException {
    // upload a source object inside s3writeTests, then exercise CopyObject as a same-bucket
    // server-side copy (sourceBucket == this instance's bucket).
    String testContent = "real-connection-copy-test";
    String sourceFileName = "copy-source-" + System.currentTimeMillis() + ".txt";
    String destFileName = "copy-dest-" + System.currentTimeMillis() + ".txt";
    File source = createTempFileWithName(sourceFileName, testContent);
    String sourceKey = S3_WRITE_TESTS_FOLDER_PATH + "/" + sourceFileName;
    String destKey = S3_WRITE_TESTS_FOLDER_PATH + "/" + destFileName;
    Map<String, String> sourceMetadata =
        Map.of(
            WriteAttribution.META_CREATED_BY,
            "realConnTestUser",
            WriteAttribution.META_CREATED_AT,
            Instant.now().toString());

    try {
      s3Utilities.uploadToS3(S3_WRITE_TESTS_FOLDER_PATH, source, sourceMetadata);

      s3Utilities.copyObjectFromBucket(s3Utilities.getBucketName(), sourceKey, destKey);

      // both source and dest should exist after CopyObject
      S3FolderContentItem destDetails = s3Utilities.getObjectDetails(destKey);
      assertTrue("copy destination should exist", destDetails != null && !destDetails.isFolder());
      assertEquals(
          "copy destination size mismatch",
          Long.valueOf(testContent.getBytes().length),
          destDetails.getSizeInBytes());
      assertTrue(
          "copy source should still exist (CopyObject does not delete source)",
          s3Utilities.getObjectDetails(sourceKey) != null);

      // a no-metadata copy uses S3's COPY directive, which PRESERVES source metadata. The
      // within-filestore move relies on this so a moved item keeps its original created-by/-at.
      assertEquals(
          "server-side copy with no metadata should preserve source creator metadata",
          "realConnTestUser",
          destDetails.getUserMetadata().get(WriteAttribution.META_CREATED_BY));
      assertEquals(
          "server-side copy with no metadata should preserve source creation-time metadata",
          sourceMetadata.get(WriteAttribution.META_CREATED_AT),
          destDetails.getUserMetadata().get(WriteAttribution.META_CREATED_AT));
    } finally {
      safeDeleteFromS3(S3_WRITE_TESTS_FOLDER_PATH, sourceFileName);
      safeDeleteFromS3(S3_WRITE_TESTS_FOLDER_PATH, destFileName);
      source.delete();
    }
  }

  /**
   * Creates a folder placeholder carrying audit metadata, verifies it is discoverable as a folder
   * and that the placeholder object round-trips the {@code rspace-created-by}/{@code
   * rspace-created-at} metadata, then deletes it via batch delete.
   */
  private void createFolderScenario() {
    String folderPath = S3_WRITE_TESTS_FOLDER_PATH + "/createFolder-" + System.currentTimeMillis();
    String placeholderKey = folderPath + "/";
    Map<String, String> metadata =
        Map.of(
            WriteAttribution.META_CREATED_BY,
            "realConnTestUser",
            WriteAttribution.META_CREATED_AT,
            Instant.now().toString());
    try {
      s3Utilities.createFolder(folderPath, metadata);

      // discoverable as a (virtual) folder
      S3FolderContentItem folder = s3Utilities.getObjectDetails(folderPath);
      assertTrue("created folder should be detected", folder != null && folder.isFolder());

      // the placeholder object itself carries the audit metadata
      S3FolderContentItem placeholder = s3Utilities.getObjectDetails(placeholderKey);
      assertTrue("folder placeholder object should exist", placeholder != null);
      assertEquals(
          "realConnTestUser", placeholder.getUserMetadata().get(WriteAttribution.META_CREATED_BY));
      assertTrue(
          "created-at metadata should be present",
          placeholder.getUserMetadata().containsKey(WriteAttribution.META_CREATED_AT));
    } finally {
      safeDeleteObject(placeholderKey);
    }
    assertTrue(
        "folder placeholder should be gone after delete",
        s3Utilities.getObjectDetails(placeholderKey) == null);
  }

  /**
   * Creates an (empty) folder, confirms it lists as empty (the basis for allowing its deletion),
   * then removes its single placeholder object with {@link S3Utilities#deleteObject(String)}.
   */
  private void deleteEmptyFolderScenario() throws IOException {
    long ts = System.currentTimeMillis();
    String base = S3_WRITE_TESTS_FOLDER_PATH + "/emptydel-" + ts;
    String placeholderKey = base + "/";
    Map<String, String> metadata =
        Map.of(
            WriteAttribution.META_CREATED_BY,
            "realConnTestUser",
            WriteAttribution.META_CREATED_AT,
            Instant.now().toString());
    try {
      s3Utilities.createFolder(base, metadata);
      assertTrue(
          "a newly created folder should be empty", s3Utilities.listFolderContents(base).isEmpty());

      // an empty folder is a single placeholder object; delete it by its exact key
      s3Utilities.deleteObject(placeholderKey);

      assertTrue("folder should be gone after delete", s3Utilities.getObjectDetails(base) == null);
    } finally {
      safeDeleteObject(placeholderKey);
    }
  }

  /**
   * Exercises a filestore rooted at the bucket top level (empty base path): a folder created with
   * no parent prefix lands at the bucket root, is discoverable when listing "", and can be deleted.
   */
  private void topLevelRootScenario() throws IOException {
    String folderName = "rootleveltest-" + System.currentTimeMillis();
    String placeholderKey = folderName + "/";
    Map<String, String> metadata =
        Map.of(
            WriteAttribution.META_CREATED_BY,
            "realConnTestUser",
            WriteAttribution.META_CREATED_AT,
            Instant.now().toString());
    try {
      // no parent prefix -> created at the bucket root
      s3Utilities.createFolder(folderName, metadata);

      S3FolderContentItem folder = s3Utilities.getObjectDetails(folderName);
      assertTrue("top-level folder should be detected", folder != null && folder.isFolder());

      boolean inRoot =
          s3Utilities.listFolderContents("").stream()
              .anyMatch(item -> item.getName().equals(folderName) && item.isFolder());
      assertTrue("created folder should appear in the bucket-root listing", inRoot);
    } finally {
      safeDeleteObject(placeholderKey);
    }
    assertTrue(
        "top-level folder should be gone after delete",
        s3Utilities.getObjectDetails(folderName) == null);
  }

  /** Best-effort delete by exact key used in test cleanup; never throws (safe in a finally). */
  private void safeDeleteObject(String key) {
    try {
      s3Utilities.deleteObject(key);
    } catch (Exception e) {
      log.warn("cleanup delete failed for {}", key, e);
    }
  }

  /** Best-effort delete used in test cleanup; never throws so it can sit in a finally block. */
  private void safeDeleteFromS3(String folderPath, String fileName) {
    try {
      s3Utilities.deleteFromS3(folderPath, fileName);
    } catch (Exception e) {
      log.warn("cleanup delete failed for {}/{}", folderPath, fileName, e);
    }
  }

  /**
   * Creates a temp file whose simple name equals {@code fileName} (so the resulting S3 object key
   * matches the desired suffix), pre-populated with {@code content}.
   */
  private static File createTempFileWithName(String fileName, String content) throws IOException {
    File scratch = File.createTempFile("s3-test-", ".tmp");
    Files.writeString(scratch.toPath(), content);
    File named = new File(scratch.getParentFile(), fileName);
    assertTrue("temp file rename failed", scratch.renameTo(named));
    return named;
  }
}
