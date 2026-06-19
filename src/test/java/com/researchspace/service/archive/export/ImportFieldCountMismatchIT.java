package com.researchspace.service.archive.export;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.core.util.progress.ProgressMonitor.NULL_MONITOR;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.core.util.ZipUtils;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.dtos.TextFieldDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import com.researchspace.service.archive.ArchiveImporterManager;
import com.researchspace.service.archive.ImportArchiveReport;
import com.researchspace.service.archive.ImportStrategy;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * RSDEV-1140 reproduction.
 *
 * <p>An RSpace XML archive bundles, per document, both the document's field content and a copy of
 * its form. When a field is deleted from a form, the exported form copy excludes that field
 * (RSPAC-1793) but a document created before the deletion still carries the field's content, so the
 * archive's form copy can have fewer fields than the document. On import, {@code
 * AbstractImporterStrategyImpl} rebuilt the document from the form copy and copied archived field
 * content across <em>by positional index</em>, iterating only over min(formFields, documentFields)
 * - silently dropping the trailing archived field and losing its content even though the import
 * reported success.
 *
 * <p>This test recreates that archive deterministically: it exports a two-field document, then
 * removes one field from the archive's {@code _form.xml} (leaving the document content intact),
 * mimicking a form whose field was deleted. The imported document must still contain the content of
 * the removed-from-form field.
 */
public class ImportFieldCountMismatchIT extends RealTransactionSpringTestBase {

  private static final String FIRST_FIELD = "FirstField";
  private static final String LAST_FIELD = "LastField";

  @Autowired private ArchiveImporterManager importer;

  @Autowired
  @Qualifier("importUsersAndRecords")
  private ImportStrategy importStrategy;

  @Autowired
  @Qualifier("archiveManager")
  private ArchiveExportServiceManager archiveService;

  @Autowired private ArchiveExportPlanner archivePlanner;

