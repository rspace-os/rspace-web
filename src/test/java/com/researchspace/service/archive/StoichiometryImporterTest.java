package com.researchspace.service.archive;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.service.FieldManager;
import com.researchspace.service.StoichiometryService;
import com.researchspace.service.archive.StoichiometryImporter.IdAndRevision;
import com.researchspace.service.archive.export.StoichiometryReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class StoichiometryImporterTest {

  public static final String NEW_FIELD_FIELDDATA = "newFieldFielData";
  public static final String NEW_FIELD_FIELDDATA_REAL =
      "<img "
          + "data-stoichiometry-table=\"{&quot;id&quot;:1"
          + ",&quot;revision&quot;:10"
          + "}\">";
  @Mock private ArchivalField oldField;
  private StoichiometryImporter testee;
  @Mock private ArchivalGalleryMetadata oldChemElement;
  @Mock private RSChemElement currentChem;
  @Mock private StoichiometryService service;
  @Mock private Field newField;
  @Mock private User user;
  @Mock private StoichiometryDTO existingStoich;
  @Mock private StructuredDocument strucDoc;
  @Mock private FieldManager fieldManager;
  @Mock private StoichiometryReader reader;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    Stoichiometry newStoichIometry = new Stoichiometry();
    newStoichIometry.setId(33L);
    when(oldChemElement.getId()).thenReturn(1L);
    when(newField.getStructuredDocument()).thenReturn(strucDoc);
    when(newField.getFieldData()).thenReturn(NEW_FIELD_FIELDDATA);
    when(service.createNewFromDataWithoutInventoryLinks(
            eq(existingStoich), eq(currentChem), eq(user)))
        .thenReturn(newStoichIometry);
    testee = new StoichiometryImporter(service, reader, oldField, newField, fieldManager, user);
  }

  @Test
  public void testNoStoichiometries() {
    when(oldField.getStoichiometries()).thenReturn(new ArrayList<>());
    testee.importStoichiometries(oldChemElement, currentChem);
    verifyZeroInteractions(service);
  }

  @Test
  public void testWhenStoichiometriesButNoParentReactionId() {
    when(oldField.getStoichiometries()).thenReturn(List.of(existingStoich));
    testee.importStoichiometries(oldChemElement, currentChem);
    verifyZeroInteractions(service);
  }

  @Test
  public void testWhenStoichiometriesButNoMatchToParentReaction() {
    when(oldField.getStoichiometries()).thenReturn(List.of(existingStoich));
    when(existingStoich.getParentReactionId()).thenReturn(123456789L);
    testee.importStoichiometries(oldChemElement, currentChem);
    verifyZeroInteractions(service);
  }

  @Test
  public void testWhenStoichiometriesMatchParentReaction() {
    when(oldField.getStoichiometries()).thenReturn(List.of(existingStoich));
    when(existingStoich.getParentReactionId()).thenReturn(1L);
    testee.importStoichiometries(oldChemElement, currentChem);
    verify(service)
        .createNewFromDataWithoutInventoryLinks(eq(existingStoich), eq(currentChem), eq(user));
    IdAndRevision idAndRevision = new IdAndRevision();
    idAndRevision.id = 33L;
    verify(reader)
        .createReplacementHtmlContentForTargetStoichiometryInFieldData(
            eq(NEW_FIELD_FIELDDATA), eq(existingStoich), eq(idAndRevision));
  }

  @Test
  public void testWhenStoichiometriesMatchParentReactionUsingARealStoichiometryReader() {
    when(oldField.getStoichiometries()).thenReturn(List.of(existingStoich));
    when(existingStoich.getParentReactionId()).thenReturn(1L);
    when(existingStoich.getRevision()).thenReturn(10L);
    when(existingStoich.getId()).thenReturn(1L);
    when(newField.getFieldData()).thenReturn(NEW_FIELD_FIELDDATA_REAL);
    testee =
        new StoichiometryImporter(
            service, new StoichiometryReader(), oldField, newField, fieldManager, user);
    testee.importStoichiometries(oldChemElement, currentChem);
    verify(service)
        .createNewFromDataWithoutInventoryLinks(eq(existingStoich), eq(currentChem), eq(user));
    verify(newField)
        .setFieldData(
            "<img data-stoichiometry-table=\"{&quot;id&quot;:33,&quot;revision&quot;:null}\">");
  }

  // --- importReactionlessStoichiometries (RSDEV-1091) ---

  @Test
  public void testReactionlessImport_whenEmptyStoichiometriesList_doesNothing() {
    when(oldField.getStoichiometries()).thenReturn(new ArrayList<>());
    testee.importReactionlessStoichiometries(Collections.emptySet());
    verifyZeroInteractions(service);
  }

  @Test
  public void testReactionlessImport_whenAllStoichsAreReactionLinked_doesNothing() {
    when(oldField.getStoichiometries()).thenReturn(List.of(existingStoich));
    when(existingStoich.getParentReactionId()).thenReturn(1L);
    testee.importReactionlessStoichiometries(Set.of(1L));
    verify(service, never()).createReactionlessFromArchive(any(), any(), any());
  }

  @Test
  public void
      testReactionlessImport_whenStoichHasNullParentReaction_callsServiceAndRewritesFieldHtml() {
    Stoichiometry created = new Stoichiometry();
    created.setId(77L);
    when(oldField.getStoichiometries()).thenReturn(List.of(existingStoich));
    when(existingStoich.getParentReactionId()).thenReturn(null);
    when(service.createReactionlessFromArchive(eq(existingStoich), eq(strucDoc), eq(user)))
        .thenReturn(created);

    testee.importReactionlessStoichiometries(Collections.emptySet());

    verify(service).createReactionlessFromArchive(eq(existingStoich), eq(strucDoc), eq(user));
    StoichiometryImporter.IdAndRevision expected = new StoichiometryImporter.IdAndRevision();
    expected.id = 77L;
    verify(reader)
        .createReplacementHtmlContentForTargetStoichiometryInFieldData(
            eq(NEW_FIELD_FIELDDATA), eq(existingStoich), eq(expected));
    verify(fieldManager).save(eq(newField), eq(user));
  }

  @Test
  public void testReactionlessImport_whenOrphanReactionLinkedStoich_doesNotImport() {
    // existingStoich has a parentReactionId that is NOT in the imported chem element set.
    // The production code emits a log.warn for this case (see StoichiometryImporter); we only
    // assert the user-facing behaviour here (no import) — the log line is best-effort
    // observability.
    when(oldField.getStoichiometries()).thenReturn(List.of(existingStoich));
    when(existingStoich.getParentReactionId()).thenReturn(99999L);

    testee.importReactionlessStoichiometries(Set.of(1L, 2L));

    verify(service, never()).createReactionlessFromArchive(any(), any(), any());
    verify(fieldManager, never()).save(any(), any());
  }
}
