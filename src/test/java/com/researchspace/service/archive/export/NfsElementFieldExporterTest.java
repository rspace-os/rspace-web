package com.researchspace.service.archive.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  @BeforeEach
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
    assertThat(tmpNfsArchiveFolder.list()).isEmpty();

    String updatedLink = nfsExporter.getReplacementUrl(context, testNfsElem);
    String[] nfsFilePathInArchive = updatedLink.split("/");

    assertThat(tmpNfsArchiveFolder.listFiles()).hasSize(1);
    File foundArchiveFolder = tmpNfsArchiveFolder.listFiles()[0];
    assertThat(foundArchiveFolder).exists();
    assertThat(foundArchiveFolder).isDirectory();
    assertThat(foundArchiveFolder).hasName(nfsFilePathInArchive[0]);

    assertThat(foundArchiveFolder.listFiles()).hasSize(1);
    File foundNfsFile = foundArchiveFolder.listFiles()[0];
    assertThat(foundNfsFile).exists();
    assertThat(foundNfsFile).isFile();
    assertThat(foundNfsFile).hasName(testFileDetails.getName());
  }

  @Test
  public void testUrlReplacementForNfsClientError() throws URISyntaxException, IOException {
    exportConfig.setIncludeNfsLinks(true);
    assertThat(tmpNfsArchiveFolder.list()).isEmpty();

    when(nfsContext.getDownloadedNfsResourceDetails(testNfsElem, support)).thenReturn(null);

    String updatedLink = nfsExporter.getReplacementUrl(context, testNfsElem);
    assertThat(tmpNfsArchiveFolder.list()).isEmpty();
    assertEquals("21:/test.txt", updatedLink, "nfs link should not be replaced if download error");
  }
}
