package com.researchspace.service.archive.export;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.core.util.progress.ProgressMonitor.NULL_MONITOR;
import static org.junit.Assert.assertTrue;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.model.User;
import com.researchspace.model.dtos.TextFieldDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldForm;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

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

  @Rule public TemporaryFolder tempExportFolder = new TemporaryFolder();
  @Rule public TemporaryFolder tempImportFolder = new TemporaryFolder();

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
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    expCfg.setExportScope(ExportScope.SELECTION);
    archivePlanner.updateExportListWithLinkedRecords(exportList, expCfg);
    String zipName =
        archiveService.exportArchive(manifest, exportList, expCfg).getExportFile().getName();
    File zipFile = new File(tempExportFolder.getRoot(), zipName);

    // Re-import repeatedly in this JVM; assert every field of every document survives, in order.
    List<String> failures = new ArrayList<>();
    for (int iteration = 1; iteration <= IMPORT_ITERATIONS; iteration++) {
      try {
        ArchivalImportConfig iconfig =
            createDefaultArchiveImportConfig(
                user, tempImportFolder.newFolder("import-" + iteration));
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

    assertTrue(
        String.format(
            "RSDEV-1140: archive import dropped or reordered field content on %d check(s) across %d"
                + " imports of %d documents (%d fields each). Failures:%n%s",
            failures.size(),
            IMPORT_ITERATIONS,
            NUM_FORMS,
            FIELDS_PER_FORM,
            String.join("\n", failures)),
        failures.isEmpty());
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
}
