package com.researchspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.testutils.TestFactory;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class PdfWordExportManagerImplTest {

  User anyUser, otherUser;
  RecordFactory recordFactory;
  ExportToFileConfig config;
  @Mock IPermissionUtils permissions;
  @InjectMocks PdfWordExportManagerImpl pdfExporter;

  @BeforeEach
  public void setUp() throws Exception {
    ReflectionTestUtils.setField(
        pdfExporter, "messageSource", new MessageSourceUtils(new JsonMessageSource()));

    anyUser = TestFactory.createAnyUser("any");
    otherUser = TestFactory.createAnyUser("any");
    recordFactory = new RecordFactory();
    config = new ExportToFileConfig();

    pdfExporter.setExportUtils(new ExportUtils());
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void conversionFailureIsLocalizedBeforeReachingExportNotifications() throws Exception {
    var records = Mockito.mock(com.researchspace.service.RecordManager.class);
    var html = Mockito.mock(com.researchspace.export.pdf.HTMLStringGenerator.class);
    var processor = Mockito.mock(com.researchspace.export.pdf.ExportProcessor.class);
    var locales = Mockito.mock(com.researchspace.service.UserLocaleService.class);
    var messages = Mockito.mock(MessageSourceUtils.class);
    ReflectionTestUtils.setField(pdfExporter, "recordManager", records);
    ReflectionTestUtils.setField(pdfExporter, "htmlStringGenerator", html);
    ReflectionTestUtils.setField(pdfExporter, "exportProcessors", java.util.List.of(processor));
    ReflectionTestUtils.setField(pdfExporter, "userLocaleService", locales);
    ReflectionTestUtils.setField(pdfExporter, "messageSource", messages);
    StructuredDocument doc = TestFactory.createAnySD();
    doc.setId(42L);
    config.setExportFormat("WORD");
    Mockito.when(records.getRecordWithFields(42L, anyUser)).thenReturn(doc);
    Mockito.when(permissions.isPermitted(doc, PermissionType.READ, anyUser)).thenReturn(true);
    Mockito.when(processor.supportsFormat(ExportFormat.WORD)).thenReturn(true);
    Mockito.when(locales.getLocaleFor(anyUser)).thenReturn(java.util.Locale.FRENCH);
    Mockito.when(
            messages.getMessageForLocale(
                "errors.documentConversion.timeout", java.util.Locale.FRENCH))
        .thenReturn("Conversion trop longue");
    Mockito.doThrow(
            new com.researchspace.documentconversion.ext.DocumentConversionException(
                com.researchspace.documentconversion.ext.DocumentConversionError.TIMEOUT))
        .when(processor)
        .makeExport(Mockito.any(), Mockito.any(), Mockito.eq(doc), Mockito.eq(config));
    assertEquals(
        "Conversion trop longue",
        org.junit.jupiter.api.Assertions.assertThrows(
                com.researchspace.service.archive.export.ExportFailureException.class,
                () ->
                    pdfExporter.doExportForSigning(
                        anyUser,
                        new Long[] {42L},
                        new String[] {doc.getName()},
                        new String[] {"NORMAL"},
                        config,
                        anyUser))
            .getMessage());
  }

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
    assertThat(details.getConcatenatedExportFile().getName())
        .as("name is " + details.getConcatenatedExportFile().getName())
        .matches(expected + "[\\w-]{8,12}\\.pdf");
  }

  private void assertIdExtractorReturned(Folder folder) {
    assertThat(pdfExporter.getIdExtractorForFolder(config, anyUser, folder, false)).isPresent();
  }

  private void assertIdExtractorNotReturned(Folder folder) {
    assertThat(pdfExporter.getIdExtractorForFolder(config, anyUser, folder, false)).isNotPresent();
  }
}
