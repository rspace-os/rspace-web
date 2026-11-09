package com.researchspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.researchspace.archive.ExportScope;
import com.researchspace.export.pdf.ExportFormat;
import com.researchspace.export.pdf.ExportOperationDetails;
import com.researchspace.export.pdf.ExportProcessor;
import com.researchspace.export.pdf.ExportProcessorInput;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.export.pdf.HTMLStringGenerator;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.model.SearchDepth;
import com.researchspace.model.User;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.IMediaFactory;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
  @Mock CommunicationManager commMgr;
  @Mock FolderManager fMger;
  @Mock RecordManager recordManager;
  @Mock IMediaFactory ecatDocumentFactory;
  @Mock ExportProcessor processor;
  @Mock HTMLStringGenerator htmlStringGenerator;
  @Mock FileStore fileStore;
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

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedExportNotifiesExporterWithoutCreatingGalleryFile(boolean mergeFailure)
      throws Exception {
    otherUser = TestFactory.createAnyUser("exporter");
    config.setExportName("empty <folder>");
    config.setExportFormat("PDF");
    config.setExportScope(ExportScope.SELECTION);
    Folder folder = TestFactory.createAFolder("empty", anyUser);
    folder.setId(1L);
    when(fMger.getFolder(1L, anyUser, SearchDepth.INFINITE)).thenReturn(folder);
    when(permissions.isPermitted(folder, PermissionType.READ, anyUser)).thenReturn(true);
    ReflectionTestUtils.setField(pdfExporter, "exportProcessors", List.of(processor));
    if (mergeFailure) {
      StructuredDocument doc = TestFactory.createAnySD();
      doc.setOwner(anyUser);
      doc.setId(2L);
      folder.addChild(doc, anyUser);
      when(recordManager.getRecordWithFields(2L, anyUser)).thenReturn(doc);
      when(permissions.isPermitted(doc, PermissionType.READ, anyUser)).thenReturn(true);
      when(htmlStringGenerator.extractHtmlStr(doc, config))
          .thenReturn(new ExportProcessorInput("content", List.of(), null, List.of(), Set.of()));
      when(processor.supportsFormat(ExportFormat.PDF)).thenReturn(true);
      when(processor.concatenateExportedFilesIntoOne(
              any(File.class), anyList(), same(fileStore), any(FileProperty.class), same(config)))
          .thenThrow(new IOException("merge failed"));
    }

    var result =
        pdfExporter.doExport(
            anyUser,
            new Long[] {1L},
            new String[] {"empty"},
            new String[] {"FOLDER"},
            config,
            otherUser);

    assertNull(result.getEcatDocumentFile());
    String detail =
        mergeFailure
            ? "Export could not be generated"
            : "Export cancelled because the selection contains no data";
    verify(commMgr)
        .systemNotify(
            eq(NotificationType.PROCESS_COMPLETED),
            argThat(
                message -> message.contains("empty &lt;folder&gt;") && message.contains(detail)),
            eq(otherUser.getUsername()),
            eq(true));
    verifyNoMoreInteractions(commMgr);
    verifyNoInteractions(ecatDocumentFactory);
    verify(recordManager, never()).getGalleryMediaFolderForUser(anyString(), any(User.class));
    if (!mergeFailure) {
      verifyNoInteractions(processor, htmlStringGenerator, fileStore);
    }
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