  @Rule public TemporaryFolder tempExportFolder = new TemporaryFolder();
  @Rule public TemporaryFolder tempImportFolder = new TemporaryFolder();
  @Rule public TemporaryFolder tempRezipFolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void trailingFieldNotDroppedWhenFormHasFewerFieldsThanDocument() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("exporter"));
    initUser(user);
    logoutAndLoginAs(user);

    // 1. Build and publish a form with two text fields.
    RSForm form = formMgr.create(user);
    formMgr.createFieldForm(
        new TextFieldDTO<TextFieldForm>(FIRST_FIELD, "first"), form.getId(), user);
    formMgr.createFieldForm(
        new TextFieldDTO<TextFieldForm>(LAST_FIELD, "last"), form.getId(), user);
    formMgr.publish(form.getId(), true, null, user);

    // 2. Create a document from the form and put a unique marker in its LAST field.
    StructuredDocument doc =
        recordMgr.createNewStructuredDocument(user.getRootFolder().getId(), form.getId(), user);
    String marker = "PUBLICATIONS_MARKER_" + getRandomName(8);
    Field lastField = doc.getFields().get(doc.getFields().size() - 1);
    lastField.setFieldData("Publications and released datasets " + marker);
    fieldMgr.save(lastField, user);
    recordMgr.save(doc, user);
    int originalFieldCount = doc.getFields().size();

    // 3. Export the document to an XML archive.
    ArchiveManifest manifest = new ArchiveManifest();
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    expCfg.setExportScope(ExportScope.SELECTION);
    ImmutableExportRecordList list = createExportList(doc.getOid(), expCfg);
    String zipName = archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    File zipFile = new File(tempExportFolder.getRoot(), zipName);

    // 4. Remove the LAST field from the archive's form copy, leaving the document content intact.
    // This deterministically reproduces an archive whose form had a field deleted (RSPAC-1793)
    // after the document was created, so the form copy has fewer fields than the document.
    File modifiedZip = removeFieldFormFromArchive(zipFile, LAST_FIELD);

    // 5. Import the modified archive.
    ArchivalImportConfig iconfig =
        createDefaultArchiveImportConfig(user, tempImportFolder.getRoot());
    ImportArchiveReport report =
        importer.importArchive(modifiedZip, iconfig, NULL_MONITOR, importStrategy::doImport);
    assertTrue("import reported failure", report.isSuccessful());

    // 6. The content of the field removed from the form copy must survive the round trip.
    StructuredDocument imported = findImportedStructuredDocument(report, doc.getName());
    String allFieldData =
        imported.getFields().stream().map(Field::getFieldData).reduce("", (a, b) -> a + "\n" + b);
    String diagnostics =
        String.format(
            "originalDocFields=%d, importedDocFields=%d, importedNames=%s%nimported field"
                + " data:%n%s",
            originalFieldCount,
            imported.getFields().size(),
            imported.getFields().stream().map(Field::getName).collect(Collectors.toList()),
            allFieldData);
    assertTrue(
        "RSDEV-1140: field content was dropped on import. " + diagnostics,
        allFieldData.contains(marker));
  }

  /**
   * Expands the archive, removes the {@code <fieldForm>} element whose {@code <name>} matches
   * {@code fieldName} from every {@code _form.xml}, and re-zips. Mimics an exported archive whose
   * form had a field deleted while documents retained that field's content.
   */
  private File removeFieldFormFromArchive(File zipFile, String fieldName) throws Exception {
    File expandDir = tempRezipFolder.newFolder("expanded");
    String rootName = ZipUtils.extractZip(zipFile, expandDir);
    File expandedRoot = new File(rootName);
    if (!expandedRoot.isAbsolute() || !expandedRoot.exists()) {
      expandedRoot = new File(expandDir, rootName);
    }
    Collection<File> formXmls =
        FileUtils.listFiles(expandedRoot, new String[] {"xml"}, true).stream()
            .filter(f -> f.getName().endsWith("_form.xml"))
            .collect(Collectors.toList());
    boolean removedAny = false;
    for (File formXml : formXmls) {
      removedAny |= removeFieldFormElement(formXml, fieldName);
    }
    assertTrue(
        "test setup: expected to remove a '" + fieldName + "' fieldForm from an exported _form.xml",
        removedAny);
    File modifiedZip = new File(tempRezipFolder.getRoot(), "modified-archive.zip");
    ZipUtils.createZip(modifiedZip, expandedRoot);
    return modifiedZip;
  }

  private boolean removeFieldFormElement(File formXml, String fieldName) throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    org.w3c.dom.Document xml = dbf.newDocumentBuilder().parse(formXml);
    NodeList fieldForms = xml.getElementsByTagName("fieldForm");
    boolean removed = false;
    for (int i = fieldForms.getLength() - 1; i >= 0; i--) {
      Element fieldForm = (Element) fieldForms.item(i);
      NodeList names = fieldForm.getElementsByTagName("name");
      if (names.getLength() > 0 && fieldName.equals(names.item(0).getTextContent())) {
        fieldForm.getParentNode().removeChild(fieldForm);
        removed = true;
      }
    }
    if (removed) {
      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.transform(new DOMSource(xml), new StreamResult(formXml));
    }
    return removed;
  }

  private ImmutableExportRecordList createExportList(
      GlobalIdentifier id, IArchiveExportConfig cfg) {
    ExportRecordList list = new ExportRecordList();
    list.add(id);
    archivePlanner.updateExportListWithLinkedRecords(list, cfg);
    return list;
  }

  private StructuredDocument findImportedStructuredDocument(
      ImportArchiveReport report, String name) {
    for (BaseRecord rec : report.getImportedRecords()) {
      if (rec.isStructuredDocument() && name.equals(rec.getName())) {
        StructuredDocument sd = (StructuredDocument) recordMgr.get(rec.getId());
        assertNotNull(sd);
        return sd;
      }
    }
    throw new IllegalStateException("imported document '" + name + "' not found in report");
  }
}
