package com.researchspace.service.archive.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.model.User;
import com.researchspace.model.comms.data.ArchiveExportNotificationData;
import com.researchspace.model.comms.data.ArchiveExportNotificationData.ExportedNfsLinkData;
import com.researchspace.model.comms.data.ArchiveExportNotificationData.ExportedRecordData;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.TestFactory;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class StandardPostExportCompletionTest {

  StandardPostExportCompletionImpl postExport = new StandardPostExportCompletionImpl();

  @Test
  public void checkExportedRecordsSummaryTest() throws URISyntaxException, IOException {

    User user = TestFactory.createAnyUser("any");

    // check export with no records
    ArchiveExportConfig expCfg = new ArchiveExportConfig();
    ArchiveResult archiveResult = new ArchiveResult();
    assertEquals(
        "No records were exported.", postExport.getExportedRecordsSummary(expCfg, archiveResult));

    // add one record
    List<Record> testRecords = new ArrayList<>();
    Record record = TestFactory.createAnyRecord(user);
    testRecords.add(record);
    archiveResult.setArchivedRecords(testRecords);
    assertEquals(
        "The archive includes 1 record.",
        postExport.getExportedRecordsSummary(expCfg, archiveResult));

    // check message for export with nfs included flag
    expCfg.setIncludeNfsLinks(true);
    assertEquals(
        "The archive includes 1 record. No filestore links were included.",
        postExport.getExportedRecordsSummary(expCfg, archiveResult));

    // check message for export with one included nfs link
    Set<ArchivalNfsFile> testNfsFiles = new HashSet<>();
    ArchivalNfsFile nfsFile = new ArchivalNfsFile();
    nfsFile.setRelativePath("/test");
    nfsFile.setAddedToArchive(true);
    testNfsFiles.add(nfsFile);
    archiveResult.setArchivedNfsFiles(testNfsFiles);
    assertEquals(
        "The archive includes 1 record and 1 linked filestore item.",
        postExport.getExportedRecordsSummary(expCfg, archiveResult));

    // add another nfs file, skipped on export
    ArchivalNfsFile nfsFile2 = new ArchivalNfsFile();
    nfsFile2.setFileStoreId(1L);
    nfsFile2.setRelativePath("/test2");
    nfsFile2.setAddedToArchive(false);
    nfsFile2.setErrorMsg("Download error");
    testNfsFiles.add(nfsFile2);
    assertEquals(
        "The archive includes 1 record and 1 linked filestore item."
            + "<br/><br/>1 filestore link was not included.",
        postExport.getExportedRecordsSummary(expCfg, archiveResult));
  }

  @Test
  public void checkExportedNotificationData() {

    User user = TestFactory.createAnyUser("any");

    // check export with no records
    ArchiveExportConfig expCfg = new ArchiveExportConfig();
    ArchiveResult archiveResult = new ArchiveResult();

    ArchiveExportNotificationData noResultData =
        postExport.createExportNotificationData("testUrl", expCfg, archiveResult);
    assertNotNull(noResultData);
    assertEquals("testUrl", noResultData.getDownloadLink());
    assertEquals("xml", noResultData.getArchiveType());
    assertNull(noResultData.getExportScope());
    assertNull(noResultData.getExportedUserOrGroupId());
    assertFalse(noResultData.isNfsLinksIncluded());
    assertTrue(noResultData.getExportedRecords().isEmpty());
    assertNull(noResultData.getExportedNfsLinks());

    // add one record with nfs link
    expCfg.setExportScope(ExportScope.SELECTION);
    expCfg.setIncludeNfsLinks(true);
    expCfg.setMaxNfsFileSize(20000);
    expCfg.setExcludedNfsFileExtensions(new HashSet<>(Arrays.asList("tif", "zip")));

    List<Record> exportedRecords = new ArrayList<>();
    Record record = TestFactory.createAnyRecord(user);
    record.setId(1L);
    exportedRecords.add(record);
    archiveResult.setArchivedRecords(exportedRecords);

    ArchivalNfsFile nfsFile = new ArchivalNfsFile();
    nfsFile.setFileSystemId(1L);
    nfsFile.setFileStoreId(2L);
    nfsFile.setRelativePath("/testFil");
    nfsFile.setAddedToArchive(true);
    nfsFile.setErrorMsg("testError");

    ArchivalNfsFile nfsFolder = new ArchivalNfsFile();
    nfsFolder.setFileSystemId(1L);
    nfsFolder.setFileStoreId(2L);
    nfsFolder.setRelativePath("/testFolder");
    nfsFolder.setAddedToArchive(true);
    nfsFolder.setFolderLink(true);
    nfsFolder.setFolderExportSummaryMsg("content,export summary msg;");

    Set<ArchivalNfsFile> exportedLinks = new LinkedHashSet<>();
    exportedLinks.add(nfsFile);
    exportedLinks.add(nfsFolder);
    archiveResult.setArchivedNfsFiles(exportedLinks);

    ArchiveExportNotificationData oneResultAndLinkData =
        postExport.createExportNotificationData("testUrl2", expCfg, archiveResult);
    assertNotNull(oneResultAndLinkData);
    assertEquals("testUrl2", oneResultAndLinkData.getDownloadLink());
    assertEquals("xml", oneResultAndLinkData.getArchiveType());
    assertEquals("SELECTION", oneResultAndLinkData.getExportScope());
    assertNull(oneResultAndLinkData.getExportedUserOrGroupId());
    assertTrue(oneResultAndLinkData.isNfsLinksIncluded());
    assertEquals(
        Long.valueOf(expCfg.getMaxNfsFileSize()), oneResultAndLinkData.getMaxNfsFileSize());
    assertEquals(
        expCfg.getExcludedNfsFileExtensions(), oneResultAndLinkData.getExcludedNfsFileExtensions());
    assertEquals(1, oneResultAndLinkData.getExportedRecords().size());
    assertEquals(2, oneResultAndLinkData.getExportedNfsLinks().size());

    ExportedRecordData exportedRecord = oneResultAndLinkData.getExportedRecords().get(0);
    assertEquals("SD1", exportedRecord.getGlobalId());
    assertEquals(record.getName(), exportedRecord.getName());
    assertNull(exportedRecord.getExportedParentGlobalId());

    ExportedNfsLinkData exportedFileLink = oneResultAndLinkData.getExportedNfsLinks().get(0);
    assertEquals(nfsFile.getFileSystemName(), exportedFileLink.getFileSystemName());
    assertEquals(nfsFile.getFileStorePath(), exportedFileLink.getFileStorePath());
    assertEquals(nfsFile.getRelativePath(), exportedFileLink.getRelativePath());
    assertEquals(nfsFile.isAddedToArchive(), exportedFileLink.isAddedToArchive());
    assertEquals(nfsFile.getErrorMsg(), exportedFileLink.getErrorMsg());
    assertFalse(exportedFileLink.isFolderLink());

    ExportedNfsLinkData exportedFolderLink = oneResultAndLinkData.getExportedNfsLinks().get(1);
    assertTrue(exportedFolderLink.isFolderLink());
    assertEquals(
        nfsFolder.getFolderExportSummaryMsg(), exportedFolderLink.getFolderExportSummaryMsg());
  }
}
