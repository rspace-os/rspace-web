package com.researchspace.service.aws.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemOption;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.service.aws.impl.S3UtilitiesImpl.S3FolderContentItem;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * The tests running a real connection to AWS and Cloudflare S3 buckets,
 * require bucket details and iam authentication secrets in deployment properties.
 */
@RunWith(ConditionalTestRunner.class)
@Slf4j
public class S3UtilitiesRealConnectionTest extends SpringTransactionalTest {

  @Autowired private S3UtilitiesFactory s3UtilitiesFactory;

  @Value("${s3.realConnectionTest.aws.region}")
  private String awsS3region;

  @Value("${s3.realConnectionTest.aws.bucketName}")
  private String awsS3bucketName;

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

  private static final int TOP_LEVEL_FOLDER_CONTENT_COUNT = 2;
  private static final String UNIT_TESTS_FOLDER_NAME = "unitTests";
  private static final int UNIT_TESTS_FOLDER_CONTENT_COUNT = 3;
  private static final String UNIT_TESTS_SUBFOLDER_NAME = "unitTests subfolder";
  private static final int UNIT_TESTS_SUBFOLDER_CONTENT_COUNT = 1;

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
  @RunIfSystemPropertyDefined(value = "nightly-s3cloudflare")
  public void testDownloadFileCloudflare() throws IOException {
    initializeS3UtilitiesWithCloudflare();
    downloadFileScenario("testS3File-Cloudflare.txt");
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly-s3cloudflare")
  public void testListFolderContentCloudflare() {
    initializeS3UtilitiesWithCloudflare();
    listFolderContentsScenario("testS3File-Cloudflare.txt");
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
      s3UtilitiesFactory.updateNfsS3Credentials(cloudflareAccessKey,cloudflareSecretKey);
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
        LocalDate.of(2026, 4, 17),
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

}
