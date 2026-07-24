package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiInventoryReferencingItem;
import com.researchspace.dao.InventoryFileDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.InventoryRecord.InventoryRecordType;
import com.researchspace.model.inventory.field.InventoryAttachmentField;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.LinkTargetResolver;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure unit tests for {@link InventoryFileApiManagerImpl#findAttachingItems}: the reverse lookup
 * behind the gallery info panel's "Related inventory items" attachment rows. Mirrors {@code
 * InventoryLinkManagerImplReferencingTest} on the links side. The DB-backed HQL is covered by
 * {@code InventoryFileApiManagerTest}.
 */
@ExtendWith(MockitoExtension.class)
class InventoryFileApiManagerImplAttachingTest {

  @Mock private InventoryFileDao inventoryFileDao;
  @Mock private InventoryPermissionUtils invPermissions;
  @Mock private LinkTargetResolver linkTargetResolver;
  @InjectMocks private InventoryFileApiManagerImpl manager;

  private User actor;

  @BeforeEach
  void setUp() {
    actor = new User("viewer");
    // most tests exercise the source query, so the target read-gate is open by default; the gate
    // test overrides this
    lenient().when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(true);
    lenient()
        .when(inventoryFileDao.findByMediaFileId(anyLong()))
        .thenReturn(Collections.emptyList());
    lenient()
        .when(inventoryFileDao.findAttachmentFieldsByMediaFileId(anyLong()))
        .thenReturn(Collections.emptyList());
  }

  private InventoryRecord parent(
      String globalId, String name, InventoryRecordType type, boolean deleted) {
    InventoryRecord rec = mock(InventoryRecord.class);
    lenient().when(rec.getOid()).thenReturn(new GlobalIdentifier(globalId));
    lenient().when(rec.getName()).thenReturn(name);
    lenient().when(rec.getType()).thenReturn(type);
    lenient().when(rec.isDeleted()).thenReturn(deleted);
    return rec;
  }

  private InventoryFile recordAttachment(InventoryRecord owningRecord) {
    InventoryFile file = mock(InventoryFile.class);
    lenient().when(file.getInventoryRecord()).thenReturn(owningRecord);
    return file;
  }

  private InventoryAttachmentField fieldAttachment(InventoryRecord owningRecord) {
    InventoryAttachmentField field = mock(InventoryAttachmentField.class);
    lenient().when(field.getInventoryRecord()).thenReturn(owningRecord);
    return field;
  }

  @Test
  void returnsRowForReadableRecordLevelAttachment() {
    InventoryRecord sample = parent("SA1", "My sample", InventoryRecordType.SAMPLE, false);
    // build the attachment mock BEFORE stubbing the dao: stubbing a mock inside a thenReturn
    // argument trips Mockito's unfinished-stubbing detection
    InventoryFile attachment = recordAttachment(sample);
    when(inventoryFileDao.findByMediaFileId(5L)).thenReturn(List.of(attachment));
    when(invPermissions.canUserReadInventoryRecord(sample, actor)).thenReturn(true);

    List<ApiInventoryReferencingItem> rows = manager.findAttachingItems("GL5", actor);

    assertEquals(1, rows.size());
    ApiInventoryReferencingItem row = rows.get(0);
    assertEquals("SA1", row.getSourceGlobalId());
    assertEquals("My sample", row.getSourceName());
    assertEquals("SAMPLE", row.getSourceType());
    // attachments carry no DataCite relation type; the UI labels them client-side
    assertNull(row.getRelationType());
  }

  @Test
  void resolvesFieldLevelAttachmentToItsOwningItem() {
    // a gallery file attached to a sample/template attachment field reports the owning sample, not
    // the field
    InventoryRecord template = parent("IT9", "My template", InventoryRecordType.SAMPLE, false);
    InventoryAttachmentField field = fieldAttachment(template);
    when(inventoryFileDao.findAttachmentFieldsByMediaFileId(5L)).thenReturn(List.of(field));
    when(invPermissions.canUserReadInventoryRecord(template, actor)).thenReturn(true);

    List<ApiInventoryReferencingItem> rows = manager.findAttachingItems("GL5", actor);

    assertEquals(1, rows.size());
    assertEquals("IT9", rows.get(0).getSourceGlobalId());
  }

