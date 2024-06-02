package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class NfsFileHandlerTest extends SpringTransactionalTest {

  @Autowired private NfsFileHandler nfsFileHandler;

  private NfsClient nfsClient = mock(NfsClient.class);

  private NfsFileDetails testFileDetails;
  private NfsFileDetails testFileDetails2;
  private NfsFolderDetails testFolderDetails;
  private NfsFolderDetails testSubFolderDetails;

  private NfsTarget testTarget1;
  private NfsTarget testTarget2;

  @Before
  public void setUp() throws Exception {

    // let's create nfsFileDetails of a file named 'nfsFile.xlsx' with input stream from
    // 'simpleExcel.xlsx'
    InputStream testResourceIS =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("simpleExcel.xlsx");
    testFileDetails = new NfsFileDetails("simpleExcel.xlsx");
    testFileDetails.setFileSystemFullPath("/test/simpleExcel.xlsx");
    testFileDetails.setRemoteInputStream(testResourceIS);

    // let's create nfsFileDetails of a file named 'Picture1.png' with input stream from
    // 'Picture1.png'
    InputStream testResourceIS2 =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.png");
    testFileDetails2 = new NfsFileDetails("Picture1.png");
    testFileDetails2.setFileSystemFullPath("/test/Picture1.png");
    testFileDetails2.setRemoteInputStream(testResourceIS2);

    testSubFolderDetails = new NfsFolderDetails("test2");
    testSubFolderDetails.setFileSystemFullPath("/test/test2");

    testFolderDetails = new NfsFolderDetails("test");
    testFolderDetails.setFileSystemFullPath("/test");
    testFolderDetails.getContent().add(testFileDetails);
    testFolderDetails.getContent().add(testFileDetails2);
    testFolderDetails.getContent().add(testSubFolderDetails);

    testTarget1 = new NfsTarget("/test/simpleExcel.xlsx", null);
    testTarget2 = new NfsTarget("/test/Picture1.png", null);
    // mock nfs client that'll return our nfsFileDetails
    when(nfsClient.queryNfsFileForDownload(testTarget1)).thenReturn(testFileDetails);
    when(nfsClient.queryNfsFileForDownload(testTarget2)).thenReturn(testFileDetails2);
    when(nfsClient.queryForNfsFolder(new NfsTarget(testFolderDetails.getFileSystemFullPath())))
        .thenReturn(testFolderDetails);
  }

  @Test
  public void testDownloadFileFromFilestore() throws IOException {
    // try file download, it should copy from provided input stream into temp folder
    NfsFileDetails downloadedFileDetails =
        nfsFileHandler.downloadNfsFileToRSpace(testTarget1, nfsClient);
    File downloadedFile = downloadedFileDetails.getLocalFile();
    assertNotNull(downloadedFile);
    assertTrue(downloadedFile.exists());
    assertEquals(testFileDetails.getName(), downloadedFile.getName()); // name from nfsFileDetails
    assertEquals(13248L, downloadedFile.length()); // length of simpleExcel.xlsx file
  }
}
