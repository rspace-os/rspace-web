package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import com.researchspace.service.impl.DiskSpaceCheckerImpl;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class DiskSpaceCheckerTest {

  private DiskSpaceCheckerImpl diskSpaceChecker;

  @Before
  public void setUp() throws Exception {
    diskSpaceChecker = new DiskSpaceCheckerImpl();
  }

  @Test
  public void testPropertiesParsing() {
    // unset values
    diskSpaceChecker.setMaxArchiveSizeMB(null);
    diskSpaceChecker.setSpaceRequiredToStartArchiveExportMB("");

    assertEquals(0, diskSpaceChecker.getMaxArchiveSizeMB());
    assertEquals(0, diskSpaceChecker.getMinSpaceRequiredToStartArchiveExportMB());

    // unparsable values
    diskSpaceChecker.setMaxArchiveSizeMB("1,500");
    diskSpaceChecker.setSpaceRequiredToStartArchiveExportMB("5GB");

    assertEquals(0, diskSpaceChecker.getMaxArchiveSizeMB());
    assertEquals(0, diskSpaceChecker.getMinSpaceRequiredToStartArchiveExportMB());

    // atypical values
    diskSpaceChecker.setMaxArchiveSizeMB("3.14");
    diskSpaceChecker.setSpaceRequiredToStartArchiveExportMB("-500");

    assertEquals(3, diskSpaceChecker.getMaxArchiveSizeMB());
    assertEquals(0, diskSpaceChecker.getMinSpaceRequiredToStartArchiveExportMB());
  }

  @Test
  public void testMaxArchiveSizeCheck() throws Exception {

    diskSpaceChecker.setMaxArchiveSizeMB("10");
    File ouputFolder = Files.createTempDirectory("diskSpaceCheckerTest").toFile();

    File smallFile = RSpaceTestUtils.getAnyAttachment();
    File largeFile14MB = RSpaceTestUtils.getResource("MS20-09HCD3x400s3.tif");

    // checking small file pass
    diskSpaceChecker.assertEnoughDiskSpaceToCopyFileIntoArchiveDir(smallFile, ouputFolder);

    // checking large file should fail
    try {
      diskSpaceChecker.assertEnoughDiskSpaceToCopyFileIntoArchiveDir(largeFile14MB, ouputFolder);
      fail("expected exception on too large file");
    } catch (DiskSpaceLimitException ioe) {
      assertEquals("Constructed archive is larger than 10 MB limit", ioe.getMessage());
    }
  }

  @Test
  public void testCurrentlyAllowedArchiveSizeMB() throws Exception {

    // with default (unset) archive limit property the allowed space should be quite large
    assertTrue(diskSpaceChecker.getCurrentlyAllowedArchiveSizeMB() > 0);
    assertTrue(diskSpaceChecker.canStartArchiveProcess());

    // let's set global archive limit to 10MB
    diskSpaceChecker.setMaxArchiveSizeMB("10");

    // in normal condition server have 10MB of disk space available, so max archive size should be
    // returned
    assertEquals(10, diskSpaceChecker.getCurrentlyAllowedArchiveSizeMB());
    assertEquals(true, diskSpaceChecker.canStartArchiveProcess());

    // let's mock disk checker's tempFile handler, so it returns 5MB of available space
    File mockFile = Mockito.mock(File.class);
    when(mockFile.getUsableSpace()).thenReturn(5_500_000L);
    diskSpaceChecker.setTempFile(mockFile);

    // now max allowed size should be limited to 5MB
    assertEquals(5, diskSpaceChecker.getCurrentlyAllowedArchiveSizeMB());

    // now let's set minimal remaining space requirement to 2MB
    diskSpaceChecker.setSpaceRequiredToStartArchiveExportMB("2");
    // now max allowed size should be limited to 3MB
    assertEquals(3, diskSpaceChecker.getCurrentlyAllowedArchiveSizeMB());
    assertTrue(diskSpaceChecker.canStartArchiveProcess());

    // now let's set minimal remaining space requirement to 10MB
    diskSpaceChecker.setSpaceRequiredToStartArchiveExportMB("10");
    // export is no longer allowed (usable space over required space limit)
    assertEquals(-5, diskSpaceChecker.getCurrentlyAllowedArchiveSizeMB());
    assertFalse(diskSpaceChecker.canStartArchiveProcess());
  }

  @Test
  public void testFullDiskArchiveSizeCheck() throws Exception {

    File ouputFolder = Files.createTempDirectory("diskSpaceCheckerTest").toFile();
    File largeFile14MB = RSpaceTestUtils.getResource("MS20-09HCD3x400s3.tif");

    // large file should pass in normal condition
    diskSpaceChecker.assertEnoughDiskSpaceToCopyFileIntoArchiveDir(largeFile14MB, ouputFolder);

    // let's mock disk checker's tempFile handler, so it returns 5MB of available space
    File mockFile = Mockito.mock(File.class);
    when(mockFile.getUsableSpace()).thenReturn(5_500_000L);
    diskSpaceChecker.setTempFile(mockFile);

    // large file shouldn't pass now
    try {
      diskSpaceChecker.assertEnoughDiskSpaceToCopyFileIntoArchiveDir(largeFile14MB, ouputFolder);
      fail("expected exception as no available disk space");
    } catch (DiskSpaceLimitException ioe) {
      assertEquals(
          "Constructed archive is getting larger (14 MB) than required available disk space",
          ioe.getMessage());
    }

    // let's mock disk checker's tempFile handler again, so it returns ~20 MB of available space
    File mockFileTwo = Mockito.mock(File.class);
    when(mockFileTwo.getUsableSpace()).thenReturn(22_000_000L);
    diskSpaceChecker.setTempFile(mockFileTwo);

    // large file should fit (remaining space requirement is 0)
    diskSpaceChecker.assertEnoughDiskSpaceToCopyFileIntoArchiveDir(largeFile14MB, ouputFolder);

    // now let's update minimal remaining space requirement to 10MB
    diskSpaceChecker.setSpaceRequiredToStartArchiveExportMB("10");
    try {
      diskSpaceChecker.assertEnoughDiskSpaceToCopyFileIntoArchiveDir(largeFile14MB, ouputFolder);
      fail(
          "expected exception as not enough disk space when minimal space requirement is included");
    } catch (DiskSpaceLimitException ioe) {
      assertEquals(
          "Constructed archive is getting larger (14 MB) than required available disk space",
          ioe.getMessage());
    }

    // let's mock disk checker's tempFile handler again, so it returns ~30 MB of available space
    File mockFileThree = Mockito.mock(File.class);
    when(mockFileThree.getUsableSpace()).thenReturn(33_000_000L);
    diskSpaceChecker.setTempFile(mockFileThree);

    // large file should fit again (file is 14 MB, remaining space requirement is 10 MB)
    diskSpaceChecker.assertEnoughDiskSpaceToCopyFileIntoArchiveDir(largeFile14MB, ouputFolder);
  }
}
