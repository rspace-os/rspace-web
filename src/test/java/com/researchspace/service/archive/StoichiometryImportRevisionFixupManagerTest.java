package com.researchspace.service.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.service.AuditManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.archive.export.StoichiometryReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StoichiometryImportRevisionFixupManagerTest {

  @Mock private AuditManager auditManager;
  @Mock private FieldManager fieldManager;
  @Mock private User user;
  @Mock private ImportArchiveReport report;

  @InjectMocks private StoichiometryImportRevisionFixupManagerImpl testee;


  private StructuredDocument mockStructuredDocument(long id) {
    StructuredDocument doc = Mockito.mock(StructuredDocument.class);
    when(doc.getId()).thenReturn(id);
    return doc;
  }

  @Test
  void noOpWhenNoImportedRecords() {
    when(report.getImportedRecords()).thenReturn(Collections.emptySet());
    testee.fixupStoichiometryRevisions(report, user);
    verify(fieldManager, never()).getFieldsByRecordId(any(Long.class), any(User.class));
  }

  @Test
  void skipsNonStructuredDocumentRecords() {
    Set<BaseRecord> records = new HashSet<>();
    records.add(new Folder());
    when(report.getImportedRecords()).thenReturn(records);
    testee.fixupStoichiometryRevisions(report, user);
    verify(fieldManager, never()).getFieldsByRecordId(any(Long.class), any(User.class));
  }

  private Field mockFieldWithData(long id, String fieldData) {
    Field field = Mockito.mock(Field.class);
    Mockito.lenient().when(field.getId()).thenReturn(id);
    final String[] data = {fieldData};
    when(field.getFieldData()).thenAnswer(inv -> data[0]);
    Mockito.lenient()
        .doAnswer(
            inv -> {
              data[0] = inv.getArgument(0);
              return null;
            })
        .when(field)
        .setFieldData(Mockito.anyString());
    return field;
  }

  @Test
  void skipsFieldsWithNoStoichiometryData() {
    setupRecordInReport();

    Field field = mockFieldWithData(200L, "<p>No stoichiometry here</p>");
    when(fieldManager.getFieldsByRecordId(100L, user)).thenReturn(List.of(field));

    testee.fixupStoichiometryRevisions(report, user);
    verify(fieldManager, never()).save(any(Field.class), any(User.class));
  }

  private void setupRecordInReport() {
    StructuredDocument doc = mockStructuredDocument(100L);
    Set<BaseRecord> records = new HashSet<>();
    records.add(doc);
    when(report.getImportedRecords()).thenReturn(records);
  }

  @Test
  void fixesNullRevisionWithActualEnversRevision() {
    setupRecordInReport();

    String fieldHtml =
        "<img data-stoichiometry-table=\"{&quot;id&quot;:42,&quot;revision&quot;:null}\">";
    Field field = mockFieldWithData(200L, fieldHtml);
    when(fieldManager.getFieldsByRecordId(100L, user)).thenReturn(List.of(field));

    Stoichiometry stoich = new Stoichiometry();
    stoich.setId(42L);
    AuditedEntity<Stoichiometry> audited = new AuditedEntity<>(stoich, 789);
    when(auditManager.getNewestRevisionForEntity(Stoichiometry.class, 42L)).thenReturn(audited);

    testee.fixupStoichiometryRevisions(report, user);

    verify(fieldManager).save(eq(field), eq(user));
    String savedData = field.getFieldData();
    StoichiometryReader reader = new StoichiometryReader();
    var stoichiometries = reader.extractStoichiometriesFromFieldContents(savedData);
    assertEquals(1, stoichiometries.size());
    assertEquals(42L, stoichiometries.get(0).getId());
    assertEquals(789L, stoichiometries.get(0).getRevision());
  }

  @Test
  void fixesMultipleStoichiometriesInOneField() {
    setupRecordInReport();

    String fieldHtml =
        "<img data-stoichiometry-table=\"{&quot;id&quot;:10,&quot;revision&quot;:null}\">"
            + "<img data-stoichiometry-table=\"{&quot;id&quot;:20,&quot;revision&quot;:null}\">";
    Field field = mockFieldWithData(200L, fieldHtml);
    when(fieldManager.getFieldsByRecordId(100L, user)).thenReturn(List.of(field));

    Stoichiometry stoich1 = new Stoichiometry();
    stoich1.setId(10L);
    AuditedEntity<Stoichiometry> audited1 = new AuditedEntity<>(stoich1, 100);
    when(auditManager.getNewestRevisionForEntity(Stoichiometry.class, 10L)).thenReturn(audited1);

    Stoichiometry stoich2 = new Stoichiometry();
    stoich2.setId(20L);
    AuditedEntity<Stoichiometry> audited2 = new AuditedEntity<>(stoich2, 200);
    when(auditManager.getNewestRevisionForEntity(Stoichiometry.class, 20L)).thenReturn(audited2);

    testee.fixupStoichiometryRevisions(report, user);

    verify(fieldManager).save(eq(field), eq(user));
    String savedData = field.getFieldData();
    StoichiometryReader reader = new StoichiometryReader();
    var stoichiometries = reader.extractStoichiometriesFromFieldContents(savedData);
    assertEquals(2, stoichiometries.size());
    assertTrue(stoichiometries.stream().allMatch(s -> s.getRevision() != null));
  }

  @Test
  void logsWarningAndContinuesWhenEnversReturnsNull() {
    setupRecordInReport();

    String fieldHtml =
        "<img data-stoichiometry-table=\"{&quot;id&quot;:42,&quot;revision&quot;:null}\">";
    Field field = mockFieldWithData(200L, fieldHtml);
    when(fieldManager.getFieldsByRecordId(100L, user)).thenReturn(List.of(field));

    when(auditManager.getNewestRevisionForEntity(Stoichiometry.class, 42L)).thenReturn(null);

    // Should not throw — logs warning and continues
    testee.fixupStoichiometryRevisions(report, user);

    // Field should NOT be saved since the revision couldn't be resolved
    verify(fieldManager, never()).save(any(Field.class), any(User.class));
  }

  @Test
  void doesNotSkipStoichiometriesWithExistingRevision() {
    setupRecordInReport();
    Stoichiometry stoich2 = new Stoichiometry();
    stoich2.setId(20L);
    AuditedEntity<Stoichiometry> audited2 = new AuditedEntity<>(stoich2, 200);
    when(auditManager.getNewestRevisionForEntity(Stoichiometry.class, 42L)).thenReturn(audited2);
    String fieldHtml =
        "<img data-stoichiometry-table=\"{&quot;id&quot;:42,&quot;revision&quot;:123}\">";
    Field field = mockFieldWithData(200L, fieldHtml);
    when(fieldManager.getFieldsByRecordId(100L, user)).thenReturn(List.of(field));

    testee.fixupStoichiometryRevisions(report, user);

    verify(auditManager).getNewestRevisionForEntity(any(Class.class), any(Long.class));
    verify(fieldManager).save(any(Field.class), any(User.class));
  }
}
