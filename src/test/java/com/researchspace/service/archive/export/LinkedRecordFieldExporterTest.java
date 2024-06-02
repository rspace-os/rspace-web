package com.researchspace.service.archive.export;

import static org.apache.commons.io.FileUtils.readFileToString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.Version;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.AuditManager;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class LinkedRecordFieldExporterTest {

  private LinkedRecordFieldExporter linkedRecExporter;

  private FieldExportContext context;
  private ArchiveExportConfig exportConfig;
  private FieldExporterSupport support;
  private ExportRecordList exportList;
  private AuditManager auditManager;

  private File tmpArchiveFolder;

  private ArchivalField field;

  @Before
  public void setUp() throws Exception {

    support = Mockito.mock(FieldExporterSupport.class);
    when(support.getRichTextUpdater()).thenReturn(new RichTextUpdater());

    auditManager = Mockito.mock(AuditManager.class);
    when(support.getAuditManager()).thenReturn(auditManager);

    exportList = new ExportRecordList();
    linkedRecExporter = new LinkedRecordFieldExporter(support, exportList);

    exportConfig = new ArchiveExportConfig();
    tmpArchiveFolder = Files.createTempDirectory("tmpArchiveFolder").toFile();

    field = new ArchivalField();
    context =
        new FieldExportContext(
            exportConfig, field, tmpArchiveFolder, tmpArchiveFolder, null, null, null);
  }

  @Test
  public void testUrlReplacementForInternalLink() throws URISyntaxException, IOException {

    String LINKEDRECORD_EXAMPLE = readLinkedRecordExampleHtml();

    StructuredDocument doc = TestFactory.createAnySD();
    doc.setId(4670L);
    doc.setUserVersion(new Version(2L));
    when(support.isRecord(4670L)).thenReturn(true);
    when(support.getDocumentById(4670L)).thenReturn(doc);

    exportList.add(doc.getOid());
    exportList.add(doc.getOidWithVersion());

    // check replacement for current document link
    RecordInformation recInfo = doc.toRecordInfo();
    FieldElementLinkPair<RecordInformation> recordInfo = new FieldElementLinkPair<>(recInfo, null);
    field.setFieldData(LINKEDRECORD_EXAMPLE);
    linkedRecExporter.export(context, recordInfo);
    String updatedFieldData = context.getArchiveField().getFieldData();
    assertTrue(updatedFieldData, updatedFieldData.contains("-4670.xml"));
    assertTrue(updatedFieldData, updatedFieldData.contains("/globalId/SD4670v2"));

    // check replacement of versioned link pointing to latest version
    RecordInformation recInfoLatestVersion = doc.toRecordInfo();
    recInfoLatestVersion.setOid(doc.getOidWithVersion());
    FieldElementLinkPair<RecordInformation> recordInfoLatestRecord =
        new FieldElementLinkPair<>(recInfoLatestVersion, null);
    field.setFieldData(LINKEDRECORD_EXAMPLE);
    linkedRecExporter.export(context, recordInfoLatestRecord);
    updatedFieldData = context.getArchiveField().getFieldData();
    assertTrue(updatedFieldData, updatedFieldData.contains("-4670.xml"));
    assertFalse(updatedFieldData, updatedFieldData.contains("/globalId/SD4670v2"));

    // check replacement of versioned link pointing to non-latest version
    RecordInformation recInfoOldVersion = doc.toRecordInfo();
    recInfoOldVersion.setOid(doc.getOidWithVersion());
    doc.setUserVersion(new Version(3L));
    when(auditManager.getRevisionNumberForDocumentVersion(
            doc.getId(), recInfoOldVersion.getVersion()))
        .thenReturn(5555);
    FieldElementLinkPair<RecordInformation> recordInfoOldRecord =
        new FieldElementLinkPair<>(recInfoOldVersion, null);
    field.setFieldData(LINKEDRECORD_EXAMPLE);
    linkedRecExporter.export(context, recordInfoOldRecord);
    updatedFieldData = context.getArchiveField().getFieldData();
    assertFalse(updatedFieldData, updatedFieldData.contains("-4670.xml"));
    assertFalse(updatedFieldData, updatedFieldData.contains("/globalId/SD4670v2"));
    assertTrue(updatedFieldData, updatedFieldData.contains("-4670-rev5555.xml"));
  }

  private String readLinkedRecordExampleHtml() throws IOException {
    File htmlFolder = new File("src/test/resources/TestResources/attachmentHTMLSnippets");
    return readFileToString(new File(htmlFolder, "linkedRecordExample.html"), "UTF-8");
  }
}
