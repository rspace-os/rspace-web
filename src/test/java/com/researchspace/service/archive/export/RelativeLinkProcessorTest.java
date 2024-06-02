package com.researchspace.service.archive.export;

import static com.researchspace.core.util.MediaUtils.IMAGES_MEDIA_FLDER_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.DiskSpaceChecker;
import com.researchspace.service.DiskSpaceLimitException;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

public class RelativeLinkProcessorTest extends SpringTransactionalTest {

  RelativeLinkProcessor processor;
  private @Autowired RecordDeletionManager deletionMgr;
  private @Autowired FieldExporterSupport support;

  @Test
  public void testGetLinkReplacementForNonTiffFile()
      throws IOException,
          URISyntaxException,
          AuthorizationException,
          DocumentAlreadyEditedException {
    ArchiveExportConfig cfg = new ArchiveExportConfig();
    cfg.setExportScope(ExportScope.SELECTION);

    User user = createInitAndLoginAnyUser();
    EcatMediaFile media = addImageToGallery(user);
    File ouputFolder = Files.createTempDirectory("nontifftest").toFile();
    ImmutableExportRecordList emptyExportList = new ExportRecordList();

    FieldExportContext context =
        new FieldExportContext(ouputFolder, ouputFolder, cfg, emptyExportList);
    processor = new RelativeLinkProcessor(context, support);
    RelativeLinks links = processor.getLinkReplacement(media);
    assertNull(links.getLinkToOriginalFile());
    assertNotNull(links.getRelativeLinkToReplaceLinkInText());
    // output folder contains 1 file
    assertEquals(1, fileCountInExportFolder(ouputFolder));

    // no select the gallery file; it will not be copied but linked to:
    // RSPAC-1333
    ExportRecordList toExportList2 = new ExportRecordList();
    toExportList2.add(media.getOid());
    File ouputFolder2 = Files.createTempDirectory("selectedMediaNotCopied").toFile();
    context = new FieldExportContext(ouputFolder2, ouputFolder2, cfg, toExportList2);
    processor = new RelativeLinkProcessor(context, support);
    links = processor.getLinkReplacement(media);
    assertEquals(0, fileCountInExportFolder(ouputFolder2));

    // now make link to Gallery, media file will not be copied
    cfg.setExportScope(ExportScope.USER);
    ouputFolder = Files.createTempDirectory("nontifftest2").toFile();
    context = new FieldExportContext(ouputFolder, ouputFolder, cfg, emptyExportList);
    processor = new RelativeLinkProcessor(context, support);
    links = processor.getLinkReplacement(media);
    assertNull(links.getLinkToOriginalFile());
    assertNotNull(links.getRelativeLinkToReplaceLinkInText());
    assertEquals(0, fileCountInExportFolder(ouputFolder));

    // now delete the gallery item folder. it will be copied into archive folder
    deletionMgr.deleteRecord(
        recordMgr.getGallerySubFolderForUser(IMAGES_MEDIA_FLDER_NAME, user).getId(),
        media.getId(),
        user);
    // refresh object following deletion to pick up deleted state.
    media = (EcatMediaFile) recordDao.get(media.getId());
    ouputFolder = Files.createTempDirectory("deletedGallery").toFile();
    context = new FieldExportContext(ouputFolder, ouputFolder, cfg, emptyExportList);
    processor = new RelativeLinkProcessor(context, support);
    links = processor.getLinkReplacement(media);

    assertEquals(1, fileCountInExportFolder(ouputFolder));
  }

  private int fileCountInExportFolder(File ouputFolder) {
    return FileUtils.listFiles(ouputFolder, FileFilterUtils.trueFileFilter(), null).size();
  }

  @Test
  public void getOriginalFile() throws IOException {
    ArchiveExportConfig cfg = new ArchiveExportConfig();
    cfg.setExportScope(ExportScope.SELECTION);

    User user = createInitAndLoginAnyUser();
    EcatMediaFile media = TestFactory.createEcatImage(25L);
    media.setFileName("xxx.tiff");
    media.setExtension("tiff");
    // export selection from Gallery
    File ouputFolder = Files.createTempDirectory("tifftest").toFile();
    ExportRecordList exportList = new ExportRecordList();
    exportList.add(media.getOid());
    FieldExportContext context = new FieldExportContext(ouputFolder, ouputFolder, cfg, exportList);
    processor = new RelativeLinkProcessor(context, support);
    assertFalse(StringUtils.isEmpty(processor.getOriginalFile(media)));
    assertTrue(processor.getOriginalFile(media).contains("../"));
  }

  @Test
  public void availableDiskSpaceCheckedDuringCopyToArchiveFolder() throws IOException {

    File ouputFolder = Files.createTempDirectory("relativeLinkProcessorTest").toFile();
    assertEquals(0, ouputFolder.listFiles().length);

    File testFileSmall = RSpaceTestUtils.getAnyAttachment();
    File testFileTooLarge = RSpaceTestUtils.getAnyPdf();

    // mock space checker that'll throw exception when tooLarge file is checked for available space
    DiskSpaceChecker mockedSpaceChecker = Mockito.mock(DiskSpaceChecker.class);
    doThrow(new DiskSpaceLimitException("not enough disk space"))
        .when(mockedSpaceChecker)
        .assertEnoughDiskSpaceToCopyFileIntoArchiveDir(eq(testFileTooLarge), any());
    FieldExporterSupport mockedSupport = Mockito.mock(FieldExporterSupport.class);
    when(mockedSupport.getDiskSpaceChecker()).thenReturn(mockedSpaceChecker);

    // create processor pointing to mockedSpaceChecker
    FieldExportContext context = new FieldExportContext(null, null, null, null);
    processor = new RelativeLinkProcessor(context, mockedSupport);

    processor.copyResourceToArchiveFolder(testFileSmall, ouputFolder);
    assertEquals(1, ouputFolder.listFiles().length);

    try {
      processor.copyResourceToArchiveFolder(testFileTooLarge, ouputFolder);
      fail();
    } catch (DiskSpaceLimitException ioe) {
      assertEquals("not enough disk space", ioe.getMessage());
    }
  }
}
