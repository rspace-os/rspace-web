package com.researchspace.service.archive.export;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.core.util.progress.ProgressMonitor.NULL_MONITOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.core.util.ZipUtils;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ChoiceFieldDTO;
import com.researchspace.model.dtos.DateFieldDTO;
import com.researchspace.model.dtos.FormFieldSource;
import com.researchspace.model.dtos.NumberFieldDTO;
import com.researchspace.model.dtos.RadioFieldDTO;
import com.researchspace.model.dtos.StringFieldDTO;
import com.researchspace.model.dtos.TextFieldDTO;
import com.researchspace.model.dtos.TimeFieldDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import com.researchspace.service.archive.ArchiveImporterManager;
import com.researchspace.service.archive.ImportArchiveReport;
import com.researchspace.service.archive.ImportStrategy;
import com.researchspace.testutils.ArchiveTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * RSDEV-1140 regression test: an XML archive of ordinary multi-field documents must re-import with
 * every field's content intact <em>and</em> in the same order.
 *
 * <p>This is the committable, synthetic equivalent of the reported failure. It exercises the
 * form-rebuild path on import, which used to drop a non-deterministic number of trailing fields
 * (and intermittently NPE or scramble field order) even when the archive's form and document agree
 * on the field set, because the rebuild left a transient field form on the form's {@code
 * cascade=ALL, orphanRemoval=true} collection that later merges re-cascaded.
 *
 * <p>The loss only surfaces with a warm Hibernate / 2nd-level cache and several distinct
 * multi-field forms rebuilt in one transaction, so the test builds several documents (each from its
 * own multi-field form) into one archive and re-imports it repeatedly within a single JVM. Each
 * form's fields are reversed after creation so columnIndex order differs from creation order,
 * making field order a meaningful assertion (and mirroring a form whose fields were reordered after
 * creation). On the pre-fix code this fails; with the field-form persistence fix it passes every
 * time.
 */
public class ArchiveImportPreservesAllFieldsIT extends RealTransactionSpringTestBase {

  private static final int NUM_FORMS = Integer.getInteger("rsdev1140.forms", 4);
  private static final int FIELDS_PER_FORM = Integer.getInteger("rsdev1140.fields", 8);
  private static final int IMPORT_ITERATIONS = Integer.getInteger("rsdev1140.iterations", 3);

  @Autowired private ArchiveImporterManager importer;

  @Autowired
  @Qualifier("importUsersAndRecords")
  private ImportStrategy importStrategy;

  @Autowired
  @Qualifier("archiveManager")
  private ArchiveExportServiceManager archiveService;

  @Autowired private ArchiveExportPlanner archivePlanner;

  @TempDir public File tempExportFolder;
  @TempDir public File tempImportFolder;

