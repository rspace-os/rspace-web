package com.researchspace.service.aws;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.service.aws.impl.S3UtilitiesImpl;
import com.researchspace.service.aws.impl.S3UtilitiesImpl.S3FolderContentItem;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * The tests running a real connection to AWS S3 bucket.
 *
 * <p>The test bucket to use is configured through deployment properties, and must be accessible by
 * user who runs the test (e.g. through access key, and secret, added to '~/.aws/config' file).
 */
@RunWith(ConditionalTestRunner.class)
@Slf4j
public class S3UtilitiesRealConnectionTest extends SpringTransactionalTest {

  @Value("${s3.realConnectionTest.region}")
  private String s3region;

  @Value("${s3.realConnectionTest.bucketName}")
  private String s3bucketName;

  private static final String TEST_ARCHIVE_PATH = "aws-export-test";
  private static final String TEST_FILE_NAME = "demo-export2.zip";


  private static final String TEST_UNIT_TESTS_PATH = "unitTests";
  private static final int TEST_UNIT_TESTS_CONTENT_COUNT = 2;
  private static final String TEST_UNIT_TESTS_SUBFOLDER_NAME = "unitTests subfolder";
  private static final int TEST_UNIT_TESTS_SUBFOLDER_CONTENT_COUNT = 1;

  private S3UtilitiesImpl s3Utilities;

  private File archiveToExport;

  @Before
  public void setup() {
    s3Utilities = new S3UtilitiesImpl();
    ReflectionTestUtils.setField(s3Utilities, "region", s3region);
    ReflectionTestUtils.setField(s3Utilities, "s3BucketName", s3bucketName);
    ReflectionTestUtils.setField(s3Utilities, "s3ArchivePath", TEST_ARCHIVE_PATH);
    ReflectionTestUtils.setField(s3Utilities, "archiveFolderStorageTime", 1);
    s3Utilities.init();

    archiveToExport = RSpaceTestUtils.getResource("archives/demo-export2.zip");
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testFileNotInS3() {
    assertFalse(s3Utilities.isArchiveInS3("fileNotInS3.zip"));
  }

  @Test(expected = SdkClientException.class)
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testIsArchiveInS3BucketNameNull() {
    ReflectionTestUtils.setField(s3Utilities, "s3BucketName", null);
    s3Utilities.isArchiveInS3(TEST_FILE_NAME);
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testUpload() {
    s3Utilities.uploadToS3(archiveToExport);
    assertTrue(s3Utilities.isArchiveInS3(TEST_FILE_NAME));
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testPresignedURL() {
    s3Utilities.uploadToS3(archiveToExport);
    URL url = s3Utilities.getPresignedUrlForArchiveDownload(TEST_FILE_NAME);
    assertNotNull(url);
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testPresignedURLFileDoesNotExist() {
    assertNull(s3Utilities.getPresignedUrlForArchiveDownload("doesNotExist.zip"));
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testDownloadFile() throws IOException {
    File tmpFile = File.createTempFile("downloaded", ".tmp");

    // download txt file from the top folder
    s3Utilities.downloadFromS3(TEST_UNIT_TESTS_PATH  + "/" + "testS3File.txt", tmpFile);
    String downloadedContent = Files.readString(tmpFile.toPath());
    assertEquals("testS3Content", downloadedContent);

    // download img file from subfolder
    s3Utilities.downloadFromS3(TEST_UNIT_TESTS_PATH  + "/"
        + TEST_UNIT_TESTS_SUBFOLDER_NAME + "/" + "test image.png", tmpFile);
    assertEquals(168434L, tmpFile.length());

    // cleanup
    tmpFile.delete();
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testListFolderContents() {

    // check the top-level unit tests folder
    List<S3FolderContentItem> items = s3Utilities.listFolderContents(TEST_UNIT_TESTS_PATH);
    assertEquals(TEST_UNIT_TESTS_CONTENT_COUNT, items.size());

    // find the expected test file and verify its size
    String testS3TxtFile = "testS3File.txt";
    Long testS3TxtFileSize = 13L;
    Optional<S3FolderContentItem> testS3File = items.stream()
        .filter(item -> item.getName().equals(testS3TxtFile) && !item.isFolder())
        .findFirst();
    assertTrue("Expected to find file 'testS3File.txt'", testS3File.isPresent());
    assertEquals("Unexpected testS3File.txt size", testS3TxtFileSize, testS3File.get().getSizeInBytes());

    // find the subfolder
    Optional<S3FolderContentItem> subfolder = items.stream()
        .filter(item -> item.getName().equals(TEST_UNIT_TESTS_SUBFOLDER_NAME) && item.isFolder())
        .findFirst();
    assertTrue("Expected to find subfolder 'unitTests subfolder'", subfolder.isPresent());

    // check the content of the subfolder
    items = s3Utilities.listFolderContents(TEST_UNIT_TESTS_PATH + "/" + TEST_UNIT_TESTS_SUBFOLDER_NAME);
    assertEquals(TEST_UNIT_TESTS_SUBFOLDER_CONTENT_COUNT, items.size());

    // find the expected test image in subfolder
    String testS3PngFile = "test image.png";
    Long testS3PngFileSize = 168434L;
    testS3File = items.stream()
        .filter(item -> item.getName().equals(testS3PngFile))
        .findFirst();
    assertTrue("Expected to find file 'test image.png'", testS3File.isPresent());
    assertEquals("Unexpected test image size", testS3PngFileSize, testS3File.get().getSizeInBytes());
  }
}
