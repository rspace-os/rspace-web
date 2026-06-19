package com.researchspace.service.archive.export;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
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
 * RSDEV-1202 export-side fix (companion to the RSDEV-1140 import-side fix).
 *
 * <p>An RSpace XML archive bundles, per document, both the document's field content and a copy of
 * its form. The form copy used to be built from the form's (filter-applied) field forms, so a field
 * form that was soft-deleted after a document was created was dropped from the copy even though the
 * document still held that field's content. The exported form then had fewer fields than the
 * document, and the content was lost on import (the Vienna report, RSDEV-1140).
 *
 * <p>The export now builds the form copy from the document's own fields, so the copy always matches
 * the document. This test drives the genuine bug state through the in-place {@code
 * deleteFieldFromForm} path (the path that actually leaves a document bound to a deleted field
 * form) and asserts the exported archive is self-consistent: the {@code _form.xml} describes
 * exactly the document's fields (including the deleted-but-used field), the archived document
 * carries the same fields, and the field's content is present. End-to-end content preservation on
 * import is covered by the RSDEV-1140 import fix (and its {@code ImportFieldCountMismatchIT}); once
 * both fixes are present the exported form no longer mismatches the document at all.
 */
public class ExportFormMatchesDocumentIT extends RealTransactionSpringTestBase {

  private static final String FIRST_FIELD = "FirstField";
  private static final String LAST_FIELD = "LastField";

  @Autowired
  @Qualifier("archiveManager")
  private ArchiveExportServiceManager archiveService;

  @Autowired private ArchiveExportPlanner archivePlanner;

  @Rule public TemporaryFolder tempExportFolder = new TemporaryFolder();
  @Rule public TemporaryFolder tempExpandFolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void exportedFormMatchesDocumentIncludingDeletedButUsedField() throws Exception {
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
    List<String> documentFieldNames =
        doc.getFields().stream().map(Field::getName).collect(Collectors.toList());

    // 3. Soft-delete the LAST field's field form IN PLACE on the document's bound form. This is the
    // genuine RSPAC-1793 / Vienna trigger: the document keeps its field and content, but the field
    // form it references is now marked deleted (and so hidden by the notdeleted Hibernate filter).
    Long lastFieldFormId = ((FieldForm) lastField.getFieldForm()).getId();
    formMgr.deleteFieldFromForm(lastFieldFormId, user);

    // 4. Export the document to an XML archive.
    ArchiveManifest manifest = new ArchiveManifest();
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    expCfg.setExportScope(ExportScope.SELECTION);
    ImmutableExportRecordList list = createExportList(doc.getOid(), expCfg);
    String zipName = archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    File zipFile = new File(tempExportFolder.getRoot(), zipName);

    // 5. The exported archive must be self-consistent: the form copy and the archived document must
    // both describe exactly the document's fields, including the deleted-but-used LAST field, and
    // the field's content must be present. Before the fix the deleted field form was filtered out
    // of
    // the form copy, so the copy had fewer fields than the document.
    ArchiveContents contents = readExportedArchive(zipFile);
    assertEquals(
        "exported form field forms should match the document's fields. form="
            + contents.formFieldNames
            + " doc="
            + documentFieldNames,
        documentFieldNames,
        contents.formFieldNames);
    assertTrue(
        "deleted-but-used field '" + LAST_FIELD + "' must be in the exported form copy",
        contents.formFieldNames.contains(LAST_FIELD));
    assertEquals(
        "archived document fields should match the exported form copy. archivedDoc="
            + contents.documentFieldNames
            + " form="
            + contents.formFieldNames,
        contents.formFieldNames,
        contents.documentFieldNames);
    assertTrue(
        "the deleted-but-used field's content must be exported in the archived document",
        contents.documentXml.contains(marker));
  }

  private ImmutableExportRecordList createExportList(
      GlobalIdentifier id, IArchiveExportConfig cfg) {
    ExportRecordList list = new ExportRecordList();
    list.add(id);
    archivePlanner.updateExportListWithLinkedRecords(list, cfg);
    return list;
  }

  /** The field names declared in an exported document's {@code _form.xml} and {@code _doc.xml}. */
  private static final class ArchiveContents {
    final List<String> formFieldNames;
    final List<String> documentFieldNames;
    final String documentXml;

    ArchiveContents(
        List<String> formFieldNames, List<String> documentFieldNames, String documentXml) {
      this.formFieldNames = formFieldNames;
      this.documentFieldNames = documentFieldNames;
      this.documentXml = documentXml;
    }
  }

  /** Expands the single-document archive and reads its form and document field names. */
  private ArchiveContents readExportedArchive(File zipFile) throws Exception {
    File expandDir = tempExpandFolder.newFolder("expanded");
    String rootName = ZipUtils.extractZip(zipFile, expandDir);
    File expandedRoot = new File(rootName);
    if (!expandedRoot.isAbsolute() || !expandedRoot.exists()) {
      expandedRoot = new File(expandDir, rootName);
    }
    File formXml = findSingle(expandedRoot, f -> f.getName().endsWith("_form.xml"), "_form.xml");
    File docXml =
        findSingle(
            expandedRoot,
            f -> f.getName().endsWith(".xml") && !f.getName().endsWith("_form.xml"),
            "document .xml");
    List<String> formFieldNames = childElementTexts(parse(formXml), "fieldForm", "name");
    List<String> documentFieldNames = elementTexts(parse(docXml), "fieldName");
    return new ArchiveContents(
        formFieldNames, documentFieldNames, FileUtils.readFileToString(docXml, "UTF-8"));
  }

  private File findSingle(File root, java.util.function.Predicate<File> filter, String desc) {
    Collection<File> matches =
        FileUtils.listFiles(root, new String[] {"xml"}, true).stream()
            // the document xml lives under doc_* folders; exclude top-level manifest/link files
            .filter(f -> f.getParentFile().getName().startsWith("doc_"))
            .filter(filter)
            .collect(Collectors.toList());
    assertEquals(
        "expected exactly one " + desc + " in the archive, found " + matches, 1, matches.size());
    return matches.iterator().next();
  }

  private org.w3c.dom.Document parse(File xml) throws Exception {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml);
  }

  /** Text of each {@code <child>} that is the first such descendant of each {@code <parent>}. */
  private List<String> childElementTexts(org.w3c.dom.Document xml, String parent, String child) {
    List<String> names = new ArrayList<>();
    NodeList parents = xml.getElementsByTagName(parent);
    for (int i = 0; i < parents.getLength(); i++) {
      NodeList children = ((Element) parents.item(i)).getElementsByTagName(child);
      if (children.getLength() > 0) {
        names.add(children.item(0).getTextContent());
      }
    }
    return names;
  }

  private List<String> elementTexts(org.w3c.dom.Document xml, String tag) {
    List<String> texts = new ArrayList<>();
    NodeList nodes = xml.getElementsByTagName(tag);
    for (int i = 0; i < nodes.getLength(); i++) {
      texts.add(nodes.item(i).getTextContent());
    }
    return texts;
  }
}
