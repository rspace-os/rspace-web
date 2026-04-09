package com.researchspace.service.aws;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.service.aws.impl.S3UtilitiesFactory;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

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

  @Autowired private S3UtilitiesFactory s3UtilitiesFactory;

  private static final int TOP_LEVEL_FOLDER_CONTENT_COUNT = 2;
  private static final String UNIT_TESTS_FOLDER_NAME = "unitTests";
  private static final int UNIT_TESTS_FOLDER_CONTENT_COUNT = 3;
  private static final String UNIT_TESTS_SUBFOLDER_NAME = "unitTests subfolder";
  private static final int UNIT_TESTS_SUBFOLDER_CONTENT_COUNT = 1;

  private S3Utilities s3Utilities;

  @Before
  public void setup() {
    s3Utilities = s3UtilitiesFactory.createS3Utilities(s3region, s3bucketName);
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testDownloadFile() throws IOException {
    File tmpFile = File.createTempFile("downloaded", ".tmp");

    // download txt file from the top folder
    s3Utilities.downloadFromS3(UNIT_TESTS_FOLDER_NAME + "/" + "testS3File.txt", tmpFile);
    String downloadedContent = Files.readString(tmpFile.toPath());
    assertEquals("testS3Content", downloadedContent);

    // download img file from subfolder
    s3Utilities.downloadFromS3(
        UNIT_TESTS_FOLDER_NAME + "/" + UNIT_TESTS_SUBFOLDER_NAME + "/" + "test image.png", tmpFile);
    assertEquals(168434L, tmpFile.length());

    // cleanup
    tmpFile.delete();
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testListFolderContents() {

    // check the root folder
    List<S3FolderContentItem> items = s3Utilities.listFolderContents("");
    assertEquals(TOP_LEVEL_FOLDER_CONTENT_COUNT, items.size());

    // check the top-level unit tests folder
    items = s3Utilities.listFolderContents(UNIT_TESTS_FOLDER_NAME);
    assertEquals(UNIT_TESTS_FOLDER_CONTENT_COUNT, items.size());

    // find the expected test file and verify its size
    String testS3TxtFile = "testS3File.txt";
    Long testS3TxtFileSize = 13L;
    Optional<S3FolderContentItem> testS3File =
        items.stream()
            .filter(item -> item.getName().equals(testS3TxtFile) && !item.isFolder())
            .findFirst();
    assertTrue("Expected to find file 'testS3File.txt'", testS3File.isPresent());
    assertEquals(
        "Unexpected testS3File.txt size", testS3TxtFileSize, testS3File.get().getSizeInBytes());
    assertEquals(
        LocalDate.of(2026, 3, 18),
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