  @Test
  public void mandatoryFieldRemainsRequiredThroughXmlExportAndImport() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("exporter"));
    initUser(user);
    logoutAndLoginAs(user);

    String run = getRandomName(6);
    List<FieldDefinition> fieldDefinitions = allFieldDefinitions(run);

    RSForm form = formMgr.create(user);
    for (FieldDefinition fieldDefinition : fieldDefinitions) {
      formMgr.createFieldForm(fieldDefinition.source(), form.getId(), user);
    }
    formMgr.publish(form.getId(), true, null, user);

    StructuredDocument doc =
        recordMgr.createNewStructuredDocument(user.getRootFolder().getId(), form.getId(), user);
    String docName = "Doc_with_required_field_" + run;
    doc.setName(docName);
    recordMgr.save(doc, user);

    ArchiveManifest manifest = new ArchiveManifest();
    ExportRecordList exportList = new ExportRecordList();
    exportList.add(doc.getOid());
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(user, tempExportFolder);
    archivePlanner.updateExportListWithLinkedRecords(exportList, expCfg);
    File zipFile = archiveService.exportArchive(manifest, exportList, expCfg).getExportFile();

    File expandedArchive = newFolder(tempImportFolder, "expanded-export");
    ZipUtils.extractZip(zipFile, expandedArchive);
    File formXml = findXmlContaining(expandedArchive, "<name>" + fieldDefinitions.get(0).name());

    for (FieldDefinition fieldDefinition : fieldDefinitions) {
      assertEquals(
          Boolean.toString(fieldDefinition.mandatory()),
          getRequiredAttribute(formXml, "fieldForm", "name", fieldDefinition.name()),
          fieldDefinition.type() + " form field required flag");
    }

    ArchivalImportConfig importConfig =
        createDefaultArchiveImportConfig(user, newFolder(tempImportFolder, "imported-archive"));
    ImportArchiveReport report =
        importer.importArchive(zipFile, importConfig, NULL_MONITOR, importStrategy::doImport);

    assertTrue(report.isSuccessful());
    StructuredDocument importedDoc = findImportedDoc(report, docName, user);
    RSForm importedForm = formMgr.getWithPopulatedFieldForms(importedDoc.getForm().getId(), user);
    Map<String, Boolean> importedFieldRequirements =
        importedForm.getFieldForms().stream()
            .collect(Collectors.toMap(FieldForm::getName, FieldForm::isMandatory));

    for (FieldDefinition fieldDefinition : fieldDefinitions) {
      assertEquals(
          fieldDefinition.mandatory(),
          importedFieldRequirements.get(fieldDefinition.name()),
          fieldDefinition.type() + " imported form field required flag");
    }
  }

  @Test
  public void everyFieldOfEveryDocumentSurvivesRepeatedImportInOrder() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("exporter"));
    initUser(user);
    logoutAndLoginAs(user);

    String run = getRandomName(6);
    // docName -> field names in display (columnIndex) order, the order we assert round-trips.
    Map<String, List<String>> expectedOrder = new LinkedHashMap<>();
    // docName -> (field name -> unique content marker).
    Map<String, Map<String, String>> markers = new LinkedHashMap<>();
    ExportRecordList exportList = new ExportRecordList();

    for (int f = 0; f < NUM_FORMS; f++) {
      RSForm form = formMgr.create(user);
      for (int i = 0; i < FIELDS_PER_FORM; i++) {
        formMgr.createFieldForm(
            new TextFieldDTO<TextFieldForm>("Field_" + f + "_" + i, ""), form.getId(), user);
      }
      // Reverse the field order so columnIndex order differs from creation order, then publish.
      List<Long> reversedIds =
          formMgr.getWithPopulatedFieldForms(form.getId(), user).getFieldForms().stream()
              .sorted(Comparator.comparingInt(FieldForm::getColumnIndex))
              .map(FieldForm::getId)
              .collect(Collectors.toList());
      Collections.reverse(reversedIds);
      formMgr.reorderFields(form.getId(), reversedIds, user);
      formMgr.publish(form.getId(), true, null, user);

      StructuredDocument doc =
          recordMgr.createNewStructuredDocument(user.getRootFolder().getId(), form.getId(), user);
      String docName = "Doc_" + f + "_" + run;
      doc.setName(docName);

      List<Field> orderedFields =
          doc.getFields().stream()
              .sorted(Comparator.comparingInt(Field::getColumnIndex))
              .collect(Collectors.toList());
      List<String> order = new ArrayList<>();
      Map<String, String> docMarkers = new LinkedHashMap<>();
      for (int i = 0; i < orderedFields.size(); i++) {
        Field field = orderedFields.get(i);
        String marker = "MARK_" + run + "_" + f + "_" + i;
        order.add(field.getName());
        docMarkers.put(field.getName(), marker);
        field.setFieldData(field.getName() + " content " + marker);
        fieldMgr.save(field, user);
      }
      recordMgr.save(doc, user);
      expectedOrder.put(docName, order);
      markers.put(docName, docMarkers);
      exportList.add(doc.getOid());
    }

    // Export all documents into one archive.
    ArchiveManifest manifest = new ArchiveManifest();
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(user, tempExportFolder);
    expCfg.setExportScope(ExportScope.SELECTION);
    archivePlanner.updateExportListWithLinkedRecords(exportList, expCfg);
    String zipName =
        archiveService.exportArchive(manifest, exportList, expCfg).getExportFile().getName();
    File zipFile = new File(tempExportFolder, zipName);

    // Re-import repeatedly in this JVM; assert every field of every document survives, in order.
    List<String> failures = new ArrayList<>();
    for (int iteration = 1; iteration <= IMPORT_ITERATIONS; iteration++) {
      try {
        ArchivalImportConfig iconfig =
            createDefaultArchiveImportConfig(
                user, newFolder(tempImportFolder, "import-" + iteration));
        ImportArchiveReport report =
            importer.importArchive(zipFile, iconfig, NULL_MONITOR, importStrategy::doImport);
        if (!report.isSuccessful()) {
          failures.add(
              String.format(
                  "iteration %d: import reported failure: %s",
                  iteration, report.getErrorList().getAllErrorMessagesAsStringsSeparatedBy("; ")));
          continue;
        }
        for (Map.Entry<String, List<String>> docEntry : expectedOrder.entrySet()) {
          String docName = docEntry.getKey();
          StructuredDocument imported = findImportedDoc(report, docName, user);
          List<Field> orderedFields =
              imported.getFields().stream()
                  .sorted(Comparator.comparingInt(Field::getColumnIndex))
                  .collect(Collectors.toList());
          List<String> importedOrder =
              orderedFields.stream().map(Field::getName).collect(Collectors.toList());
          String allData =
              orderedFields.stream().map(Field::getFieldData).reduce("", (a, b) -> a + "\n" + b);

          for (Map.Entry<String, String> m : markers.get(docName).entrySet()) {
            if (!allData.contains(m.getValue())) {
              failures.add(
                  String.format(
                      "iteration %d, %s: lost content of field '%s' (importedFields=%s)",
                      iteration, docName, m.getKey(), importedOrder));
            }
          }
          if (!importedOrder.equals(docEntry.getValue())) {
            failures.add(
                String.format(
                    "iteration %d, %s: field order changed: expected %s but got %s",
                    iteration, docName, docEntry.getValue(), importedOrder));
          }
        }
      } catch (Exception e) {
        failures.add(String.format("iteration %d: threw %s", iteration, e));
      }
    }

    assertThat(failures)
        .as(
            String.format(
                "RSDEV-1140: archive import dropped or reordered field content on %d check(s)"
                    + " across %d imports of %d documents (%d fields each). Failures:%n%s",
                failures.size(),
                IMPORT_ITERATIONS,
                NUM_FORMS,
                FIELDS_PER_FORM,
                String.join("\n", failures)))
        .isEmpty();
  }

  private StructuredDocument findImportedDoc(ImportArchiveReport report, String name, User user) {
    for (BaseRecord rec : report.getImportedRecords()) {
      if (rec.isStructuredDocument() && name.equals(rec.getName())) {
        // getRecordWithFields eagerly initialises the fields collection so it can be read outside
        // the import transaction (plain get() leaves it as a lazy proxy).
        return (StructuredDocument) recordMgr.getRecordWithFields(rec.getId(), user);
      }
    }
    throw new IllegalStateException("imported document '" + name + "' not found in report");
  }

  private static List<FieldDefinition> allFieldDefinitions(String run) {
    return List.of(
        new FieldDefinition(
            "Mandatory_Number_" + run,
            true,
            FieldType.NUMBER,
            new NumberFieldDTO<>(
                "1", "10", "2", "5", FieldType.NUMBER, "Mandatory_Number_" + run, true)),
        new FieldDefinition(
            "Optional_Number_" + run,
            false,
            FieldType.NUMBER,
            new NumberFieldDTO<>(
                "1", "10", "2", "5", FieldType.NUMBER, "Optional_Number_" + run, false)),
        new FieldDefinition(
            "Mandatory_String_" + run,
            true,
            FieldType.STRING,
            new StringFieldDTO<>("Mandatory_String_" + run, true, "no", "string value")),
        new FieldDefinition(
            "Optional_String_" + run,
            false,
            FieldType.STRING,
            new StringFieldDTO<>("Optional_String_" + run, false, "no", "string value")),
        new FieldDefinition(
            "Mandatory_Text_" + run,
            true,
            FieldType.TEXT,
            new TextFieldDTO<TextFieldForm>("Mandatory_Text_" + run, true, "text value")),
        new FieldDefinition(
            "Optional_Text_" + run,
            false,
            FieldType.TEXT,
            new TextFieldDTO<TextFieldForm>("Optional_Text_" + run, false, "text value")),
        new FieldDefinition(
            "Mandatory_Radio_" + run,
            true,
            FieldType.RADIO,
            new RadioFieldDTO<>(
                "0=alpha&1=beta", "alpha", "Mandatory_Radio_" + run, false, false, true)),
        new FieldDefinition(
            "Optional_Radio_" + run,
            false,
            FieldType.RADIO,
            new RadioFieldDTO<>(
                "0=alpha&1=beta", "alpha", "Optional_Radio_" + run, false, false, false)),
        new FieldDefinition(
            "Mandatory_Choice_" + run,
            true,
            FieldType.CHOICE,
            new ChoiceFieldDTO<>(
                "0=alpha&1=beta", "yes", "0=alpha", "Mandatory_Choice_" + run, true)),
        new FieldDefinition(
            "Optional_Choice_" + run,
            false,
            FieldType.CHOICE,
            new ChoiceFieldDTO<>(
                "0=alpha&1=beta", "yes", "0=alpha", "Optional_Choice_" + run, false)),
        new FieldDefinition(
            "Mandatory_Date_" + run,
            true,
            FieldType.DATE,
            new DateFieldDTO<>(
                "2026-09-22",
                "2026-09-01",
                "2026-09-30",
                "yyyy-MM-dd",
                "Mandatory_Date_" + run,
                true)),
        new FieldDefinition(
            "Optional_Date_" + run,
            false,
            FieldType.DATE,
            new DateFieldDTO<>(
                "2026-09-22",
                "2026-09-01",
                "2026-09-30",
                "yyyy-MM-dd",
                "Optional_Date_" + run,
                false)),
        new FieldDefinition(
            "Mandatory_Time_" + run,
            true,
            FieldType.TIME,
            new TimeFieldDTO<>("10:30", "09:00", "17:00", "HH:mm", "Mandatory_Time_" + run, true)),
        new FieldDefinition(
            "Optional_Time_" + run,
            false,
            FieldType.TIME,
            new TimeFieldDTO<>("10:30", "09:00", "17:00", "HH:mm", "Optional_Time_" + run, false)));
  }

  private record FieldDefinition(
      String name,
      boolean mandatory,
      FieldType type,
      FormFieldSource<? extends FieldForm> source) {}

  private static File newFolder(File root, String... subDirs) throws IOException {
    String subFolder = String.join("/", subDirs);
    File result = new File(root, subFolder);
    if (!result.mkdirs()) {
      throw new IOException("Couldn't create folders " + root);
    }
    return result;
  }

  private File findXmlContaining(File expandedArchive, String expectedText) throws Exception {
    for (File xmlFile : ArchiveTestUtils.getAllXMLFilesInArchive(expandedArchive)) {
      if (Files.readString(xmlFile.toPath()).contains(expectedText)) {
        return xmlFile;
      }
    }
    throw new IllegalStateException("No exported XML file contained '" + expectedText + "'");
  }

  private String getRequiredAttribute(
      File xmlFile, String elementName, String childElementName, String childValue)
      throws Exception {
    Element element = findElementByChildText(xmlFile, elementName, childElementName, childValue);
    return element.getAttribute("required");
  }

  private Element findElementByChildText(
      File xmlFile, String elementName, String childElementName, String childValue)
      throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    Document document = factory.newDocumentBuilder().parse(xmlFile);
    NodeList elements = document.getElementsByTagName(elementName);
    for (int i = 0; i < elements.getLength(); i++) {
      Element element = (Element) elements.item(i);
      NodeList childElements = element.getElementsByTagName(childElementName);
      if (childElements.getLength() > 0
          && childValue.equals(childElements.item(0).getTextContent())) {
        return element;
      }
    }
    throw new IllegalStateException(
        "No <" + elementName + "> in " + xmlFile + " has <" + childElementName + ">" + childValue);
  }
}
