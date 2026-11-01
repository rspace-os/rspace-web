package com.researchspace.service.archive.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.model.User;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.data.ArchiveExportNotificationData;
import com.researchspace.model.comms.data.ArchiveExportNotificationData.ExportedNfsLinkData;
import com.researchspace.model.comms.data.ArchiveExportNotificationData.ExportedRecordData;
import com.researchspace.model.record.Record;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.aws.S3ExportUtilities;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.http.SdkHttpResponse;

public class StandardPostExportCompletionTest {

  StandardPostExportCompletionImpl postExport = new StandardPostExportCompletionImpl();

  @BeforeEach
  void setUp() {
    postExport.setMessages(new MessageSourceUtils(new JsonMessageSource()));
  }

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
    assertThat(noResultData.getExportedRecords()).isEmpty();
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
    assertThat(oneResultAndLinkData.getExportedRecords()).hasSize(1);
    assertThat(oneResultAndLinkData.getExportedNfsLinks()).hasSize(2);

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

  @Test
  public void s3ExportNotificationShowsPresignedUrlAsLinkText(@TempDir Path tempDir)
      throws Exception {
    String s3Url = "https://bucket.s3.eu-west-2.amazonaws.com/archives/a.zip?X-Amz-Expires=3600";
    File exportFile = Files.writeString(tempDir.resolve("a.zip"), "zip").toFile();

    CommunicationManager commMgr = mock(CommunicationManager.class);
    Notification notification = new Notification();
    notification.setId(1L);
    when(commMgr.systemNotify(any(), any(), anyString(), anyString(), anyBoolean()))
        .thenReturn(notification);
    S3ExportUtilities s3 = mock(S3ExportUtilities.class);
    when(s3.uploadArchiveToS3(exportFile))
        .thenReturn(SdkHttpResponse.builder().statusCode(200).build());
    when(s3.isArchiveInS3("a.zip")).thenReturn(true);
    when(s3.getPresignedUrlForArchiveDownload("a.zip")).thenReturn(new URL(s3Url));

    ReflectionTestUtils.setField(postExport, "commMgr", commMgr);
    ReflectionTestUtils.setField(postExport, "s3ExportUtilities", s3);
    ReflectionTestUtils.setField(postExport, "publisher", mock(ApplicationEventPublisher.class));
    ReflectionTestUtils.setField(postExport, "removalPolicy", ExportRemovalPolicy.FALSE);
    ReflectionTestUtils.setField(
        postExport,
        "velocity",
        RSpaceTestUtils.setupVelocity(
            "src/main/resources/velocityTemplates/messageAndNotificationEmails"));
    ReflectionTestUtils.setField(postExport, "hasS3Access", true);
    ReflectionTestUtils.setField(postExport, "serverURLPrefix", "http://localhost:8080");

    ArchiveResult result = new ArchiveResult();
    result.setExportFile(exportFile);
    postExport.postArchiveCompletionOperations(
        new ArchiveExportConfig(), TestFactory.createAnyUser("any"), result);

    ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
    verify(commMgr).updateNotificationMessage(eq(1L), msg.capture());
    assertThat(msg.getValue())
        .contains("<a href='" + s3Url + "'>" + s3Url + "</a>")
        .doesNotContain("downloadArchive");
  }
}
