package com.researchspace.service.aws.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The tests running a real connection to AWS S3 bucket.
 *
 * <p>The test bucket to use is configured through deployment properties, and must be accessible by
 * user who runs the test (e.g. through access key, and secret, added to '~/.aws/config' file).
 */
@Slf4j
@EnabledIfSystemProperty(named = "nightly", matches = "(|true)")
public class S3ExportUtilitiesRealConnectionTest extends SpringTransactionalTest {

  @Value("${s3.export.realConnectionTest.region}")
  private String s3region;

  @Value("${s3.export.realConnectionTest.bucketName}")
  private String s3bucketName;

  private static final String TEST_ARCHIVE_PATH = "aws-export-test";
  private static final String TEST_FILE_NAME = "demo-export2.zip";

  private S3ExportUtilitiesImpl s3ExportUtilities;

  @Autowired private S3UtilitiesFactory s3UtilitiesFactory;

  private File archiveToExport;

  @BeforeEach
  public void setup() {
    s3ExportUtilities = new S3ExportUtilitiesImpl();
    ReflectionTestUtils.setField(s3ExportUtilities, "s3ExportRegion", s3region);
    ReflectionTestUtils.setField(s3ExportUtilities, "s3ExportBucketName", s3bucketName);
    ReflectionTestUtils.setField(s3ExportUtilities, "archiveFolderStorageTime", 1);
    ReflectionTestUtils.setField(s3ExportUtilities, "s3ArchivePath", TEST_ARCHIVE_PATH);
    ReflectionTestUtils.setField(s3ExportUtilities, "s3UtilitiesFactory", s3UtilitiesFactory);
    s3ExportUtilities.init();

    archiveToExport = RSpaceTestUtils.getResource("archives/demo-export2.zip");
  }

  @Test
  public void testFileNotInS3() {
    assertFalse(s3ExportUtilities.isArchiveInS3("fileNotInS3.zip"));
  }

  @Test
  public void testIsArchiveInS3BucketNameNull() {
    // clear & reinitialize s3Utilities
    ReflectionTestUtils.setField(s3ExportUtilities, "s3Utilities", null);
    s3ExportUtilities.init();
    s3ExportUtilities.isArchiveInS3(TEST_FILE_NAME);

    // clear & reinitialize s3Utilities, but with null bucket name
    ReflectionTestUtils.setField(s3ExportUtilities, "s3Utilities", null);
    ReflectionTestUtils.setField(s3ExportUtilities, "s3ExportBucketName", "");
    s3ExportUtilities.init();
    assertThrows(
        IllegalStateException.class, () -> s3ExportUtilities.isArchiveInS3(TEST_FILE_NAME));
  }

  @Test
  public void testUpload() {
    s3ExportUtilities.uploadArchiveToS3(archiveToExport);
    assertTrue(s3ExportUtilities.isArchiveInS3(TEST_FILE_NAME));
  }

  @Test
  public void testPresignedURL() {
    s3ExportUtilities.uploadArchiveToS3(archiveToExport);
    URL url = s3ExportUtilities.getPresignedUrlForArchiveDownload(TEST_FILE_NAME);
    assertNotNull(url);
  }

  @Test
  public void testPresignedURLFileDoesNotExist() {
    assertNull(s3ExportUtilities.getPresignedUrlForArchiveDownload("doesNotExist.zip"));
  }
}
