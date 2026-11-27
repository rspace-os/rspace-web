package com.researchspace.service.archive.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.dao.ArchiveDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.RoRService;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class XMLArchiveExportManagerServiceImplTest {

  @Mock private RecordDao recordDao;
  @Mock private ArchiveDao archiveDao;
  @Mock private BeanFactory beanFactory;
  @Mock private ArchiveNamingStrategy archiveNamingStrategy;
  @Mock private ArchiveExportPlanner archivePlanner;
  @Mock private RoRService rorService;
  @Mock private ExportObjectGenerator exportObjectGenerator;

  @InjectMocks private XMLArchiveExportManagerServiceImpl exporter;

  @TempDir File exportFolder;

  @Test
  void failureWritingADocumentFailsTheWholeExport() throws Exception {
    ReflectionTestUtils.setField(exporter, "dataHandlers", List.of());
    StructuredDocument doc = TestFactory.createAnySD();
    doc.setId(1L);
    GlobalIdentifier docId = doc.getOid();
    ExportRecordList exportList = new ExportRecordList();
    exportList.add(docId);
    ArchiveExportConfig config = new ArchiveExportConfig();
    config.setTopLevelExportFolder(exportFolder);
    config.setExportScope(ExportScope.SELECTION);

    when(beanFactory.getBean(ExportObjectGenerator.class)).thenReturn(exportObjectGenerator);
    when(archiveNamingStrategy.generateArchiveName(
            any(IArchiveExportConfig.class), any(ExportContext.class)))
        .thenReturn("RSpace-export");
    when(recordDao.get(1L)).thenReturn(doc);
    when(archivePlanner.getVersionsToExportForRecord(config, docId, doc))
        .thenReturn(List.of(new AuditedRecord(doc, null)));
    when(exportObjectGenerator.makeRecordExport(
            any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any()))
        .thenThrow(new ExportFailureException("Error writing form XML"));

    RecordExportFailureException failure =
        assertThrows(
            RecordExportFailureException.class,
            () -> exporter.exportArchive(new ArchiveManifest(), exportList, config));
    verifyNoInteractions(archiveDao);
    assertEquals("Error writing form XML", failure.getCause().getMessage());
    String userMessage = failure.resolve((code, args) -> code + " " + Arrays.toString(args));
    assertThat(userMessage)
        .startsWith(RecordExportFailureException.MESSAGE_CODE + " [" + doc.getName() + ", SD1, ")
        .doesNotContain("Error writing form XML");
  }
}
