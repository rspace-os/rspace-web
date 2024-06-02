package com.researchspace.service.archive.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.service.DiskSpaceChecker;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class NfsElementFieldExporterTest {

  private NfsElementFieldExporter nfsExporter;

  private FieldExportContext context;
  private ArchiveExportConfig exportConfig;
  private NfsExportContext nfsContext;
  private FieldExporterSupport support;

  private Long testFileStoreId = 21L;
  private String testFilePath = "/test.txt";
  private NfsElement testNfsElem = new NfsElement(testFileStoreId, testFilePath);
  private NfsFileDetails testFileDetails;

  private File tmpNfsArchiveFolder;

  @Before
  public void setUp() throws Exception {

    File anyAttachment = RSpaceTestUtils.getAnyAttachment();
    testFileDetails = new NfsFileDetails(anyAttachment.getName());
    testFileDetails.setLocalFile(anyAttachment);
    tmpNfsArchiveFolder = Files.createTempDirectory("testNfsArchive").toFile();

    support = Mockito.mock(FieldExporterSupport.class);
    when(support.getDiskSpaceChecker()).thenReturn(mock(DiskSpaceChecker.class));
    nfsExporter = new NfsElementFieldExporter(support);

    nfsContext = mock(NfsExportContext.class);
    when(nfsContext.getDownloadedNfsResourceDetails(testNfsElem, support))
        .thenReturn(testFileDetails);
    when(nfsContext.getArchiveNfsDir()).thenReturn(tmpNfsArchiveFolder);

    exportConfig = new ArchiveExportConfig();
    context =
        new FieldExportContext(
            exportConfig, null, tmpNfsArchiveFolder, tmpNfsArchiveFolder, null, nfsContext, null);
    when(nfsContext.getExportConfig()).thenReturn(exportConfig);
  }

  @Test
  public void testUrlReplacementForNonNfsExport() throws URISyntaxException, IOException {
    String updatedLink = nfsExporter.getReplacementUrl(context, testNfsElem);
    assertEquals("21:/test.txt", updatedLink);
  }

  @Test
  public void testUrlReplacementWithNfsClient() throws URISyntaxException, IOException {
    exportConfig.setIncludeNfsLinks(true);
    assertEquals(0, tmpNfsArchiveFolder.list().length);

    String updatedLink = nfsExporter.getReplacementUrl(context, testNfsElem);
    String[] nfsFilePathInArchive = updatedLink.split("/");

    assertEquals(1, tmpNfsArchiveFolder.listFiles().length);
    File foundArchiveFolder = tmpNfsArchiveFolder.listFiles()[0];
    assertTrue(foundArchiveFolder.exists());
    assertTrue(foundArchiveFolder.isDirectory());
    assertEquals(nfsFilePathInArchive[0], foundArchiveFolder.getName());

    assertEquals(1, foundArchiveFolder.listFiles().length);
    File foundNfsFile = foundArchiveFolder.listFiles()[0];
    assertTrue(foundNfsFile.exists());
    assertTrue(foundNfsFile.isFile());
    assertEquals(testFileDetails.getName(), foundNfsFile.getName());
  }

  @Test
  public void testUrlReplacementForNfsClientError() throws URISyntaxException, IOException {
    exportConfig.setIncludeNfsLinks(true);
    assertEquals(0, tmpNfsArchiveFolder.list().length);

    when(nfsContext.getDownloadedNfsResourceDetails(testNfsElem, support)).thenReturn(null);

    String updatedLink = nfsExporter.getReplacementUrl(context, testNfsElem);
    assertEquals(0, tmpNfsArchiveFolder.list().length);
    assertEquals("21:/test.txt", updatedLink, "nfs link should not be replaced if download error");
  }
}
