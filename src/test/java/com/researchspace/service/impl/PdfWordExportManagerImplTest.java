package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.archive.ExportScope;
import com.researchspace.export.pdf.ExportFormat;
import com.researchspace.export.pdf.ExportOperationDetails;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PdfWordExportManagerImplTest {

  User anyUser, otherUser;
  RecordFactory recordFactory;
  ExportToFileConfig config;

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock IPermissionUtils permissions;
  @InjectMocks PdfWordExportManagerImpl pdfExporter;

  @Before
  public void setUp() throws Exception {

    anyUser = TestFactory.createAnyUser("any");
    otherUser = TestFactory.createAnyUser("any");
    recordFactory = new RecordFactory();
    config = new ExportToFileConfig();

    pdfExporter.setExportUtils(new ExportUtils());
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void GetIdExtractorForFolderIgnoresExamples_RSPAC_1411() {
    Folder examples = recordFactory.createSystemCreatedFolder(Folder.EXPORTS_FOLDER_NAME, anyUser);
    config.setExportScope(ExportScope.USER);
    assertIdExtractorNotReturned(examples);
    config.setExportScope(ExportScope.GROUP);
    assertIdExtractorNotReturned(examples);
    config.setExportScope(ExportScope.SELECTION);
    // when(permissions.isPermitted(examples, PermissionType.READ, anyUser)).thenReturn(true);
    assertIdExtractorReturned(examples);
  }

  @Test
  public void GetIdExtractorForFolderIncludesOtherSystemFolders_RSPAC_1411() {
    Folder examples = recordFactory.createSystemCreatedFolder(Folder.SHARED_FOLDER_NAME, anyUser);
    for (ExportScope scope : ExportScope.values()) {
      config.setExportScope(scope);
      assertIdExtractorReturned(examples);
    }
  }

  @Test
  public void ownerOnlyConfigurationForNotebook() {
    Notebook nb = TestFactory.createANotebook("any", anyUser);
    StructuredDocument doc = TestFactory.createAnySD();
    doc.setOwner(otherUser);
    doc.setId(2L);
    nb.addChild(doc, anyUser);
    PdfExportTreeTraversor idEXtractor =
        pdfExporter.getIdExtractorForFolder(config, anyUser, nb, false).get();
    assertTrue(idEXtractor.process(nb));
    assertEquals(0, idEXtractor.getExportRecordList().getRecordsToExportSize());
    // include ids from notebooks with entries from other users
    assertTrue(idEXtractor.process(doc));
    assertEquals(1, idEXtractor.getExportRecordList().getRecordsToExportSize());
  }

  @Test
  public void ownerOnlyConfigurationForFolder() {
    Folder folder = TestFactory.createAFolder("any", otherUser);
    StructuredDocument doc = TestFactory.createAnySD();
    doc.setOwner(otherUser);
    doc.setId(2L);
    folder.addChild(doc, otherUser);
    Mockito.when(permissions.isPermitted(folder, PermissionType.READ, anyUser)).thenReturn(false);
    PdfExportTreeTraversor idEXtractor =
        pdfExporter.getIdExtractorForFolder(config, anyUser, folder, false).get();
    // assertTrue(idEXtractor.process(nb));
    assertTrue(idEXtractor.process(doc));
  }

  @Test
  public void createExportOperationDetailsSanitisesExportNameForFileName() throws IOException {
    config.setExportName("okname");
    config.setExportFormat(ExportFormat.PDF.name());
    assertExportName("okname");

    config.setExportName("name with spaces");
    assertExportName("name_with_spaces");

    config.setExportName("name with ?\"<> chars");
    assertExportName("name_with______chars");
  }

  private void assertExportName(String expected) throws IOException {
    ExportOperationDetails details = pdfExporter.createExportOperationDetails(config);
    assertTrue(
        "name is " + details.getConcatenatedExportFile().getName(),
        details.getConcatenatedExportFile().getName().matches(expected + "[\\w-]{8,12}\\.pdf"));
  }

  private void assertIdExtractorReturned(Folder folder) {
    assertTrue(pdfExporter.getIdExtractorForFolder(config, anyUser, folder, false).isPresent());
  }

  private void assertIdExtractorNotReturned(Folder folder) {
    assertFalse(pdfExporter.getIdExtractorForFolder(config, anyUser, folder, false).isPresent());
  }
}
