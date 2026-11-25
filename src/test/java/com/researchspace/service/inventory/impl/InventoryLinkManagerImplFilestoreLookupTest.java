package com.researchspace.service.inventory.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.InventoryRecord.InventoryRecordType;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.service.FilestoreAclChecker;
import com.researchspace.service.inventory.FilestoreLinkingItem;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.LinkTargetResolver;
import com.researchspace.service.inventory.LinkTargetSnapshotResolver;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryLinkManagerImplFilestoreLookupTest {

  private static final List<String> PATHS = List.of("data/XRD", "data/XRD/run.dat");

  @Mock private InventoryLinkDao linkDao;
  @Mock private InventoryPermissionUtils permissionUtils;
  @Mock private LinkTargetResolver linkTargetResolver;
  @Mock private LinkTargetSnapshotResolver snapshotResolver;
  @Mock private FilestoreAclChecker aclChecker;
  @InjectMocks private InventoryLinkManagerImpl manager;

  private final User actor = new User("viewer");
  private final NfsFileSystem fileSystem = GalleryFilestoreTestUtils.createS3FileSystem(7L);

  private InventoryRecord item(String globalId, InventoryRecordType type, String ownerFullName) {
    InventoryRecord rec = mock(InventoryRecord.class, RETURNS_DEEP_STUBS);
    when(rec.getOid()).thenReturn(new GlobalIdentifier(globalId));
    when(rec.getType()).thenReturn(type);
    when(rec.getOwner().getFullName()).thenReturn(ownerFullName);
    return rec;
  }

  private InventoryRecord readable(
      String globalId, String name, InventoryRecordType type, String ownerFullName) {
    InventoryRecord rec = item(globalId, type, ownerFullName);
    when(rec.getName()).thenReturn(name);
    when(permissionUtils.canUserReadInventoryRecord(rec, actor)).thenReturn(true);
    return rec;
  }

  private InventoryRecord hidden(String globalId, InventoryRecordType type, String ownerFullName) {
    InventoryRecord rec = item(globalId, type, ownerFullName);
    when(permissionUtils.canUserReadInventoryRecord(rec, actor)).thenReturn(false);
    return rec;
  }

  private static InventoryLink link(String relation) {
    InventoryLink link = new InventoryLink();
    link.setRelationType(relation);
    return link;
  }

  private static ExtraLinkField extraField(InventoryRecord rec, InventoryLink link) {
    ExtraLinkField field = mock(ExtraLinkField.class);
    when(field.getInventoryRecord()).thenReturn(rec);
    when(field.getLink()).thenReturn(link);
    return field;
  }

  private static InventoryLinkField templateField(InventoryRecord rec, InventoryLink link) {
    InventoryLinkField field = mock(InventoryLinkField.class);
    when(field.getInventoryRecord()).thenReturn(rec);
    when(field.getLink()).thenReturn(link);
    return field;
  }

  @Test
  void readableItemsCarryIdentityAndRelationAndUnreadableOnesOnlyKindAndOwner() {
    // two unreadable items of one owner stay two rows
    InventoryRecord sample =
        readable("SA7", "Buffer A", InventoryRecordType.SAMPLE, "Ada Lovelace");
    InventoryRecord hiddenOne = hidden("IN3", InventoryRecordType.INSTRUMENT, "Grace Hopper");
    InventoryRecord hiddenTwo = hidden("IN4", InventoryRecordType.INSTRUMENT, "Grace Hopper");
    ExtraLinkField sampleRow = extraField(sample, link("IsSourceOf"));
    ExtraLinkField hiddenRow = extraField(hiddenOne, link("Collects"));
    InventoryLinkField hiddenTemplateRow = templateField(hiddenTwo, link("Collects"));
    when(linkDao.findLinkFieldsTargetingFilestorePaths(7L, PATHS, null, null))
        .thenReturn(List.of(sampleRow, hiddenRow));
    when(linkDao.findStructuredLinkFieldsTargetingFilestorePaths(7L, PATHS, null, null))
        .thenReturn(List.of(hiddenTemplateRow));

    List<FilestoreLinkingItem> rows =
        manager.findItemsLinkingFilestorePaths(fileSystem, PATHS, actor);

    assertEquals(3, rows.size());
    FilestoreLinkingItem placeholder = rows.get(0);
    assertFalse(placeholder.isReadable());
    assertNull(placeholder.name());
    assertNull(placeholder.relationType());
    assertEquals("INSTRUMENT", placeholder.type());
    assertEquals("Grace Hopper", placeholder.ownerFullName());
    assertFalse(rows.get(1).isReadable());
    assertEquals(
        new FilestoreLinkingItem("SA7", "Buffer A", "SAMPLE", "Ada Lovelace", "IsSourceOf"),
        rows.get(2));
  }

  @Test
  void anItemIsOneRowPerRelationHoweverManyFieldsLinkThePaths() {
    // the folder and a file in it, linked by the same item
    InventoryRecord sample =
        readable("SA7", "Buffer A", InventoryRecordType.SAMPLE, "Ada Lovelace");
    ExtraLinkField folderRow = extraField(sample, link("IsSourceOf"));
    ExtraLinkField fileRow = extraField(sample, link("IsSourceOf"));
    ExtraLinkField otherRelationRow = extraField(sample, link("References"));
    when(linkDao.findLinkFieldsTargetingFilestorePaths(7L, PATHS, null, null))
        .thenReturn(List.of(folderRow, fileRow, otherRelationRow));

    List<FilestoreLinkingItem> rows =
        manager.findItemsLinkingFilestorePaths(fileSystem, PATHS, actor);

    assertThat(rows)
        .extracting(FilestoreLinkingItem::relationType)
        .containsExactly("IsSourceOf", "References");
    verify(permissionUtils, times(1)).canUserReadInventoryRecord(sample, actor);
  }

  @Test
  void manyPathsAreLookedUpInBatchesThatAreMergedBeforeAnItemIsCountedOnce() {
    List<String> paths = IntStream.range(0, 1001).mapToObj(i -> "f" + i).toList();
    InventoryRecord sample =
        readable("SA7", "Buffer A", InventoryRecordType.SAMPLE, "Ada Lovelace");
    // a separate copy of the same item, so the merge cannot rely on object identity; read access
    // is asked once per Global ID, so never of this copy
    InventoryRecord sameSample = item("SA7", InventoryRecordType.SAMPLE, "Ada Lovelace");
    when(sameSample.getName()).thenReturn("Buffer A");
    ExtraLinkField firstBatchRow = extraField(sample, link("IsSourceOf"));
    ExtraLinkField secondBatchRow = extraField(sameSample, link("IsSourceOf"));
    when(linkDao.findLinkFieldsTargetingFilestorePaths(7L, paths.subList(0, 1000), null, null))
        .thenReturn(List.of(firstBatchRow));
    when(linkDao.findLinkFieldsTargetingFilestorePaths(7L, paths.subList(1000, 1001), null, null))
        .thenReturn(List.of(secondBatchRow));

    List<FilestoreLinkingItem> rows =
        manager.findItemsLinkingFilestorePaths(fileSystem, paths, actor);

    assertEquals(
        List.of(
            new FilestoreLinkingItem("SA7", "Buffer A", "SAMPLE", "Ada Lovelace", "IsSourceOf")),
        rows);
  }

  @Test
  void itemsInTheTrashAreLeftOutWithoutAskingWhoMayReadThem() {
    InventoryRecord trashed = mock(InventoryRecord.class);
    when(trashed.isDeleted()).thenReturn(true);
    ExtraLinkField trashedRow = extraField(trashed, link("IsSourceOf"));
    when(linkDao.findLinkFieldsTargetingFilestorePaths(7L, PATHS, null, null))
        .thenReturn(List.of(trashedRow));

    assertThat(manager.findItemsLinkingFilestorePaths(fileSystem, PATHS, actor)).isEmpty();
    verify(permissionUtils, never()).canUserReadInventoryRecord(any(InventoryRecord.class), any());
  }

  @Test
  void templatesAreLeftOutWithoutAskingWhoMayReadThem() {
    // an extra Link field put on a template through the API
    InventoryRecord template = mock(InventoryRecord.class);
    when(template.getType()).thenReturn(InventoryRecordType.INSTRUMENT_TEMPLATE);
    ExtraLinkField templateRow = extraField(template, link("IsSourceOf"));
    when(linkDao.findLinkFieldsTargetingFilestorePaths(7L, PATHS, null, null))
        .thenReturn(List.of(templateRow));

    assertThat(manager.findItemsLinkingFilestorePaths(fileSystem, PATHS, actor)).isEmpty();
    verify(permissionUtils, never()).canUserReadInventoryRecord(any(InventoryRecord.class), any());
  }

  @Test
  void pathsAreNormalisedBeforeTheLookupAndRowsComeInGlobalIdOrder() {
    InventoryRecord sampleTen = readable("SA10", "ten", InventoryRecordType.SAMPLE, "Ada Lovelace");
    InventoryRecord sampleTwo = readable("SA2", "two", InventoryRecordType.SAMPLE, "Ada Lovelace");
    InventoryRecord container =
        readable("IC3", "box", InventoryRecordType.CONTAINER, "Ada Lovelace");
    ExtraLinkField tenRow = extraField(sampleTen, link("References"));
    ExtraLinkField twoRow = extraField(sampleTwo, link("References"));
    ExtraLinkField containerRow = extraField(container, link("References"));
    when(linkDao.findLinkFieldsTargetingFilestorePaths(7L, List.of("data/XRD/run.dat"), null, null))
        .thenReturn(List.of(tenRow, twoRow, containerRow));

    List<FilestoreLinkingItem> rows =
        manager.findItemsLinkingFilestorePaths(fileSystem, List.of("//data/XRD/run.dat/"), actor);

    assertThat(rows)
        .extracting(FilestoreLinkingItem::globalId)
        .containsExactly("IC3", "SA2", "SA10");
  }

  @Test
  void onAPerUserLoginFilesystemOnlyTheActorsFilestoresWithAMatchingBaseAreSearched() {
    NfsFileSystem irods = GalleryFilestoreTestUtils.createIrodsFileSystem(8L);
    ExtraLinkField fromRoot =
        extraField(
            readable("SA1", "Run", InventoryRecordType.SAMPLE, "Viewer"), link("IsSourceOf"));
    ExtraLinkField fromHome =
        extraField(
            readable("SA2", "Rerun", InventoryRecordType.SAMPLE, "Viewer"), link("IsSourceOf"));
    List<String> rooted = List.of("data/XRD/run.dat");
    List<String> underHome = List.of("data/XRD/rerun.dat");
    when(linkDao.findLinkFieldsTargetingFilestorePaths(8L, rooted, actor, true))
        .thenReturn(List.of(fromRoot));
    when(linkDao.findStructuredLinkFieldsTargetingFilestorePaths(8L, rooted, actor, true))
        .thenReturn(List.of());
    // a path without a leading slash starts at the login's home
    when(linkDao.findLinkFieldsTargetingFilestorePaths(8L, underHome, actor, false))
        .thenReturn(List.of(fromHome));
    when(linkDao.findStructuredLinkFieldsTargetingFilestorePaths(8L, underHome, actor, false))
        .thenReturn(List.of());

    assertThat(
            manager.findItemsLinkingFilestorePaths(
                irods, List.of("/data/XRD/run.dat", "data/XRD/rerun.dat"), actor))
        .extracting(FilestoreLinkingItem::globalId)
        .containsExactly("SA1", "SA2");
  }

  @Test
  void aUserTheFilesystemRefusesNeverReachesTheLinks() {
    doThrow(new AuthorizationException("denied")).when(aclChecker).assertCanRead(actor, fileSystem);

    assertThrows(
        AuthorizationException.class,
        () -> manager.findItemsLinkingFilestorePaths(fileSystem, PATHS, actor));
    verifyNoInteractions(linkDao);
  }
}
