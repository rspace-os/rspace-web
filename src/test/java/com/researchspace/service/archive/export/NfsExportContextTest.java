package com.researchspace.service.archive.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsException;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsResourceDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.service.DiskSpaceChecker;
import com.researchspace.service.DiskSpaceLimitException;
import com.researchspace.service.NfsFileHandler;
import com.researchspace.service.NfsManager;
import com.researchspace.service.impl.DiskSpaceCheckerImpl;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class NfsExportContextTest {

  private NfsExportContext nfsContext;
  private ArchiveExportConfig exportConfig;

  private NfsClient nfsClient = mock(NfsClient.class);
  private NfsManager mockNfsManager = mock(NfsManager.class);
  private NfsFileHandler mockNfsFileHandler = mock(NfsFileHandler.class);
  private FieldExporterSupport support = mock(FieldExporterSupport.class);
  private DiskSpaceChecker diskSpaceChecker;

  private NfsFileSystem testFileSystem;
  private NfsFileStore testFileStore;
  private NfsFileStore testFileStore2;

  private NfsElement testNfsFileElem;
  private NfsElement testNfsFileElem2;
  private NfsFileDetails testNfsFileDetails;

  private NfsTarget validNfsTarget;

  private NfsTarget validNfsTargetFolder;

  private NfsFileDetails brokenFileDetails;

  private NfsTarget brokenNfsTarget;
  private NfsElement testNfsFolderElem;
  private NfsFolderDetails testFolderDetails;
  private NfsFolderDetails testSubfolderDetails;

  @Before
  public void setUp() throws Exception {

    // mock nfs manager returning test filestore/filesystem
    testFileSystem = new NfsFileSystem();
    testFileSystem.setId(11L);
    testFileSystem.setName("Test FS");

    // two filestores pointing to the same filesystem
    testFileStore = new NfsFileStore();
    testFileStore.setId(21L);
    testFileStore.setPath("");
    testFileStore.setFileSystem(testFileSystem);

    testFileStore2 = new NfsFileStore();
    testFileStore2.setId(22L);
    testFileStore2.setPath("/test");
    testFileStore2.setFileSystem(testFileSystem);

    // two nfs file links pointing to the same file, through different filestores
    testNfsFileElem = new NfsElement(testFileStore.getId(), "/test/test.txt");
    testNfsFileElem2 = new NfsElement(testFileStore2.getId(), "/test.txt");

    testNfsFileDetails = new NfsFileDetails("test.txt");
    testNfsFileDetails.setFileSystemId(testFileSystem.getId());
    testNfsFileDetails.setFileSystemFullPath("/test/test.txt");

    validNfsTarget = new NfsTarget(testNfsFileDetails.getFileSystemFullPath(), null);

    // a file that doesn't download, will be part of test folder,
    brokenFileDetails = new NfsFileDetails("not_downloadable.txt");
    brokenFileDetails.setFileSystemId(testFileSystem.getId());
    brokenFileDetails.setFileSystemFullPath("/test/not_downloadable.txt");
    brokenNfsTarget = new NfsTarget(brokenFileDetails.getFileSystemFullPath(), null);
    // a subfolder inside test folder
    testSubfolderDetails = new NfsFolderDetails("otherFolder");
    testSubfolderDetails.setFileSystemId(testFileSystem.getId());
    testSubfolderDetails.setFileSystemFullPath("/test/otherFolder");

    // test nfs folder link, pointing to the folder with good file, broken file, and subfolder
    testNfsFolderElem = new NfsElement(testFileStore.getId(), "/test");
    testNfsFolderElem.setLinkType(NfsElement.LINKTYPE_DIR);
    testFolderDetails = new NfsFolderDetails("test");
    testFolderDetails.setFileSystemFullPath("/test");
    testFolderDetails.getContent().add(brokenFileDetails);
    testFolderDetails.getContent().add(testNfsFileDetails);
    testFolderDetails.getContent().add(testSubfolderDetails);
    validNfsTargetFolder = new NfsTarget(testNfsFolderElem.getPath(), null);

    when(mockNfsFileHandler.downloadNfsFileToRSpace(validNfsTarget, nfsClient))
        .thenReturn(testNfsFileDetails);
    when(mockNfsFileHandler.downloadNfsFileToRSpace(eq(validNfsTarget), eq(nfsClient), any()))
        .thenReturn(testNfsFileDetails);
    when(mockNfsFileHandler.downloadNfsFileToRSpace(eq(brokenNfsTarget), eq(nfsClient), any()))
        .thenThrow(new NfsException("test error", null));
    when(mockNfsFileHandler.retireveNfsFolderDetails(validNfsTargetFolder, nfsClient))
        .thenReturn(testFolderDetails);

    when(mockNfsManager.getNfsFileStore(testFileStore.getId())).thenReturn(testFileStore);
    when(mockNfsManager.getNfsFileStore(testFileStore2.getId())).thenReturn(testFileStore2);

    // export/nfs configs pointing to mock nfsClient
    Map<Long, NfsClient> nfsClientMap = Collections.singletonMap(testFileSystem.getId(), nfsClient);
    exportConfig = new ArchiveExportConfig();
    exportConfig.setAvailableNfsClients(nfsClientMap);
    nfsContext = new NfsExportContext(exportConfig);

    // support object returning mock managers
    when(support.getNfsManager()).thenReturn(mockNfsManager);
    when(support.getNfsFileHandler()).thenReturn(mockNfsFileHandler);

    diskSpaceChecker = new DiskSpaceCheckerImpl();
    when(support.getDiskSpaceChecker()).thenReturn(diskSpaceChecker);
  }

  @Test
  public void testDownloadMultipleNfsElementsPointingToSameFile() throws IOException {

    // mock nfs client being logged in
    when(nfsClient.isUserLoggedIn()).thenReturn(true);

    NfsResourceDetails foundDetails =
        nfsContext.getDownloadedNfsResourceDetails(testNfsFileElem, support);
    verify(mockNfsFileHandler).downloadNfsFileToRSpace(validNfsTarget, nfsClient, null);
    assertEquals(testNfsFileDetails, foundDetails);

    // calling second time for the same element, shouldn't call nfsClient
    NfsResourceDetails foundDetailsRepeatCall =
        nfsContext.getDownloadedNfsResourceDetails(testNfsFileElem, support);
    verify(mockNfsFileHandler).downloadNfsFileToRSpace(validNfsTarget, nfsClient, null);
    assertEquals(testNfsFileDetails, foundDetailsRepeatCall);

    // calling third time for the different element, but with the same full path, so shouldn't call
    // nfsClient
    NfsResourceDetails foundDetailsSamePathCall =
        nfsContext.getDownloadedNfsResourceDetails(testNfsFileElem2, support);
    verify(mockNfsFileHandler).downloadNfsFileToRSpace(validNfsTarget, nfsClient, null);
    assertEquals(testNfsFileDetails, foundDetailsSamePathCall);
  }

  @Test
  public void testDownloadNfsFolder() throws IOException {
    // mock nfs client being logged in
    when(nfsClient.isUserLoggedIn()).thenReturn(true);

    // retrieves folder and file inside
    NfsResourceDetails downloadedFolderDetails =
        nfsContext.getDownloadedNfsResourceDetails(testNfsFolderElem, support);
    verify(mockNfsFileHandler, times(1))
        .retireveNfsFolderDetails(eq(validNfsTargetFolder), eq(nfsClient));
    verify(mockNfsFileHandler, times(1))
        .downloadNfsFileToRSpace(
            eq(brokenNfsTarget), eq(nfsClient), eq(downloadedFolderDetails.getLocalFile()));
    verify(mockNfsFileHandler, times(1))
        .downloadNfsFileToRSpace(
            eq(brokenNfsTarget), eq(nfsClient), eq(downloadedFolderDetails.getLocalFile()));
    assertEquals(testFolderDetails, downloadedFolderDetails);

    // reports an error about broken file
    assertEquals(1, nfsContext.getErrors().size());
    Entry<String, String> brokenFileErrorDetails =
        nfsContext.getErrors().entrySet().iterator().next();
    assertEquals("11_/test/not_downloadable.txt", brokenFileErrorDetails.getKey());
    assertEquals("download error: test error", brokenFileErrorDetails.getValue());

    // folder download messages
    assertEquals(1, nfsContext.getFolderSummaryMsgs().size());
    Entry<String, String> folderSummaryMsg =
        nfsContext.getFolderSummaryMsgs().entrySet().iterator().next();
    assertEquals("11_/test", folderSummaryMsg.getKey());
    // internal message
    assertEquals(
        "not_downloadable.txt::download error: test error;;"
            + "test.txt::included;;otherFolder::skipped as subfolder;;",
        folderSummaryMsg.getValue());

    ArchivalNfsFile archivalNfsFolder = new ArchivalNfsFile();
    archivalNfsFolder.setFileStoreId(testFileStore.getId());
    archivalNfsFolder.setFileSystemId(testFileSystem.getId());
    archivalNfsFolder.setRelativePath(testNfsFolderElem.getPath());
    // printout message
    String downloadSummaryMessage = nfsContext.getDownloadSummaryMsgForNfsFolder(archivalNfsFolder);
    assertEquals(
        "Included: test.txt; Skipped: not_downloadable.txt (download error: test error), "
            + "otherFolder (skipped as subfolder);",
        downloadSummaryMessage);
  }

  @Test
  public void testNotLoggedIntoFileSystemError() throws IOException {
    assertEquals(0, nfsContext.getErrors().size());

    // user not logged in to file system
    NfsResourceDetails notLogged =
        nfsContext.getDownloadedNfsResourceDetails(testNfsFileElem, support);
    assertNull(notLogged);
    assertEquals(1, nfsContext.getErrors().size());
    assertEquals(
        "user not logged into 'Test FS' File System",
        nfsContext.getErrors().values().iterator().next());

    // no nfs client for file system pointed by testNfsElem2
    testFileSystem.setId(12L);
    testFileStore2.setPath("/notTest");
    NfsResourceDetails noNfsClient =
        nfsContext.getDownloadedNfsResourceDetails(testNfsFileElem2, support);
    assertNull(noNfsClient);
    assertEquals(2, nfsContext.getErrors().size());
  }

  @Test
  public void testExtensionAndFileSizeFilter() throws URISyntaxException, IOException {
    // mock nfs client being logged in
    when(nfsClient.isUserLoggedIn()).thenReturn(true);

    // files larger than configured size, or with excluded extension, shouldn't be downloaded
    exportConfig.setIncludeNfsLinks(true);
    exportConfig.setMaxNfsFileSize(1000);
    exportConfig.setExcludedNfsFileExtensions(new HashSet<>(Arrays.asList("tiff", "tif")));

    // large file skipped
    NfsElement largeNfsElem = new NfsElement(testFileStore.getId(), "/largeFile.zip");
    NfsFileDetails largeFileDetails = new NfsFileDetails("/largeFile.zip");
    largeFileDetails.setSize(1001L);
    when(nfsClient.queryForNfsFile(new NfsTarget("/largeFile.zip"))).thenReturn(largeFileDetails);

    NfsResourceDetails tooLargeFile =
        nfsContext.getDownloadedNfsResourceDetails(largeNfsElem, support);
    assertNull(tooLargeFile);
    assertEquals(1, nfsContext.getErrors().size(), "file not filtered by size");
    assertEquals(
        "file skipped (file larger than provided size limit)",
        nfsContext.getErrors().values().iterator().next());

    // excluded extension file skipped
    NfsElement excludedExtensionElem = new NfsElement(testFileStore.getId(), "/picture.tif");
    NfsFileDetails excludedExtFileDetails = new NfsFileDetails("picture.TIF");
    excludedExtFileDetails.setSize(999L);
    when(nfsClient.queryForNfsFile(new NfsTarget("/picture.tif")))
        .thenReturn(excludedExtFileDetails);

    nfsContext.getErrors().clear();
    NfsResourceDetails excludedExtensionFile =
        nfsContext.getDownloadedNfsResourceDetails(excludedExtensionElem, support);
    assertNull(excludedExtensionFile);
    assertEquals(1, nfsContext.getErrors().size(), "file not filtered by extension");
    assertEquals(
        "file skipped (file extension 'tif' excluded)",
        nfsContext.getErrors().values().iterator().next());
  }

  @Test
  public void testFailedNfsFileDownload() throws IOException {
    assertEquals(0, nfsContext.getErrors().size());

    // login user, set up nfsFileHandler throwing IOException
    when(nfsClient.isUserLoggedIn()).thenReturn(true);
    when(mockNfsFileHandler.downloadNfsFileToRSpace(validNfsTarget, nfsClient, null))
        .thenThrow(new IOException("test exception"));

    // logged in and correct path - should encounter exception on download
    NfsResourceDetails downloadException =
        nfsContext.getDownloadedNfsResourceDetails(testNfsFileElem, support);
    assertNull(downloadException);
    assertEquals(1, nfsContext.getErrors().size());
  }

  @Test
  public void checkNfsDownloadNotStartedIfNotEnoughDiskSpace() throws IOException {

    // mock nfs client being logged in
    when(nfsClient.isUserLoggedIn()).thenReturn(true);
    exportConfig.setIncludeNfsLinks(true);

    // large file
    NfsElement largeNfsElem = new NfsElement(testFileStore.getId(), "/largeFile.zip");
    NfsFileDetails largeFileDetails = new NfsFileDetails("/largeFile.zip");
    largeFileDetails.setSize(21 * FileUtils.ONE_MB); // 21 MB
    when(nfsClient.queryForNfsFile(new NfsTarget("/largeFile.zip"))).thenReturn(largeFileDetails);

    // let's set archive disk space limit to 20MB
    diskSpaceChecker.setMaxArchiveSizeMB("20");

    // should throw exception without calling download method on nfs file handler
    try {
      nfsContext.getDownloadedNfsResourceDetails(largeNfsElem, support);
      fail("disk space exception expected at this point");
    } catch (DiskSpaceLimitException e) {
      assertEquals("Constructed archive is larger than 20 MB limit", e.getMessage());
    }
    verify(mockNfsFileHandler, never()).downloadNfsFileToRSpace(any(), any(), any());
  }
}
