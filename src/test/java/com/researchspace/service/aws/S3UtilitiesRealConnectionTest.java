package com.researchspace.service.aws;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.service.aws.impl.S3UtilitiesImpl;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
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
  private static final String TEST_UNIT_TESTS_PATH = "unitTests";
  private static final String TEST_FILE_NAME = "demo-export2.zip";


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
    // Download the file
    File downloadedFile = File.createTempFile("downloaded", ".txt");
    s3Utilities.downloadFromS3(TEST_UNIT_TESTS_PATH  + "/" + "testS3File.txt", downloadedFile);

    // Verify content
    String downloadedContent = Files.readString(downloadedFile.toPath());
    assertEquals("testS3Content", downloadedContent);

    // cleanup
    downloadedFile.delete();
  }
}