  @Test
  void oneRowPerConnectionWithoutDedup() {
    // the same item attaching the same gallery file twice yields two rows (mirrors the links panel)
    InventoryRecord sample = parent("SA1", "My sample", InventoryRecordType.SAMPLE, false);
    InventoryFile first = recordAttachment(sample);
    InventoryFile second = recordAttachment(sample);
    when(inventoryFileDao.findByMediaFileId(5L)).thenReturn(List.of(first, second));
    when(invPermissions.canUserReadInventoryRecord(sample, actor)).thenReturn(true);

    assertEquals(2, manager.findAttachingItems("GL5", actor).size());
  }

  @Test
  void filtersOutSourcesTheActorCannotRead() {
    // leaking names/ids of items the caller may not read would defeat the per-record permission
    // check the panel relies on
    InventoryRecord readable = parent("SA10", "visible", InventoryRecordType.SAMPLE, false);
    InventoryRecord hidden = parent("SA11", "secret", InventoryRecordType.SAMPLE, false);
    InventoryFile readableAttachment = recordAttachment(readable);
    InventoryFile hiddenAttachment = recordAttachment(hidden);
    when(inventoryFileDao.findByMediaFileId(5L))
        .thenReturn(List.of(readableAttachment, hiddenAttachment));
    when(invPermissions.canUserReadInventoryRecord(readable, actor)).thenReturn(true);
    when(invPermissions.canUserReadInventoryRecord(hidden, actor)).thenReturn(false);

    List<ApiInventoryReferencingItem> rows = manager.findAttachingItems("GL5", actor);

    assertEquals(1, rows.size());
    assertEquals("SA10", rows.get(0).getSourceGlobalId());
  }

  @Test
  void skipsSoftDeletedSourceRecords() {
    InventoryRecord deleted = parent("SA12", "binned", InventoryRecordType.SAMPLE, true);
    InventoryFile attachment = recordAttachment(deleted);
    when(inventoryFileDao.findByMediaFileId(5L)).thenReturn(List.of(attachment));

    assertTrue(manager.findAttachingItems("GL5", actor).isEmpty());
  }

  @Test
  void skipsAttachmentWhoseOwningRecordCannotBeResolved() {
    // a field-level attachment surfaced by the record query has a null owning record; it is found
    // instead via the attachment-field query, so the null must be skipped rather than NPE
    InventoryFile orphan = recordAttachment(null);
    when(inventoryFileDao.findByMediaFileId(5L)).thenReturn(List.of(orphan));

    assertTrue(manager.findAttachingItems("GL5", actor).isEmpty());
  }

  @Test
  void rejectsNonGalleryTargetWithoutQuerying() {
    // attachments only target gallery files; a non-GL id is treated as not-found and never queried
    assertThrows(ApiRuntimeException.class, () -> manager.findAttachingItems("SA42", actor));
    verify(inventoryFileDao, never()).findByMediaFileId(anyLong());
  }

  @Test
  void rejectsMalformedGlobalId() {
    assertThrows(ApiRuntimeException.class, () -> manager.findAttachingItems("not-a-gid", actor));
  }

  @Test
  void rejectsTargetThatIsNotAReadableGalleryFile() {
    // the resolver returns false for an unreadable, missing, or wrong-type (non-media) target; all
    // collapse to the same not-found so the response never confirms the file exists, and the source
    // queries never run
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(false);

    assertThrows(ApiRuntimeException.class, () -> manager.findAttachingItems("GL5", actor));
    verify(inventoryFileDao, never()).findByMediaFileId(anyLong());
  }
}
