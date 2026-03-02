package com.researchspace.service.aws;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.service.aws.impl.S3UtilitiesImpl;
import com.researchspace.service.impl.ConditionalTestRunnerNotSpring;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.waiters.S3Waiter;

/** These tests are running with a real connection to aws S3 */
@RunWith(ConditionalTestRunnerNotSpring.class)
@Slf4j
public class S3UtilitiesITTest {

  private static final String TEST_FILE_NAME = "demo-export2.zip";
  private static final String REGION = "eu-west-1";
  private static final String BUCKET_NAME = "test-dhjsakhfuidshfis";
  private static final String S3_OBJECT_KEY = "aws-export-test";

  private S3UtilitiesImpl s3Utilities;

  private File archiveToExport;

  @Before
  public void setup() {
    s3Utilities = new S3UtilitiesImpl();
    ReflectionTestUtils.setField(s3Utilities, "region", REGION);
    ReflectionTestUtils.setField(s3Utilities, "s3BucketName", BUCKET_NAME);
    ReflectionTestUtils.setField(s3Utilities, "s3ArchivePath", S3_OBJECT_KEY);
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
  public void testUpload() throws Exception {
    s3Utilities.getS3Uploader(archiveToExport).apply(archiveToExport);
    assertTrue(s3Utilities.isArchiveInS3(TEST_FILE_NAME));
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testPresignedURL() throws Exception {
    s3Utilities.getS3Uploader(archiveToExport).apply(archiveToExport);
    URL url = s3Utilities.getPresignedUrlForArchiveDownload(TEST_FILE_NAME);
    assertNotNull(url);
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testPresignedURLFileDoesNotExist() {
    assertNull(s3Utilities.getPresignedUrlForArchiveDownload("doesNotExist.zip"));
  }
}
