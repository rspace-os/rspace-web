package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryReferencingItem;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.LinkTargetResolver;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryLinkManagerImplReferencingTest {

  @Mock private InventoryLinkDao linkDao;
  @Mock private InventoryPermissionUtils permissionUtils;
  @Mock private LinkTargetResolver linkTargetResolver;
  @InjectMocks private InventoryLinkManagerImpl manager;

  private User actor;

  @BeforeEach
  void setUp() {
    actor = new User("viewer");
    // these tests cover the source query, so the target read-permission gate is open;
    // InventoryLinkManagerImplUnitTest covers the gate itself
    lenient().when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(true);
  }

  @Test
  void queriesByParsedPrefixAndDbIdForElnTarget() {
    when(linkDao.findReferencingLinkFields(GlobalIdPrefix.SD, 123L))
        .thenReturn(Collections.emptyList());

    assertTrue(manager.findReferencingItems("SD123", actor).isEmpty());

    verify(linkDao).findReferencingLinkFields(GlobalIdPrefix.SD, 123L);
  }

  @Test
  void stripsVersionSuffixSoPinnedLinksMatchTheBaseRecord() {
    when(linkDao.findReferencingLinkFields(GlobalIdPrefix.SD, 123L))
        .thenReturn(Collections.emptyList());

    manager.findReferencingItems("SD123v5", actor);

    verify(linkDao).findReferencingLinkFields(GlobalIdPrefix.SD, 123L);
  }

  @Test
  void queriesByPrefixAndDbIdForInventoryTarget() {
    when(linkDao.findReferencingLinkFields(GlobalIdPrefix.SA, 42L))
        .thenReturn(Collections.emptyList());

    assertEquals(0, manager.findReferencingItems("SA42", actor).size());

    verify(linkDao).findReferencingLinkFields(GlobalIdPrefix.SA, 42L);
  }

  private InventoryRecord parent(String globalIdString, String name, boolean deleted) {
    InventoryRecord rec = mock(InventoryRecord.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    org.mockito.Mockito.lenient()
        .when(rec.getOid())
        .thenReturn(new com.researchspace.model.core.GlobalIdentifier(globalIdString));
    org.mockito.Mockito.lenient().when(rec.getName()).thenReturn(name);
    org.mockito.Mockito.lenient().when(rec.isDeleted()).thenReturn(deleted);
    return rec;
  }

  private ExtraLinkField extraFieldRow(InventoryRecord rec, InventoryLink link) {
    ExtraLinkField field = mock(ExtraLinkField.class);
    org.mockito.Mockito.lenient().when(field.getInventoryRecord()).thenReturn(rec);
    org.mockito.Mockito.lenient().when(field.getLink()).thenReturn(link);
    return field;
  }

  private InventoryLink linkWith(String relation, Long pin, Date modifiedAt) {
    InventoryLink link = new InventoryLink();
    link.setRelationType(relation);
    link.setVersionPin(pin);
    link.setModifiedAt(modifiedAt);
    return link;
  }

  @Test
  void buildsRowsOnlyForSourcesTheActorCanRead() {
    // inverting or dropping this filter would leak names/ids of items the
    // caller may not read into the back-links panels
    InventoryRecord readable = parent("SA10", "visible sample", false);
    InventoryRecord hidden = parent("SA11", "secret sample", false);
    InventoryLink linkA = linkWith("References", 2L, new Date(1700000000000L));
    InventoryLink linkB = linkWith("Cites", null, null);
    // build the field mocks BEFORE stubbing linkDao: stubbing inside thenReturn
    // arguments trips Mockito's unfinished-stubbing detection
    ExtraLinkField readableRow = extraFieldRow(readable, linkA);
    ExtraLinkField hiddenRow = extraFieldRow(hidden, linkB);
    when(linkDao.findReferencingLinkFields(GlobalIdPrefix.SD, 123L))
        .thenReturn(List.of(readableRow, hiddenRow));
    when(permissionUtils.canUserReadInventoryRecord(readable, actor)).thenReturn(true);
    when(permissionUtils.canUserReadInventoryRecord(hidden, actor)).thenReturn(false);

    List<ApiInventoryReferencingItem> rows = manager.findReferencingItems("SD123", actor);

    assertEquals(1, rows.size());
    ApiInventoryReferencingItem row = rows.get(0);
    assertEquals("SA10", row.getSourceGlobalId());
    assertEquals("visible sample", row.getSourceName());
    assertEquals("References", row.getRelationType());
    assertEquals(Long.valueOf(2L), row.getVersionPin());
    assertEquals(Long.valueOf(1700000000000L), row.getModifiedAtMillis());
  }

  @Test
  void skipsSoftDeletedSourceRecords() {
    InventoryRecord deleted = parent("SA12", "binned sample", true);
    ExtraLinkField row = extraFieldRow(deleted, linkWith("References", null, null));
    when(linkDao.findReferencingLinkFields(GlobalIdPrefix.SD, 123L)).thenReturn(List.of(row));

    assertTrue(manager.findReferencingItems("SD123", actor).isEmpty());
  }

  @Test
  void toleratesRowWithoutModificationDate() {
    InventoryRecord rec = parent("SA13", "no date", false);
    ExtraLinkField row = extraFieldRow(rec, linkWith("References", null, null));
    when(linkDao.findReferencingLinkFields(GlobalIdPrefix.SD, 123L)).thenReturn(List.of(row));
    when(permissionUtils.canUserReadInventoryRecord(rec, actor)).thenReturn(true);

    List<ApiInventoryReferencingItem> rows = manager.findReferencingItems("SD123", actor);

    assertEquals(1, rows.size());
    org.junit.jupiter.api.Assertions.assertNull(rows.get(0).getModifiedAtMillis());
  }

  @Test
  void includesStructuredTemplateLinkFieldsAsSources() {
    // template-defined link fields are first-class links; a sample linking to
    // the target through one must appear in the back-references like an
    // extra-field link does
    InventoryRecord sample = parent("SA20", "templated sample", false);
    InventoryLink link = linkWith("IsPartOf", null, null);
    InventoryLinkField structured = mock(InventoryLinkField.class);
    org.mockito.Mockito.lenient().when(structured.getInventoryRecord()).thenReturn(sample);
    org.mockito.Mockito.lenient().when(structured.getLink()).thenReturn(link);
    when(linkDao.findReferencingLinkFields(GlobalIdPrefix.SD, 123L))
        .thenReturn(java.util.Collections.emptyList());
    when(linkDao.findReferencingStructuredLinkFields(GlobalIdPrefix.SD, 123L))
        .thenReturn(List.of(structured));
    when(permissionUtils.canUserReadInventoryRecord(sample, actor)).thenReturn(true);

    List<ApiInventoryReferencingItem> rows = manager.findReferencingItems("SD123", actor);

    assertEquals(1, rows.size());
    assertEquals("SA20", rows.get(0).getSourceGlobalId());
    assertEquals("IsPartOf", rows.get(0).getRelationType());
  }
}
